package ssg.legoflow.http2.connection;

import ssg.legoflow.http2.frame.*;
import ssg.legoflow.http2.hpack.HpackDecoder;
import ssg.legoflow.http2.hpack.HpackEncoder;
import ssg.legoflow.http2.stream.Http2FlowControl;
import ssg.legoflow.http2.stream.Http2Stream;
import ssg.legoflow.http2.stream.Http2StreamManager;
import ssg.legoflow.http2.stream.Http2StreamState;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Http2Connection {

    private final boolean isServer;
    private final Http2Settings localSettings;
    private final Http2Settings remoteSettings;
    private final Http2StreamManager streamManager;
    private final Http2FlowControl flowControl;
    private final HpackEncoder encoder;
    private final HpackDecoder decoder;
    private final List<Consumer<Http2Frame>> frameListeners = new CopyOnWriteArrayList<>();
    private boolean prefaceReceived;
    private boolean prefaceSent;
    private int lastStreamId;
    private boolean goawaySent;
    private boolean goawayReceived;

    public Http2Connection(boolean isServer) {
        this(isServer, new Http2Settings());
    }

    public Http2Connection(boolean isServer, Http2Settings settings) {
        this.isServer = isServer;
        this.localSettings = settings;
        this.remoteSettings = new Http2Settings();
        this.streamManager = new Http2StreamManager(isServer,
                settings.maxConcurrentStreams(), settings.initialWindowSize());
        this.flowControl = new Http2FlowControl(settings.initialWindowSize());
        this.encoder = new HpackEncoder(settings.headerTableSize());
        this.decoder = new HpackDecoder(settings.headerTableSize());
    }

    public void addFrameListener(Consumer<Http2Frame> listener) {
        frameListeners.add(listener);
    }

    public List<Http2Frame> handlePreface(ByteBuffer data) {
        var outFrames = new ArrayList<Http2Frame>();
        if (isServer) {
            if (!Http2ConnectionPreface.isClientPreface(data)) {
                throw new IllegalStateException("Invalid client connection preface");
            }
            prefaceReceived = true;
            outFrames.add(Http2Frame.settings(localSettings.encode()));
            prefaceSent = true;
        }
        return outFrames;
    }

    public List<Http2Frame> sendPreface() {
        var outFrames = new ArrayList<Http2Frame>();
        if (!isServer) {
            outFrames.add(Http2Frame.settings(localSettings.encode()));
            prefaceSent = true;
        }
        return outFrames;
    }

    public List<Http2Frame> processFrame(Http2Frame frame) {
        var outFrames = new ArrayList<Http2Frame>();

        switch (frame.type()) {
            case SETTINGS -> processSettings(frame, outFrames);
            case HEADERS -> processHeaders(frame);
            case DATA -> processData(frame, outFrames);
            case WINDOW_UPDATE -> processWindowUpdate(frame);
            case PING -> processPing(frame, outFrames);
            case GOAWAY -> processGoaway(frame);
            case RST_STREAM -> processRstStream(frame);
            case PRIORITY -> processPriority(frame);
            case PUSH_PROMISE -> processPushPromise(frame);
            case CONTINUATION -> processContinuation(frame);
        }

        notifyListeners(frame);
        return outFrames;
    }

    private void processSettings(Http2Frame frame, List<Http2Frame> outFrames) {
        if (frame.hasFlag(Http2Flags.ACK)) return;
        var received = Http2Settings.decode(frame.payload());
        remoteSettings.applyFrom(received);
        streamManager.setMaxConcurrentStreams(remoteSettings.maxConcurrentStreams());
        encoder.setMaxTableSize(remoteSettings.headerTableSize());
        outFrames.add(Http2Frame.settingsAck());
    }

    private void processHeaders(Http2Frame frame) {
        int streamId = frame.streamId();
        var stream = streamManager.getOrCreateStream(streamId);
        if (stream.state() == Http2StreamState.IDLE) {
            stream.transitionTo(Http2StreamState.OPEN);
        }

        var headers = decoder.decodeToHttpHeaders(frame.payload());
        for (String name : headers.names()) {
            for (String value : headers.getAll(name)) {
                stream.headers().add(name, value);
            }
        }

        if (frame.hasFlag(Http2Flags.END_STREAM)) {
            if (stream.state() == Http2StreamState.OPEN) {
                stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);
            } else if (stream.state() == Http2StreamState.HALF_CLOSED_LOCAL) {
                stream.transitionTo(Http2StreamState.CLOSED);
            }
        }
        lastStreamId = Math.max(lastStreamId, streamId);
    }

    private void processData(Http2Frame frame, List<Http2Frame> outFrames) {
        int streamId = frame.streamId();
        var stream = streamManager.getStream(streamId);
        if (stream == null) {
            throw new IllegalStateException("DATA frame on unknown stream: " + streamId);
        }

        int length = frame.payloadLength();
        flowControl.consumeConnectionReceiveWindow(length);
        stream.consumeReceiveWindow(length);
        stream.addData(frame.payload());

        if (frame.hasFlag(Http2Flags.END_STREAM)) {
            if (stream.state() == Http2StreamState.OPEN) {
                stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);
            } else if (stream.state() == Http2StreamState.HALF_CLOSED_LOCAL) {
                stream.transitionTo(Http2StreamState.CLOSED);
            }
        }

        if (length > 0) {
            flowControl.restoreConnectionReceiveWindow(length);
            outFrames.add(Http2Frame.windowUpdate(0, length));
            outFrames.add(Http2Frame.windowUpdate(streamId, length));
        }
    }

    private void processWindowUpdate(Http2Frame frame) {
        var stream = frame.streamId() == 0 ? null : streamManager.getStream(frame.streamId());
        flowControl.processWindowUpdate(frame, stream);
    }

    private void processPing(Http2Frame frame, List<Http2Frame> outFrames) {
        if (!frame.hasFlag(Http2Flags.ACK)) {
            outFrames.add(Http2Frame.pingAck(frame.payload()));
        }
    }

    private void processGoaway(Http2Frame frame) {
        goawayReceived = true;
        var payload = frame.payload();
        lastStreamId = payload.getInt() & 0x7FFFFFFF;
    }

    private void processRstStream(Http2Frame frame) {
        var stream = streamManager.getStream(frame.streamId());
        if (stream != null && !stream.isClosed()) {
            stream.transitionTo(Http2StreamState.CLOSED);
        }
    }

    private void processPriority(Http2Frame frame) {
        // Priority is advisory; acknowledged but not enforced
    }

    private void processPushPromise(Http2Frame frame) {
        var payload = frame.payload();
        int promisedStreamId = payload.getInt() & 0x7FFFFFFF;
        var promisedStream = streamManager.getOrCreateStream(promisedStreamId);
        promisedStream.transitionTo(Http2StreamState.RESERVED_REMOTE);

        var headerBlock = payload.slice();
        var headers = decoder.decodeToHttpHeaders(headerBlock);
        for (String name : headers.names()) {
            for (String value : headers.getAll(name)) {
                promisedStream.headers().add(name, value);
            }
        }
    }

    private void processContinuation(Http2Frame frame) {
        int streamId = frame.streamId();
        var stream = streamManager.getStream(streamId);
        if (stream != null) {
            var headers = decoder.decodeToHttpHeaders(frame.payload());
            for (String name : headers.names()) {
                for (String value : headers.getAll(name)) {
                    stream.headers().add(name, value);
                }
            }
        }
    }

    private void notifyListeners(Http2Frame frame) {
        for (var listener : frameListeners) {
            listener.accept(frame);
        }
    }

    public Http2Frame createHeadersFrame(int streamId, ssg.legoflow.http.core.HttpHeaders headers,
                                          boolean endStream) {
        var encoded = encoder.encode(headers);
        return Http2Frame.headers(streamId, encoded, endStream, true);
    }

    public Http2Frame createDataFrame(int streamId, ByteBuffer data, boolean endStream) {
        return Http2Frame.data(streamId, data, endStream);
    }

    public Http2Frame createGoaway(Http2ErrorCode errorCode) {
        goawaySent = true;
        return Http2Frame.goaway(lastStreamId, errorCode, null);
    }

    public Http2StreamManager streamManager() {
        return streamManager;
    }

    public Http2FlowControl flowControl() {
        return flowControl;
    }

    public Http2Settings localSettings() {
        return localSettings;
    }

    public Http2Settings remoteSettings() {
        return remoteSettings;
    }

    public HpackEncoder encoder() {
        return encoder;
    }

    public HpackDecoder decoder() {
        return decoder;
    }

    public boolean isServer() {
        return isServer;
    }

    public boolean isPrefaceReceived() {
        return prefaceReceived;
    }

    public boolean isPrefaceSent() {
        return prefaceSent;
    }

    public boolean isGoawaySent() {
        return goawaySent;
    }

    public boolean isGoawayReceived() {
        return goawayReceived;
    }

    public int lastStreamId() {
        return lastStreamId;
    }
}
