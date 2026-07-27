package ssg.legoflow.http3;

import ssg.legoflow.http3.qpack.QpackDecoder;
import ssg.legoflow.http3.qpack.QpackEncoder;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicPacketCodec;
import ssg.legoflow.http3.quic.QuicStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * HTTP/3 connection layered on top of a QUIC connection.
 *
 * <p>Manages the HTTP/3 control stream, QPACK encoder and decoder streams,
 * SETTINGS exchange, and request/response processing. Bidirectional QUIC
 * streams carry HTTP/3 request and response frames (HEADERS + DATA),
 * while unidirectional streams carry control and QPACK data.</p>
 *
 * <p>This class is thread-safe. Stream maps and listener lists use
 * concurrent data structures.</p>
 *
 * @since 1.0.0
 */
public class Http3Connection {

    private static final Logger LOG = LoggerFactory.getLogger(Http3Connection.class);

    /** Unidirectional stream type for control stream. */
    public static final long STREAM_TYPE_CONTROL = 0x00;

    /** Unidirectional stream type for QPACK encoder stream. */
    public static final long STREAM_TYPE_QPACK_ENCODER = 0x02;

    /** Unidirectional stream type for QPACK decoder stream. */
    public static final long STREAM_TYPE_QPACK_DECODER = 0x03;

    private final QuicConnection quicConnection;
    private final Http3Settings localSettings;
    private volatile Http3Settings remoteSettings;
    private final QpackEncoder encoder;
    private final QpackDecoder decoder;
    private final Http3FrameCodec frameCodec;
    private final Map<Long, QuicStream> requestStreams = new ConcurrentHashMap<>();
    private final List<Consumer<Http3Frame>> frameListeners = new CopyOnWriteArrayList<>();

    private volatile QuicStream controlStream;
    private volatile QuicStream qpackEncoderStream;
    private volatile QuicStream qpackDecoderStream;
    private volatile boolean settingsSent;
    private volatile boolean goawaySent;
    private volatile boolean goawayReceived;
    private volatile long lastStreamId;

    /**
     * Creates a new HTTP/3 connection wrapping the given QUIC connection.
     *
     * @param quicConnection the underlying QUIC connection
     * @since 1.0.0
     */
    public Http3Connection(QuicConnection quicConnection) {
        this(quicConnection, new Http3Settings());
    }

    /**
     * Creates a new HTTP/3 connection with the given settings.
     *
     * @param quicConnection the underlying QUIC connection
     * @param settings       the local HTTP/3 settings
     * @since 1.0.0
     */
    public Http3Connection(QuicConnection quicConnection, Http3Settings settings) {
        this.quicConnection = quicConnection;
        this.localSettings = settings;
        this.remoteSettings = new Http3Settings();
        this.encoder = new QpackEncoder((int) settings.qpackMaxTableCapacity());
        this.decoder = new QpackDecoder((int) settings.qpackMaxTableCapacity());
        this.frameCodec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
    }

    /**
     * Initialises the HTTP/3 connection by creating control and QPACK streams,
     * and sending the initial SETTINGS frame.
     *
     * @since 1.0.0
     */
    public void initialize() {
        // Create control stream (unidirectional, type 0x00)
        controlStream = quicConnection.createStream(false);
        sendStreamType(controlStream, STREAM_TYPE_CONTROL);
        sendSettings();

        // Create QPACK encoder stream (unidirectional, type 0x02)
        qpackEncoderStream = quicConnection.createStream(false);
        sendStreamType(qpackEncoderStream, STREAM_TYPE_QPACK_ENCODER);

        // Create QPACK decoder stream (unidirectional, type 0x03)
        qpackDecoderStream = quicConnection.createStream(false);
        sendStreamType(qpackDecoderStream, STREAM_TYPE_QPACK_DECODER);

        LOG.info("HTTP/3 connection initialized with control stream {}, encoder stream {}, decoder stream {}",
                controlStream.streamId(), qpackEncoderStream.streamId(), qpackDecoderStream.streamId());
    }

    /**
     * Sends a request on a new bidirectional QUIC stream.
     *
     * @param headers the request headers (pseudo-headers + regular headers)
     * @param body    the request body, or {@code null}
     * @return the QUIC stream used for the request
     * @since 1.0.0
     */
    public QuicStream sendRequest(List<Map.Entry<String, String>> headers, ByteBuffer body) {
        var stream = quicConnection.createStream(true);
        requestStreams.put(stream.streamId(), stream);

        // Encode and send HEADERS frame
        var encodedHeaders = encoder.encode(headers);
        var headersFrame = Http3Frame.headers(encodedHeaders);
        sendFrame(stream, headersFrame);

        // Send DATA frame if body is present
        if (body != null && body.hasRemaining()) {
            var dataFrame = Http3Frame.data(body);
            sendFrame(stream, dataFrame);
        }

        lastStreamId = Math.max(lastStreamId, stream.streamId());
        return stream;
    }

    /**
     * Sends a response on the given QUIC stream.
     *
     * @param stream  the request stream to respond on
     * @param headers the response headers (pseudo-headers + regular headers)
     * @param body    the response body, or {@code null}
     * @since 1.0.0
     */
    public void sendResponse(QuicStream stream, List<Map.Entry<String, String>> headers, ByteBuffer body) {
        var encodedHeaders = encoder.encode(headers);
        var headersFrame = Http3Frame.headers(encodedHeaders);
        sendFrame(stream, headersFrame);

        if (body != null && body.hasRemaining()) {
            var dataFrame = Http3Frame.data(body);
            sendFrame(stream, dataFrame);
        }
    }

    /**
     * Sends a PUSH_PROMISE on the given stream.
     *
     * @param stream        the request stream
     * @param pushId        the push ID
     * @param promiseHeaders the promised request headers
     * @since 1.0.0
     */
    public void sendPushPromise(QuicStream stream, long pushId, List<Map.Entry<String, String>> promiseHeaders) {
        var encodedHeaders = encoder.encode(promiseHeaders);
        var buf = ByteBuffer.allocate(8 + encodedHeaders.remaining());
        QuicPacketCodec.encodeVarInt(buf, pushId);
        buf.put(encodedHeaders.duplicate());
        buf.flip();
        sendFrame(stream, Http3Frame.pushPromise(buf));
    }

    /**
     * Receives and decodes headers from the given stream's data.
     *
     * @param stream the stream to read from
     * @return the decoded headers, or an empty list if no data is available
     * @since 1.0.0
     */
    public List<Map.Entry<String, String>> receiveHeaders(QuicStream stream) {
        var data = stream.getAccumulatedData();
        if (!data.hasRemaining()) {
            return List.of();
        }

        // Decode the first frame (should be HEADERS)
        var frame = frameCodec.decodeFrame(data);
        if (frame.type() != Http3FrameType.HEADERS) {
            throw new IllegalStateException("Expected HEADERS frame, got " + frame.type());
        }

        return decoder.decode(frame.payload().duplicate());
    }

    /**
     * Receives body data from the given stream.
     *
     * @param stream the stream to read from
     * @return the body data, or an empty buffer
     * @since 1.0.0
     */
    public ByteBuffer receiveBody(QuicStream stream) {
        var data = stream.getAccumulatedData();
        if (!data.hasRemaining()) {
            return ByteBuffer.allocate(0);
        }

        // Skip HEADERS frame to find DATA frame
        var combined = new ArrayList<ByteBuffer>();
        while (data.hasRemaining()) {
            data.mark();
            try {
                var frame = frameCodec.decodeFrame(data);
                if (frame.type() == Http3FrameType.DATA) {
                    combined.add(frame.payload().duplicate());
                }
            } catch (Exception e) {
                data.reset();
                break;
            }
        }

        if (combined.isEmpty()) {
            return ByteBuffer.allocate(0);
        }

        int total = combined.stream().mapToInt(ByteBuffer::remaining).sum();
        var result = ByteBuffer.allocate(total);
        for (var buf : combined) {
            result.put(buf);
        }
        result.flip();
        return result;
    }

    /**
     * Sends a GOAWAY frame on the control stream.
     *
     * @param lastStreamId the last stream ID that was or might be processed
     * @since 1.0.0
     */
    public void sendGoaway(long lastStreamId) {
        if (controlStream == null) {
            throw new IllegalStateException("Control stream not initialized");
        }
        var buf = ByteBuffer.allocate(8);
        QuicPacketCodec.encodeVarInt(buf, lastStreamId);
        buf.flip();
        sendFrame(controlStream, Http3Frame.goaway(buf));
        goawaySent = true;
        LOG.info("GOAWAY sent with last stream ID {}", lastStreamId);
    }

    /**
     * Adds a listener for frames processed by this connection.
     *
     * @param listener the frame listener
     * @since 1.0.0
     */
    public void addFrameListener(Consumer<Http3Frame> listener) {
        frameListeners.add(listener);
    }

    /**
     * Closes the HTTP/3 connection gracefully.
     *
     * @since 1.0.0
     */
    public void close() {
        if (!goawaySent && controlStream != null && quicConnection.isConnected()) {
            sendGoaway(lastStreamId);
        }
        quicConnection.close(
                ssg.legoflow.http3.quic.QuicErrorCode.NO_ERROR,
                "HTTP/3 connection closed");
    }

    /**
     * Returns whether the connection is currently active.
     *
     * @return {@code true} if the QUIC connection is connected
     * @since 1.0.0
     */
    public boolean isConnected() {
        return quicConnection.isConnected();
    }

    /**
     * Returns the underlying QUIC connection.
     *
     * @return the QUIC connection
     * @since 1.0.0
     */
    public QuicConnection quicConnection() {
        return quicConnection;
    }

    /**
     * Returns the local HTTP/3 settings.
     *
     * @return the local settings
     * @since 1.0.0
     */
    public Http3Settings localSettings() {
        return localSettings;
    }

    /**
     * Returns the remote HTTP/3 settings.
     *
     * @return the remote settings
     * @since 1.0.0
     */
    public Http3Settings remoteSettings() {
        return remoteSettings;
    }

    /**
     * Sets the remote settings (received from peer).
     *
     * @param settings the remote settings
     * @since 1.0.0
     */
    public void setRemoteSettings(Http3Settings settings) {
        this.remoteSettings = settings;
    }

    /**
     * Returns the QPACK encoder.
     *
     * @return the encoder
     * @since 1.0.0
     */
    public QpackEncoder encoder() {
        return encoder;
    }

    /**
     * Returns the QPACK decoder.
     *
     * @return the decoder
     * @since 1.0.0
     */
    public QpackDecoder decoder() {
        return decoder;
    }

    /**
     * Returns the HTTP/3 frame codec.
     *
     * @return the frame codec
     * @since 1.0.0
     */
    public Http3FrameCodec frameCodec() {
        return frameCodec;
    }

    /**
     * Returns the control stream.
     *
     * @return the control stream, or {@code null} if not initialized
     * @since 1.0.0
     */
    public QuicStream controlStream() {
        return controlStream;
    }

    /**
     * Returns the QPACK encoder stream.
     *
     * @return the encoder stream, or {@code null} if not initialized
     * @since 1.0.0
     */
    public QuicStream qpackEncoderStream() {
        return qpackEncoderStream;
    }

    /**
     * Returns the QPACK decoder stream.
     *
     * @return the decoder stream, or {@code null} if not initialized
     * @since 1.0.0
     */
    public QuicStream qpackDecoderStream() {
        return qpackDecoderStream;
    }

    /**
     * Returns whether a GOAWAY has been sent.
     *
     * @return {@code true} if GOAWAY was sent
     * @since 1.0.0
     */
    public boolean isGoawaySent() {
        return goawaySent;
    }

    /**
     * Returns whether a GOAWAY has been received.
     *
     * @return {@code true} if GOAWAY was received
     * @since 1.0.0
     */
    public boolean isGoawayReceived() {
        return goawayReceived;
    }

    /**
     * Marks that a GOAWAY was received from the peer.
     *
     * @since 1.0.0
     */
    public void markGoawayReceived() {
        this.goawayReceived = true;
    }

    /**
     * Returns the last stream ID.
     *
     * @return the last stream ID
     * @since 1.0.0
     */
    public long lastStreamId() {
        return lastStreamId;
    }

    /**
     * Returns the map of active request streams.
     *
     * @return the request stream map
     * @since 1.0.0
     */
    public Map<Long, QuicStream> requestStreams() {
        return Map.copyOf(requestStreams);
    }

    private void sendSettings() {
        var settingsPayload = localSettings.encode();
        var settingsFrame = Http3Frame.settings(settingsPayload);
        sendFrame(controlStream, settingsFrame);
        settingsSent = true;
    }

    private void sendStreamType(QuicStream stream, long type) {
        var buf = ByteBuffer.allocate(8);
        QuicPacketCodec.encodeVarInt(buf, type);
        buf.flip();
        stream.send(buf);
    }

    private void sendFrame(QuicStream stream, Http3Frame frame) {
        var encoded = frameCodec.encodeFrame(frame);
        stream.send(encoded);
        for (var listener : frameListeners) {
            listener.accept(frame);
        }
    }
}
