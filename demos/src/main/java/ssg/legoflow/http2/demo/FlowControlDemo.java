package ssg.legoflow.http2.demo;

import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2Settings;
import ssg.legoflow.http2.frame.Http2Frame;
import ssg.legoflow.http2.stream.Http2StreamState;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
public class FlowControlDemo {

    private final Http2Connection connection;
    private final int maxFrameSize;

    public FlowControlDemo() {
        this(Http2Config.defaults());
    }

    public FlowControlDemo(Http2Config config) {
        var settings = new Http2Settings();
        settings.set(Http2Settings.INITIAL_WINDOW_SIZE, config.initialWindowSize());
        settings.set(Http2Settings.MAX_FRAME_SIZE, config.maxFrameSize());
        this.connection = new Http2Connection(true, settings);
        this.maxFrameSize = config.maxFrameSize();
    }

    public Http2Connection connection() {
        return connection;
    }

    public List<Http2Frame> sendLargePayload(int streamId, byte[] payload) {
        var stream = connection.streamManager().getOrCreateStream(streamId);
        if (stream.state() == Http2StreamState.IDLE) {
            stream.transitionTo(Http2StreamState.OPEN);
        }

        var outFrames = new ArrayList<Http2Frame>();
        var flowControl = connection.flowControl();
        int offset = 0;

        while (offset < payload.length) {
            int maxSend = flowControl.calculateMaxSendSize(stream, maxFrameSize);
            if (maxSend <= 0) break;

            int chunkSize = Math.min(maxSend, payload.length - offset);
            var chunk = ByteBuffer.wrap(payload, offset, chunkSize);

            boolean endStream = (offset + chunkSize >= payload.length);
            outFrames.add(Http2Frame.data(streamId, chunk, endStream));

            flowControl.consumeConnectionSendWindow(chunkSize);
            stream.consumeSendWindow(chunkSize);
            offset += chunkSize;
        }

        return outFrames;
    }

    public void applyWindowUpdate(int streamId, int increment) {
        var flowControl = connection.flowControl();
        if (streamId == 0) {
            flowControl.applyConnectionWindowUpdate(increment);
        } else {
            var stream = connection.streamManager().getStream(streamId);
            if (stream != null) {
                flowControl.applyStreamWindowUpdate(stream, increment);
            }
        }
    }

    public int getConnectionSendWindow() {
        return connection.flowControl().connectionSendWindow();
    }

    public int getStreamSendWindow(int streamId) {
        var stream = connection.streamManager().getStream(streamId);
        return stream != null ? stream.sendWindowSize() : 0;
    }
}
