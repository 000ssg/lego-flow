package ssg.legoflow.media.rtsp.interleaved;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages interleaved RTP/RTCP transport over an RTSP TCP connection.
 *
 * <p>Provides channel registration and frame dispatch for multiplexed
 * binary data on the RTSP TCP stream. Typically channel 0 is RTP and
 * channel 1 is RTCP for the first media stream.
 *
 * @since 1.0.0
 */
public final class InterleavedTransport implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InterleavedTransport.class);

    private final Map<Integer, Consumer<byte[]>> channelHandlers = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * Creates an interleaved transport.
     */
    public InterleavedTransport() {
        this.closed = false;
    }

    /**
     * Registers a handler for a specific channel.
     *
     * @param channel the channel number (0-255)
     * @param handler the handler to receive frame data
     */
    public void registerChannel(int channel, Consumer<byte[]> handler) {
        Objects.requireNonNull(handler, "handler");
        channelHandlers.put(channel, handler);
        LOG.debug("Registered handler for interleaved channel {}", channel);
    }

    /**
     * Unregisters the handler for a specific channel.
     *
     * @param channel the channel number
     */
    public void unregisterChannel(int channel) {
        channelHandlers.remove(channel);
    }

    /**
     * Dispatches a received interleaved frame to the registered handler.
     *
     * @param frame the received frame
     */
    public void dispatch(InterleavedFrame frame) {
        var handler = channelHandlers.get(frame.channel());
        if (handler != null) {
            handler.accept(frame.data());
        } else {
            LOG.warn("No handler for interleaved channel {}, dropping {} bytes",
                    frame.channel(), frame.data().length);
        }
    }

    /**
     * Sends an interleaved frame on the given output stream.
     *
     * @param out   the output stream (RTSP TCP connection)
     * @param frame the frame to send
     * @throws IOException if an I/O error occurs
     */
    public void send(OutputStream out, InterleavedFrame frame) throws IOException {
        if (closed) {
            throw new IOException("Transport is closed");
        }
        byte[] encoded = InterleavedFrameCodec.encode(frame);
        synchronized (out) {
            out.write(encoded);
            out.flush();
        }
    }

    /**
     * Sends RTP/RTCP data on a specific channel.
     *
     * @param out     the output stream
     * @param channel the channel number
     * @param data    the RTP/RTCP packet data
     * @throws IOException if an I/O error occurs
     */
    public void send(OutputStream out, int channel, byte[] data) throws IOException {
        send(out, new InterleavedFrame(channel, data));
    }

    /**
     * Returns the number of registered channels.
     *
     * @return the channel count
     */
    public int channelCount() {
        return channelHandlers.size();
    }

    @Override
    public void close() {
        closed = true;
        channelHandlers.clear();
    }

    @Override
    public String toString() {
        return "InterleavedTransport[channels=" + channelHandlers.keySet() + "]";
    }
}
