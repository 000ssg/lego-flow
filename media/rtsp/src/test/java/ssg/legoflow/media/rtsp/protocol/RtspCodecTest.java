package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RtspCodec}.
 */
class RtspCodecTest {

    @Test
    void testEncodeRequest() {
        var request = RtspRequest.builder(RtspMethod.OPTIONS, "rtsp://server/media")
                .cseq(1)
                .userAgent("Test/1.0")
                .build();

        byte[] encoded = RtspCodec.encodeRequest(request);
        String text = new String(encoded);
        assertThat(text).startsWith("OPTIONS rtsp://server/media RTSP/2.0\r\n");
        assertThat(text).contains("Cseq: 1\r\n");
        assertThat(text).contains("User-Agent: Test/1.0\r\n");
        assertThat(text).endsWith("\r\n\r\n");
    }

    @Test
    void testEncodeRequestWithBody() {
        var request = RtspRequest.builder(RtspMethod.ANNOUNCE, "rtsp://server/media")
                .cseq(2)
                .body("v=0\r\n")
                .build();

        byte[] encoded = RtspCodec.encodeRequest(request);
        String text = new String(encoded);
        assertThat(text).contains("Content-Length: 5\r\n");
        assertThat(text).endsWith("v=0\r\n");
    }

    @Test
    void testEncodeResponse() {
        var response = RtspResponse.builder(RtspStatus.OK)
                .cseq(1)
                .server("TestServer/1.0")
                .build();

        byte[] encoded = RtspCodec.encodeResponse(response);
        String text = new String(encoded);
        assertThat(text).startsWith("RTSP/2.0 200 OK\r\n");
        assertThat(text).contains("Cseq: 1\r\n");
        assertThat(text).endsWith("\r\n\r\n");
    }

    @Test
    void testEncodeResponseWithBody() {
        var response = RtspResponse.builder(RtspStatus.OK)
                .cseq(2)
                .body("v=0\r\n", "application/sdp")
                .build();

        byte[] encoded = RtspCodec.encodeResponse(response);
        String text = new String(encoded);
        assertThat(text).contains("Content-Type: application/sdp\r\n");
        assertThat(text).contains("Content-Length: 5\r\n");
    }

    @Test
    void testDecodeRequest() throws IOException {
        String raw = "DESCRIBE rtsp://server/media RTSP/2.0\r\n"
                + "CSeq: 2\r\n"
                + "Accept: application/sdp\r\n"
                + "\r\n";
        var request = RtspCodec.decodeRequest(raw.getBytes());
        assertThat(request.method()).isEqualTo(RtspMethod.DESCRIBE);
        assertThat(request.uri()).isEqualTo(URI.create("rtsp://server/media"));
        assertThat(request.headers().cseq()).isEqualTo(2);
        assertThat(request.headers().first("Accept")).hasValue("application/sdp");
    }

    @Test
    void testDecodeRequestWithBody() throws IOException {
        String body = "v=0\r\n";
        String raw = "ANNOUNCE rtsp://server/media RTSP/2.0\r\n"
                + "CSeq: 5\r\n"
                + "Content-Type: application/sdp\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n"
                + body;
        var request = RtspCodec.decodeRequest(raw.getBytes());
        assertThat(request.method()).isEqualTo(RtspMethod.ANNOUNCE);
        assertThat(request.hasBody()).isTrue();
        assertThat(request.bodyAsString()).isEqualTo(body);
    }

    @Test
    void testDecodeResponse() throws IOException {
        String raw = "RTSP/2.0 200 OK\r\n"
                + "CSeq: 1\r\n"
                + "Public: OPTIONS, DESCRIBE, SETUP\r\n"
                + "\r\n";
        var response = RtspCodec.decodeResponse(raw.getBytes());
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.headers().cseq()).isEqualTo(1);
        assertThat(response.headers().first("Public")).isPresent();
    }

    @Test
    void testDecodeResponseWithBody() throws IOException {
        String body = "v=0\r\no=- 12345 1 IN IP4 127.0.0.1\r\n";
        String raw = "RTSP/2.0 200 OK\r\n"
                + "CSeq: 2\r\n"
                + "Content-Type: application/sdp\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n"
                + body;
        var response = RtspCodec.decodeResponse(raw.getBytes());
        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.hasBody()).isTrue();
        assertThat(response.bodyAsString()).isEqualTo(body);
    }

    @Test
    void testDecodeRequestEmptyLineThrows() {
        assertThatThrownBy(() -> RtspCodec.decodeRequest("\r\n".getBytes()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testRoundTripRequest() throws IOException {
        var original = RtspRequest.builder(RtspMethod.SETUP, "rtsp://server/media/track1")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();

        byte[] encoded = RtspCodec.encodeRequest(original);
        var decoded = RtspCodec.decodeRequest(encoded);

        assertThat(decoded.method()).isEqualTo(original.method());
        assertThat(decoded.uri()).isEqualTo(original.uri());
        assertThat(decoded.headers().cseq()).isEqualTo(3);
    }

    @Test
    void testRoundTripResponse() throws IOException {
        var original = RtspResponse.builder(RtspStatus.OK)
                .cseq(3)
                .session("abc123", 60)
                .transport("RTP/AVP;unicast;server_port=6000-6001")
                .build();

        byte[] encoded = RtspCodec.encodeResponse(original);
        var decoded = RtspCodec.decodeResponse(encoded);

        assertThat(decoded.status()).isEqualTo(original.status());
        assertThat(decoded.headers().cseq()).isEqualTo(3);
        assertThat(decoded.headers().sessionId()).hasValue("abc123");
    }

    @Test
    void testIsInterleavedFrame() {
        assertThat(RtspCodec.isInterleavedFrame((byte) '$')).isTrue();
        assertThat(RtspCodec.isInterleavedFrame((byte) 'R')).isFalse();
        assertThat(RtspCodec.isInterleavedFrame((byte) 0)).isFalse();
    }

    @Test
    void testDecodeResponseSessionNotFound() throws IOException {
        String raw = "RTSP/2.0 454 Session Not Found\r\n"
                + "CSeq: 10\r\n"
                + "\r\n";
        var response = RtspCodec.decodeResponse(raw.getBytes());
        assertThat(response.status()).isEqualTo(RtspStatus.SESSION_NOT_FOUND);
    }
}
