package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.frame.Http2Frame;
import ssg.legoflow.http2.frame.Http2FrameType;
import ssg.legoflow.http2.stream.Http2StreamState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ServerPushDemoTest {

    @Test
    void testServerPushResponse() {
        var demo = new ServerPushDemo();
        var serverConn = demo.server().acceptConnection();

        var stream = serverConn.streamManager().getOrCreateStream(1);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/page");
        stream.headers().set(":scheme", "https");
        stream.headers().set(":authority", "localhost");
        stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);

        var outFrames = demo.handleRequestWithPush(serverConn, stream);

        assertThat(outFrames).isNotEmpty();

        var pushPromise = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.PUSH_PROMISE)
                .findFirst();
        assertThat(pushPromise).isPresent();
        assertThat(pushPromise.get().streamId()).isEqualTo(1);

        var headersFrames = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.HEADERS)
                .toList();
        assertThat(headersFrames.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testPushPromiseContainsPushedStreamId() {
        var demo = new ServerPushDemo();
        var serverConn = demo.server().acceptConnection();

        var stream = serverConn.streamManager().getOrCreateStream(1);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/page");
        stream.headers().set(":scheme", "https");
        stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);

        var outFrames = demo.handleRequestWithPush(serverConn, stream);

        var pushPromise = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.PUSH_PROMISE)
                .findFirst()
                .orElseThrow();

        var payload = pushPromise.payload();
        int promisedStreamId = payload.getInt() & 0x7FFFFFFF;
        assertThat(promisedStreamId).isEven();
        assertThat(promisedStreamId).isGreaterThan(0);
    }

    @Test
    void testPushedResponseHasData() {
        var demo = new ServerPushDemo();
        var serverConn = demo.server().acceptConnection();

        var stream = serverConn.streamManager().getOrCreateStream(1);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/page");
        stream.headers().set(":scheme", "https");
        stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);

        var outFrames = demo.handleRequestWithPush(serverConn, stream);

        var dataFrames = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.DATA)
                .toList();
        assertThat(dataFrames.size()).isGreaterThanOrEqualTo(2);
    }
}
