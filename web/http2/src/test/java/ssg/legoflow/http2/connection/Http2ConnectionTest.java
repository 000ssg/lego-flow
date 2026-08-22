package ssg.legoflow.http2.connection;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http2.frame.*;
import ssg.legoflow.http2.stream.Http2StreamState;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
class Http2ConnectionTest {

    @Test
    void testServerHandlesClientPreface() {
        var server = new Http2Connection(true);
        var preface = Http2ConnectionPreface.createClientPreface();

        var outFrames = server.handlePreface(preface);

        assertThat(server.isPrefaceReceived()).isTrue();
        assertThat(server.isPrefaceSent()).isTrue();
        assertThat(outFrames).isNotEmpty();
        assertThat(outFrames.getFirst().type()).isEqualTo(Http2FrameType.SETTINGS);
    }

    @Test
    void testServerRejectsInvalidPreface() {
        var server = new Http2Connection(true);
        var garbage = ByteBuffer.wrap("NOT A PREFACE AT ALL!!!!!".getBytes());

        assertThatThrownBy(() -> server.handlePreface(garbage))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testClientSendPreface() {
        var client = new Http2Connection(false);
        var outFrames = client.sendPreface();

        assertThat(client.isPrefaceSent()).isTrue();
        assertThat(outFrames).hasSize(1);
        assertThat(outFrames.getFirst().type()).isEqualTo(Http2FrameType.SETTINGS);
    }

    @Test
    void testProcessSettingsFrame() {
        var connection = new Http2Connection(true);
        var settings = new Http2Settings();
        settings.set(Http2Settings.MAX_CONCURRENT_STREAMS, 50);

        var frame = Http2Frame.settings(settings.encode());
        var outFrames = connection.processFrame(frame);

        assertThat(outFrames).hasSize(1);
        assertThat(outFrames.getFirst().type()).isEqualTo(Http2FrameType.SETTINGS);
        assertThat(outFrames.getFirst().hasFlag(Http2Flags.ACK)).isTrue();
        assertThat(connection.remoteSettings().maxConcurrentStreams()).isEqualTo(50);
    }

    @Test
    void testProcessSettingsAckIgnored() {
        var connection = new Http2Connection(true);
        var frame = Http2Frame.settingsAck();

        var outFrames = connection.processFrame(frame);
        assertThat(outFrames).isEmpty();
    }

    @Test
    void testProcessHeadersFrame() {
        var connection = new Http2Connection(true);

        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        headers.set(":path", "/");
        headers.set(":scheme", "https");
        var encoded = connection.encoder().encode(headers);

        var frame = Http2Frame.headers(1, encoded, true, true);
        connection.processFrame(frame);

        var stream = connection.streamManager().getStream(1);
        assertThat(stream).isNotNull();
        assertThat(stream.state()).isEqualTo(Http2StreamState.HALF_CLOSED_REMOTE);
        assertThat(stream.headers().get(":method")).isEqualTo("GET");
    }

    @Test
    void testProcessDataFrame() {
        var connection = new Http2Connection(true);

        var headers = new HttpHeaders();
        headers.set(":method", "POST");
        headers.set(":path", "/data");
        var headersEncoded = connection.encoder().encode(headers);
        connection.processFrame(Http2Frame.headers(1, headersEncoded, false, true));

        var data = ByteBuffer.wrap("request body".getBytes());
        var dataFrame = Http2Frame.data(1, data, true);
        var outFrames = connection.processFrame(dataFrame);

        var stream = connection.streamManager().getStream(1);
        assertThat(stream.state()).isEqualTo(Http2StreamState.HALF_CLOSED_REMOTE);

        var accumulated = stream.getAccumulatedData();
        var bytes = new byte[accumulated.remaining()];
        accumulated.get(bytes);
        assertThat(new String(bytes)).isEqualTo("request body");

        assertThat(outFrames.stream().anyMatch(f -> f.type() == Http2FrameType.WINDOW_UPDATE)).isTrue();
    }

    @Test
    void testProcessPingFrame() {
        var connection = new Http2Connection(true);
        var opaqueData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        var frame = Http2Frame.ping(opaqueData);

        var outFrames = connection.processFrame(frame);

        assertThat(outFrames).hasSize(1);
        assertThat(outFrames.getFirst().type()).isEqualTo(Http2FrameType.PING);
        assertThat(outFrames.getFirst().hasFlag(Http2Flags.ACK)).isTrue();
    }

    @Test
    void testProcessPingAckIgnored() {
        var connection = new Http2Connection(true);
        var opaqueData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        var frame = Http2Frame.pingAck(opaqueData);

        var outFrames = connection.processFrame(frame);
        assertThat(outFrames).isEmpty();
    }

    @Test
    void testProcessGoaway() {
        var connection = new Http2Connection(true);
        var frame = Http2Frame.goaway(5, Http2ErrorCode.NO_ERROR, null);

        connection.processFrame(frame);

        assertThat(connection.isGoawayReceived()).isTrue();
    }

    @Test
    void testProcessRstStream() {
        var connection = new Http2Connection(true);

        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        var encoded = connection.encoder().encode(headers);
        connection.processFrame(Http2Frame.headers(1, encoded, false, true));

        connection.processFrame(Http2Frame.rstStream(1, Http2ErrorCode.CANCEL));

        var stream = connection.streamManager().getStream(1);
        assertThat(stream.isClosed()).isTrue();
    }

    @Test
    void testCreateGoaway() {
        var connection = new Http2Connection(true);
        var frame = connection.createGoaway(Http2ErrorCode.NO_ERROR);

        assertThat(frame.type()).isEqualTo(Http2FrameType.GOAWAY);
        assertThat(connection.isGoawaySent()).isTrue();
    }

    @Test
    void testCreateHeadersFrame() {
        var connection = new Http2Connection(true);
        var headers = new HttpHeaders();
        headers.set(":status", "200");
        headers.set("content-type", "text/html");

        var frame = connection.createHeadersFrame(1, headers, true);

        assertThat(frame.type()).isEqualTo(Http2FrameType.HEADERS);
        assertThat(frame.streamId()).isEqualTo(1);
        assertThat(frame.hasFlag(Http2Flags.END_STREAM)).isTrue();
        assertThat(frame.hasFlag(Http2Flags.END_HEADERS)).isTrue();
    }

    @Test
    void testCreateDataFrame() {
        var connection = new Http2Connection(true);
        var data = ByteBuffer.wrap("response".getBytes());

        var frame = connection.createDataFrame(1, data, true);

        assertThat(frame.type()).isEqualTo(Http2FrameType.DATA);
        assertThat(frame.streamId()).isEqualTo(1);
        assertThat(frame.hasFlag(Http2Flags.END_STREAM)).isTrue();
    }

    @Test
    void testFrameListener() {
        var connection = new Http2Connection(true);
        var receivedFrames = new ArrayList<Http2Frame>();
        connection.addFrameListener(receivedFrames::add);

        var settings = new Http2Settings();
        var frame = Http2Frame.settings(settings.encode());
        connection.processFrame(frame);

        assertThat(receivedFrames).hasSize(1);
    }

    @Test
    void testProcessWindowUpdate() {
        var connection = new Http2Connection(true);
        var frame = Http2Frame.windowUpdate(0, 32768);

        connection.processFrame(frame);

        assertThat(connection.flowControl().connectionSendWindow())
                .isEqualTo(65535 + 32768);
    }

    @Test
    void testProcessPushPromise() {
        var connection = new Http2Connection(false);

        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        headers.set(":path", "/style.css");
        var encoded = connection.encoder().encode(headers);

        var frame = Http2Frame.pushPromise(1, 2, encoded);
        connection.processFrame(frame);

        var promisedStream = connection.streamManager().getStream(2);
        assertThat(promisedStream).isNotNull();
        assertThat(promisedStream.state()).isEqualTo(Http2StreamState.RESERVED_REMOTE);
    }
}
