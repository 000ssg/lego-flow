package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;

/**
 * Stateful codec that accumulates raw bytes from NIO reads and emits
 * complete AMQP frames to the pipeline.
 *
 * <p>Byte streaming contract (from service module):
 * <ul>
 * <li>Transport (ProcessingThread) delivers arbitrary chunks — never accumulates.</li>
 * <li>This codec accumulates — handles partial frames naturally.</li>
 * <li>A single read may contain 0.5 frame, 1 frame, or 3.7 frames — all handled.</li>
 * </ul>
 *
 * <p>On each {@link #onRead}, incoming bytes are drained into an internal
 * accumulator. The loop checks for complete frames (header → size → body),
 * extracts them, and calls the callback. The remainder stays for the next read.
 */
public class AmqpFrameCodecImpl implements AmqpFrameCodec {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpFrameCodecImpl.class);
    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024; // 16 MB cap

    private final FrameExtractor extractor;
    private final FrameAccumulator accumulator;

    /**
     * Called by the pipeline when a complete frame is extracted from the
     * accumulator. The callback fires the frame to the next handler.
     */
    @FunctionalInterface
    public interface FrameExtractor {
        void extractFrame(DataChannel channel, ByteBuffer frameData);
    }

    public AmqpFrameCodecImpl(FrameExtractor extractor) {
        this.extractor = extractor;
        this.accumulator = new FrameAccumulator();
    }

    /**
     * Processes incoming byte chunks. Accumulates partial data, extracts
     * complete frames, and fires them to the pipeline.
     */
    @Override
    public void onRead(DataChannel channel, ByteBuffer data) {
        if (data.remaining() <= 0) return;

        accumulator.put(data);

        // Loop: extract complete frames while available
        while (true) {
            ByteBuffer frame = accumulator.extractCompleteFrame();
            if (frame == null) break; // partial frame — wait for next read

            try {
                extractor.extractFrame(channel, frame);
            } catch (Exception e) {
                LOG.error("Error extracting frame", e);
                return;
            }
        }
    }

    /**
     * Encodes a frame and writes it to the channel. Outbound frames are
     * encoded directly — no accumulation needed.
     */
    @Override
    public void onWrite(DataChannel channel) {
        // Outbound write is handled by the service layer (AmqpClientService.produce)
        // This method exists for pipeline compatibility
    }

    @Override
    public void onConnect(DataChannel channel) {
        accumulator.reset();
    }

    @Override
    public void onDisconnect(DataChannel channel) {
        accumulator.reset();
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.warn("Codec error on channel", cause);
        accumulator.reset();
    }

    @Override
    public ByteBuffer encode(Object frame) {
        if (frame instanceof AmqpFrame af) {
            // Encode: size(4) + doff(1) + type(1) + channel(2) + encoded performative + optional payload
            var bodyBuf = ssg.legoflow.messaging.amqp.types.TypeCodec.encode(af.performative());
            bodyBuf.flip();
            int payloadLen = af.payload() != null ? af.payload().remaining() : 0;
            int totalSize = 8 + bodyBuf.remaining() + payloadLen;
            ByteBuffer out = ByteBuffer.allocate(totalSize);
            out.putInt(totalSize);
            out.put((byte) 2); // doff
            out.put(af.type());
            out.putShort((short) af.channel());
            out.put(bodyBuf);
            if (payloadLen > 0 && af.payload().hasRemaining()) {
                out.put(af.payload());
            }
            out.flip();
            return out;
        }
        throw new IllegalArgumentException("Expected AmqpFrame, got: " + frame.getClass());
    }

    /**
     * Internal accumulator that collects bytes from partial reads and
     * extracts complete AMQP frames.
     */
    static class FrameAccumulator {
        private byte[] data = new byte[0];
        private int length = 0;

        void reset() {
            data = new byte[0];
            length = 0;
        }

        void put(ByteBuffer src) {
            int n = src.remaining();
            if (n == 0) return;
            // Ensure capacity
            int needed = length + n;
            if (needed > data.length) {
                if (needed > MAX_BUFFER_SIZE) {
                    throw new IllegalArgumentException("Accumulator exceeded " +
                            MAX_BUFFER_SIZE + " bytes");
                }
                byte[] larger = new byte[Math.max(needed, data.length * 2)];
                System.arraycopy(data, 0, larger, 0, length);
                data = larger;
            }
            src.get(data, length, n);
            length += n;
        }

        ByteBuffer extractCompleteFrame() {
            if (length < 4) return null;
            int size = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                       ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            if (size < 4 || length < size) return null;
            // Copy the frame out
            byte[] frameData = new byte[size];
            System.arraycopy(data, 0, frameData, 0, size);
            // Shift remaining bytes to front
            int remaining = length - size;
            if (remaining > 0) {
                System.arraycopy(data, size, data, 0, remaining);
            }
            length = remaining;
            return ByteBuffer.wrap(frameData);
        }
    }
}
