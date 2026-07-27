package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RtspRequest}.
 */
class RtspRequestTest {

    @Test
    void testCreateRequest() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "1");
        var request = new RtspRequest(RtspMethod.OPTIONS,
                URI.create("rtsp://server/media"), headers);

        assertThat(request.method()).isEqualTo(RtspMethod.OPTIONS);
        assertThat(request.uri()).isEqualTo(URI.create("rtsp://server/media"));
        assertThat(request.hasBody()).isFalse();
    }

    @Test
    void testRequestWithBody() {
        var headers = new RtspHeaders();
        headers.set("CSeq", "2");
        byte[] body = "test body".getBytes();
        var request = new RtspRequest(RtspMethod.ANNOUNCE,
                URI.create("rtsp://server/media"), headers, body);

        assertThat(request.hasBody()).isTrue();
        assertThat(request.bodyAsString()).isEqualTo("test body");
        assertThat(request.body()).isEqualTo(body);
    }

    @Test
    void testRequestLine() {
        var headers = new RtspHeaders();
        var request = new RtspRequest(RtspMethod.DESCRIBE,
                URI.create("rtsp://server/media"), headers);
        assertThat(request.requestLine()).isEqualTo("DESCRIBE rtsp://server/media RTSP/2.0");
    }

    @Test
    void testBuilderOptions() {
        var request = RtspRequest.builder(RtspMethod.OPTIONS, "rtsp://server/*")
                .cseq(1)
                .userAgent("TestClient")
                .build();

        assertThat(request.method()).isEqualTo(RtspMethod.OPTIONS);
        assertThat(request.headers().cseq()).isEqualTo(1);
        assertThat(request.headers().first("User-Agent")).hasValue("TestClient");
    }

    @Test
    void testBuilderDescribe() {
        var request = RtspRequest.builder(RtspMethod.DESCRIBE, "rtsp://server/media")
                .cseq(2)
                .accept("application/sdp")
                .build();

        assertThat(request.method()).isEqualTo(RtspMethod.DESCRIBE);
        assertThat(request.headers().first("Accept")).hasValue("application/sdp");
    }

    @Test
    void testBuilderSetup() {
        var request = RtspRequest.builder(RtspMethod.SETUP, "rtsp://server/media/track1")
                .cseq(3)
                .transport("RTP/AVP;unicast;client_port=8000-8001")
                .build();

        assertThat(request.method()).isEqualTo(RtspMethod.SETUP);
        assertThat(request.headers().first("Transport"))
                .hasValue("RTP/AVP;unicast;client_port=8000-8001");
    }

    @Test
    void testBuilderPlay() {
        var request = RtspRequest.builder(RtspMethod.PLAY, "rtsp://server/media")
                .cseq(4)
                .session("abc123")
                .range("npt=0-")
                .build();

        assertThat(request.method()).isEqualTo(RtspMethod.PLAY);
        assertThat(request.headers().sessionId()).hasValue("abc123");
        assertThat(request.headers().first("Range")).hasValue("npt=0-");
    }

    @Test
    void testBuilderWithBody() {
        var request = RtspRequest.builder(RtspMethod.ANNOUNCE, "rtsp://server/media")
                .cseq(5)
                .body("sdp content")
                .build();

        assertThat(request.hasBody()).isTrue();
        assertThat(request.bodyAsString()).isEqualTo("sdp content");
        assertThat(request.headers().contentLength()).isEqualTo(11);
    }

    @Test
    void testBodyIsCopied() {
        byte[] body = "test".getBytes();
        var request = new RtspRequest(RtspMethod.ANNOUNCE,
                URI.create("rtsp://server/media"), new RtspHeaders(), body);
        body[0] = 'X';
        assertThat(request.body()[0]).isEqualTo((byte) 't');
    }

    @Test
    void testToString() {
        var request = RtspRequest.builder(RtspMethod.PLAY, "rtsp://server/media")
                .cseq(1).build();
        assertThat(request.toString()).contains("PLAY").contains("rtsp://server/media");
    }
}
