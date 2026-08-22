package ssg.legoflow.rpc.grpc.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.grpc.client.GrpcChannel;
import ssg.legoflow.rpc.grpc.client.GrpcStub;
import ssg.legoflow.rpc.grpc.protobuf.ProtoMessage;
import ssg.legoflow.rpc.grpc.server.GrpcServer;
import java.util.List;
import java.util.zip.CRC32;
import static org.assertj.core.api.Assertions.*;
class UploadServiceTest {

    private GrpcStub stub;

    @BeforeEach
    void setUp() {
        var server = new GrpcServer();
        UploadService.register(server);
        var channel = new GrpcChannel(server);
        stub = new GrpcStub(channel, UploadService.serviceDescriptor());
    }

    @Test
    void testUploadSingleChunk() {
        byte[] data = "hello world".getBytes();
        var chunk = new ProtoMessage()
                .setBytes(1, data)
                .setVarint(2, 0);

        var response = stub.clientStreamingCall("Upload", List.of(chunk));
        assertThat(response.getVarint(1)).isEqualTo(data.length); // total_bytes
        assertThat(response.getInt32(3)).isEqualTo(1); // chunk_count
    }

    @Test
    void testUploadMultipleChunks() {
        var chunks = List.of(
                new ProtoMessage().setBytes(1, new byte[]{1, 2, 3}).setVarint(2, 0),
                new ProtoMessage().setBytes(1, new byte[]{4, 5, 6}).setVarint(2, 1),
                new ProtoMessage().setBytes(1, new byte[]{7, 8}).setVarint(2, 2)
        );

        var response = stub.clientStreamingCall("Upload", chunks);
        assertThat(response.getVarint(1)).isEqualTo(8); // total bytes
        assertThat(response.getInt32(3)).isEqualTo(3); // chunk count
    }

    @Test
    void testUploadChecksum() {
        byte[] data1 = {0x01, 0x02, 0x03};
        byte[] data2 = {0x04, 0x05};

        var crc = new CRC32();
        crc.update(data1);
        crc.update(data2);
        long expectedChecksum = crc.getValue();

        var chunks = List.of(
                new ProtoMessage().setBytes(1, data1).setVarint(2, 0),
                new ProtoMessage().setBytes(1, data2).setVarint(2, 1)
        );

        var response = stub.clientStreamingCall("Upload", chunks);
        assertThat(response.getVarint(2)).isEqualTo(expectedChecksum);
    }

    @Test
    void testUploadLargeData() {
        byte[] largeChunk = new byte[10000];
        for (int i = 0; i < largeChunk.length; i++) {
            largeChunk[i] = (byte) (i % 256);
        }

        var chunk = new ProtoMessage()
                .setBytes(1, largeChunk)
                .setVarint(2, 0);

        var response = stub.clientStreamingCall("Upload", List.of(chunk));
        assertThat(response.getVarint(1)).isEqualTo(10000);
    }

    @Test
    void testServiceDescriptor() {
        var desc = UploadService.serviceDescriptor();
        assertThat(desc.fullName()).isEqualTo("demo.Upload");
        assertThat(desc.methods()).hasSize(1);
        assertThat(desc.method("Upload")).isNotNull();
    }
}
