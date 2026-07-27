package ssg.legoflow.http2.demo;

import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.frame.Http2Frame;
import ssg.legoflow.http2.frame.Http2FrameType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MultiplexingDemoTest {

    @Test
    void testConcurrentRequests() {
        var demo = new MultiplexingDemo();
        demo.client().connect();

        var frames = demo.sendConcurrentRequests("/resource/1", "/resource/2", "/resource/3");

        assertThat(frames).isNotEmpty();

        var headersFrames = frames.stream()
                .filter(f -> f.type() == Http2FrameType.HEADERS)
                .toList();
        assertThat(headersFrames).hasSize(3);

        var streamIds = headersFrames.stream()
                .map(Http2Frame::streamId)
                .distinct()
                .toList();
        assertThat(streamIds).hasSize(3);
    }

    @Test
    void testStreamIdsAreOdd() {
        var demo = new MultiplexingDemo();
        demo.client().connect();

        var frames = demo.sendConcurrentRequests("/resource/1", "/resource/2");

        var streamIds = frames.stream()
                .map(Http2Frame::streamId)
                .distinct()
                .toList();
        for (int id : streamIds) {
            assertThat(id % 2).isEqualTo(1);
        }
    }

    @Test
    void testServerProcessesMultiplexedRequests() {
        var demo = new MultiplexingDemo();
        demo.client().connect();

        var requestFrames = demo.sendConcurrentRequests("/resource/1", "/resource/2");

        var serverConn = demo.server().acceptConnection();
        for (var frame : requestFrames) {
            serverConn.processFrame(frame);
        }

        var stream1 = serverConn.streamManager().getStream(1);
        var stream3 = serverConn.streamManager().getStream(3);
        assertThat(stream1).isNotNull();
        assertThat(stream3).isNotNull();
    }

    @Test
    void testServerDispatchesMultiplexedRequests() {
        var demo = new MultiplexingDemo();
        demo.client().connect();

        var requestFrames = demo.sendConcurrentRequests("/resource/1", "/resource/2");

        var serverConn = demo.server().acceptConnection();
        for (var frame : requestFrames) {
            serverConn.processFrame(frame);
        }

        var responseFrames1 = demo.server().handleRequest(serverConn, serverConn.streamManager().getStream(1));
        var responseFrames3 = demo.server().handleRequest(serverConn, serverConn.streamManager().getStream(3));

        assertThat(responseFrames1).isNotEmpty();
        assertThat(responseFrames3).isNotEmpty();
    }
}
