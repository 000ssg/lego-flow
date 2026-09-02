package ssg.legoflow.http.websocket;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.util.ArrayList;
import java.util.List;
public class WebSocketFrameCodec extends AbstractDataFilter<ByteBuffer> {

    private final Mode mode;
    private ByteBuffer accumulator;

    public enum Mode { ENCODE, DECODE }

    public WebSocketFrameCodec(Mode mode) {
        super(ByteBuffer.class);
        this.mode = mode;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return data;
    }

    public ByteBuffer encodeFrame(WebSocketFrame frame) {
        var out = new ByteArrayOutputStream();
        int firstByte = frame.getOpCode().code();
        if (frame.isFin()) firstByte |= 0x80;
        out.write(firstByte);

        var payloadBuf = frame.getPayload();
        var payload = new byte[payloadBuf.remaining()];
        payloadBuf.get(payload);
        int length = payload.length;

        if (length < 126) {
            out.write(frame.isMasked() ? (length | 0x80) : length);
        } else if (length < 65536) {
            out.write(frame.isMasked() ? (126 | 0x80) : 126);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(frame.isMasked() ? (127 | 0x80) : 127);
            var buf = BufferPool.getBuffer(8).putLong(length);
            out.writeBytes(buf.array());
        }

        if (frame.isMasked()) {
            var maskKeyBuf = frame.getMaskKey();
            var maskKey = new byte[maskKeyBuf.remaining()];
            maskKeyBuf.get(maskKey);
            out.writeBytes(maskKey);
            var masked = new byte[payload.length];
            for (int i = 0; i < payload.length; i++) {
                masked[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }
            out.writeBytes(masked);
        } else {
            out.writeBytes(payload);
        }
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Decodes a single WebSocket frame from a ByteBuffer that contains a complete frame.
     * Returns null if the buffer does not contain enough data for a complete frame,
     * instead of throwing ArrayIndexOutOfBoundsException.
     *
     * @param data the buffer containing frame bytes
     * @return the decoded frame, or null if insufficient data
     * @since 0.1.0
     */
    public WebSocketFrame decodeFrame(ByteBuffer data) {
        var bytes = new byte[data.remaining()];
        data.mark();
        data.duplicate().get(bytes);
        if (bytes.length < 2) throw new IllegalArgumentException("Frame too short");
        int pos = 0;
        boolean fin = (bytes[pos] & 0x80) != 0;
        var opCode = WebSocketOpCode.fromCode(bytes[pos] & 0x0F);
        pos++;

        boolean masked = (bytes[pos] & 0x80) != 0;
        long payloadLen = bytes[pos] & 0x7F;
        pos++;

        if (payloadLen == 126) {
            if (bytes.length < pos + 2) return null;
            payloadLen = ((bytes[pos] & 0xFF) << 8) | (bytes[pos + 1] & 0xFF);
            pos += 2;
        } else if (payloadLen == 127) {
            if (bytes.length < pos + 8) return null;
            payloadLen = ByteBuffer.wrap(bytes, pos, 8).getLong();
            pos += 8;
        }

        if (masked) {
            if (bytes.length < pos + 4) return null;
        }

        int totalNeeded = pos + (masked ? 4 : 0) + (int) payloadLen;
        if (bytes.length < totalNeeded) return null;

        ByteBuffer maskKey = null;
        if (masked) {
            var maskKeyBytes = new byte[4];
            System.arraycopy(bytes, pos, maskKeyBytes, 0, 4);
            pos += 4;
            maskKey = ByteBuffer.wrap(maskKeyBytes);
        }

        var payload = new byte[(int) payloadLen];
        System.arraycopy(bytes, pos, payload, 0, (int) payloadLen);

        if (masked) {
            var mkBytes = new byte[maskKey.remaining()];
            maskKey.duplicate().get(mkBytes);
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mkBytes[i % 4]);
            }
        }

        return new WebSocketFrame(fin, opCode, masked, ByteBuffer.wrap(payload), maskKey);
    }

    /**
     * Decodes zero or more complete WebSocket frames from a stream of ByteBuffers.
     * Partial frames are accumulated internally and completed on subsequent calls.
     *
     * @param data one or more ByteBuffers of incoming data
     * @return list of complete decoded frames (may be empty if data is partial)
     * @since 0.1.0
     */
    public List<WebSocketFrame> decodeFrames(ByteBuffer... data) {
        var combined = combineWithAccumulator(data);
        var frames = new ArrayList<WebSocketFrame>();

        while (combined.remaining() >= 2) {
            combined.mark();
            int pos = combined.position();
            byte[] header = new byte[2];
            combined.get(header);

            boolean fin = (header[0] & 0x80) != 0;
            var opCode = WebSocketOpCode.fromCode(header[0] & 0x0F);
            boolean masked = (header[1] & 0x80) != 0;
            long payloadLen = header[1] & 0x7F;

            if (payloadLen == 126) {
                if (combined.remaining() < 2) {
                    combined.reset();
                    break;
                }
                payloadLen = ((combined.get() & 0xFF) << 8) | (combined.get() & 0xFF);
            } else if (payloadLen == 127) {
                if (combined.remaining() < 8) {
                    combined.reset();
                    break;
                }
                payloadLen = combined.getLong();
            }

            int needed = (masked ? 4 : 0) + (int) payloadLen;
            if (combined.remaining() < needed) {
                combined.reset();
                break;
            }

            ByteBuffer maskKey = null;
            if (masked) {
                var maskKeyBytes = new byte[4];
                combined.get(maskKeyBytes);
                maskKey = ByteBuffer.wrap(maskKeyBytes);
            }

            var payload = new byte[(int) payloadLen];
            combined.get(payload);

            if (masked) {
                var mkBytes = new byte[maskKey.remaining()];
                maskKey.duplicate().get(mkBytes);
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mkBytes[i % 4]);
                }
            }

            frames.add(new WebSocketFrame(fin, opCode, masked, ByteBuffer.wrap(payload), maskKey));
        }

        // Save remainder to accumulator
        if (combined.hasRemaining()) {
            accumulator = BufferPool.getBuffer(combined.remaining());
            accumulator.put(combined);
            accumulator.flip();
        } else {
            accumulator = null;
        }

        return frames;
    }

    private ByteBuffer combineWithAccumulator(ByteBuffer[] data) {
        int totalSize = (accumulator != null ? accumulator.remaining() : 0);
        for (var buf : data) {
            totalSize += buf.remaining();
        }
        var combined = BufferPool.getBuffer(totalSize);
        if (accumulator != null) {
            combined.put(accumulator.duplicate());
            accumulator = null;
        }
        for (var buf : data) {
            combined.put(buf.duplicate());
        }
        combined.flip();
        return combined;
    }

    /**
     * Returns whether this codec has buffered partial data from a previous decode call.
     *
     * @return true if there is buffered data awaiting more input
     * @since 0.1.0
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }
}
