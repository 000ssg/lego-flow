package ssg.legoflow.rpc.grpc.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.rpc.grpc.common.MethodDescriptor;
import ssg.legoflow.rpc.grpc.common.StatusException;
import ssg.legoflow.rpc.grpc.protobuf.*;
import ssg.legoflow.rpc.grpc.transport.GrpcEncoding;
import ssg.legoflow.rpc.grpc.transport.GrpcFrameCodec;
import ssg.legoflow.rpc.grpc.transport.GrpcStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ClientCallTest {

    private static final MessageDescriptor REQ_DESC = MessageDescriptor.builder("Req")
            .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.INT32))
            .build();

    private static final MessageDescriptor RESP_DESC = MessageDescriptor.builder("Resp")
            .addField(FieldDescriptor.scalar(1, "result", FieldDescriptor.Type.INT32))
            .build();

    private final MethodDescriptor method = MethodDescriptor.unary("test.Svc", "M", REQ_DESC, RESP_DESC);

    @Test
    void testEncodeRequest() {
        var call = new ClientCall(method, CallOptions.defaults());
        var request = new ProtoMessage().setVarint(1, 42);
        byte[] framed = call.encodeRequest(request);

        assertThat(framed.length).isGreaterThan(5);
        assertThat(framed[0]).isEqualTo((byte) 0); // not compressed
    }

    @Test
    void testEncodeMultipleRequests() {
        var call = new ClientCall(method, CallOptions.defaults());
        var requests = List.of(
                new ProtoMessage().setVarint(1, 1),
                new ProtoMessage().setVarint(1, 2)
        );
        byte[] combined = call.encodeRequests(requests);
        assertThat(combined.length).isGreaterThan(10);
    }

    @Test
    void testBuildRequestHeaders() {
        var options = CallOptions.defaults().authority("localhost:50051");
        var call = new ClientCall(method, options);
        var headers = call.buildRequestHeaders();

        assertThat(headers.get(":method")).isEqualTo("POST");
        assertThat(headers.get(":path")).isEqualTo("/test.Svc/M");
        assertThat(headers.get("content-type")).isEqualTo("application/grpc");
    }

    @Test
    void testProcessResponse() {
        var call = new ClientCall(method, CallOptions.defaults());
        var responseMsg = new ProtoMessage().setVarint(1, 99);
        byte[] encoded = ProtobufCodec.encode(responseMsg, RESP_DESC);
        byte[] framed = GrpcFrameCodec.encode(encoded);

        call.processResponse(framed, GrpcEncoding.IDENTITY);

        var trailers = new HttpHeaders();
        trailers.set("grpc-status", "0");
        call.processTrailers(trailers);

        var response = call.getResponse();
        assertThat(response.getInt32(1)).isEqualTo(99);
    }

    @Test
    void testProcessMultipleResponses() {
        var method = MethodDescriptor.serverStreaming("test.Svc", "Stream", REQ_DESC, RESP_DESC);
        var call = new ClientCall(method, CallOptions.defaults());

        byte[] frame1 = GrpcFrameCodec.encode(
                ProtobufCodec.encode(new ProtoMessage().setVarint(1, 1), RESP_DESC));
        byte[] frame2 = GrpcFrameCodec.encode(
                ProtobufCodec.encode(new ProtoMessage().setVarint(1, 2), RESP_DESC));
        byte[] combined = new byte[frame1.length + frame2.length];
        System.arraycopy(frame1, 0, combined, 0, frame1.length);
        System.arraycopy(frame2, 0, combined, frame1.length, frame2.length);

        call.processResponse(combined, GrpcEncoding.IDENTITY);

        var trailers = new HttpHeaders();
        trailers.set("grpc-status", "0");
        call.processTrailers(trailers);

        assertThat(call.getResponses()).hasSize(2);
    }

    @Test
    void testProcessTrailersError() {
        var call = new ClientCall(method, CallOptions.defaults());

        var trailers = new HttpHeaders();
        trailers.set("grpc-status", "5");
        trailers.set("grpc-message", "not%20found");
        call.processTrailers(trailers);

        assertThat(call.status()).isEqualTo(GrpcStatus.NOT_FOUND);
        assertThat(call.statusMessage()).isEqualTo("not found");
    }

    @Test
    void testGetResponseOnError() {
        var call = new ClientCall(method, CallOptions.defaults());

        var trailers = new HttpHeaders();
        trailers.set("grpc-status", "13");
        call.processTrailers(trailers);

        assertThatThrownBy(call::getResponse)
                .isInstanceOf(StatusException.class);
    }

    @Test
    void testGetResponseNoMessages() {
        var call = new ClientCall(method, CallOptions.defaults());

        var trailers = new HttpHeaders();
        trailers.set("grpc-status", "0");
        call.processTrailers(trailers);

        assertThatThrownBy(call::getResponse)
                .isInstanceOf(StatusException.class)
                .hasMessageContaining("No response message");
    }

    @Test
    void testCancel() {
        var call = new ClientCall(method, CallOptions.defaults());
        call.cancel();

        assertThat(call.isCancelled()).isTrue();
        assertThat(call.status()).isEqualTo(GrpcStatus.CANCELLED);
    }

    @Test
    void testCompressedRequest() {
        var options = CallOptions.defaults().encoding(GrpcEncoding.GZIP);
        var call = new ClientCall(method, options);
        var request = new ProtoMessage().setVarint(1, 42);
        byte[] framed = call.encodeRequest(request);

        assertThat(framed[0]).isEqualTo((byte) 1); // compressed
    }

    @Test
    void testResponseMetadata() {
        var call = new ClientCall(method, CallOptions.defaults());
        call.setResponseMetadata(new ssg.legoflow.rpc.grpc.metadata.Metadata().put("key", "val"));
        assertThat(call.responseMetadata().get("key")).isEqualTo("val");
    }
}
