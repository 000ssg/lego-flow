package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RtspResponse}.
 */
class RtspResponseTest {

    @Test
    void testCreateResponse() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "1");
        var response = new RtspResponse(RtspStatus.OK, headers);

        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.hasBody()).isFalse();
    }

    @Test
    void testResponseWithBody() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "2");
        byte[] body = "v=0\r\n".getBytes();
        var response = new RtspResponse(RtspStatus.OK, headers, body);

        assertThat(response.hasBody()).isTrue();
        assertThat(response.bodyAsString()).isEqualTo("v=0\r\n");
    }

    @Test
    void testStatusLine() {
        var response = new RtspResponse(RtspStatus.OK, new RtspHeaders());
        assertThat(response.statusLine()).isEqualTo("RTSP/2.0 200 OK");
    }

    @Test
    void testStatusLineNotFound() {
        var response = new RtspResponse(RtspStatus.NOT_FOUND, new RtspHeaders());
        assertThat(response.statusLine()).isEqualTo("RTSP/2.0 404 Not Found");
    }

    @Test
    void testBuilderOk() {
        var response = RtspResponse.builder(RtspStatus.OK)
                .cseq(1)
                .server("TestServer/1.0")
                .build();

        assertThat(response.status()).isEqualTo(RtspStatus.OK);
        assertThat(response.headers().cseq()).isEqualTo(1);
        assertThat(response.headers().first("Server")).hasValue("TestServer/1.0");
    }

    @Test
    void testBuilderWithSession() {
        var response = RtspResponse.builder(RtspStatus.OK)
                .cseq(3)
                .session("abc123", 60)
                .build();

        assertThat(response.headers().sessionId()).hasValue("abc123");
        assertThat(response.headers().sessionTimeout()).hasValue(60);
    }

    @Test
    void testBuilderWithTransport() {
        var response = RtspResponse.builder(RtspStatus.OK)
                .cseq(3)
                .transport("RTP/AVP;unicast;server_port=6000-6001")
                .build();

        assertThat(response.headers().first("Transport"))
                .hasValue("RTP/AVP;unicast;server_port=6000-6001");
    }

    @Test
    void testBuilderWithBody() {
        var response = RtspResponse.builder(RtspStatus.OK)
                .cseq(2)
                .body("v=0\r\n", "application/sdp")
                .build();

        assertThat(response.hasBody()).isTrue();
        assertThat(response.bodyAsString()).isEqualTo("v=0\r\n");
        assertThat(response.headers().first("Content-Type")).hasValue("application/sdp");
        assertThat(response.headers().contentLength()).isEqualTo(5);
    }

    @Test
    void testBuilderPublicMethods() {
        var response = RtspResponse.builder(RtspStatus.OK)
                .cseq(1)
                .publicMethods("OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN")
                .build();

        assertThat(response.headers().first("Public"))
                .hasValue("OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN");
    }

    @Test
    void testBodyIsCopied() {
        byte[] body = "test".getBytes();
        var response = new RtspResponse(RtspStatus.OK, new RtspHeaders(), body);
        body[0] = 'X';
        assertThat(response.body()[0]).isEqualTo((byte) 't');
    }

    @Test
    void testToString() {
        var response = RtspResponse.builder(RtspStatus.NOT_FOUND).cseq(1).build();
        assertThat(response.toString()).contains("404").contains("Not Found");
    }
}
