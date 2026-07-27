package ssg.legoflow.http2.frame;

import java.nio.ByteBuffer;

public final class Http2Frame {

    public static final int HEADER_SIZE = 9;

    private final Http2FrameType type;
    private final byte flags;
    private final int streamId;
    private final ByteBuffer payload;

    public Http2Frame(Http2FrameType type, byte flags, int streamId, ByteBuffer payload) {
        this.type = type;
        this.flags = flags;
        this.streamId = streamId & 0x7FFFFFFF;
        this.payload = payload != null ? payload.asReadOnlyBuffer() : ByteBuffer.allocate(0);
    }

    public Http2FrameType type() {
        return type;
    }

    public byte flags() {
        return flags;
    }

    public int streamId() {
        return streamId;
    }

    public ByteBuffer payload() {
        return payload.duplicate();
    }

    public int payloadLength() {
        return payload.remaining();
    }

    public boolean hasFlag(byte flag) {
        return Http2Flags.hasFlag(flags, flag);
    }

    public static Http2Frame data(int streamId, ByteBuffer data, boolean endStream) {
        byte flags = endStream ? Http2Flags.END_STREAM : 0;
        return new Http2Frame(Http2FrameType.DATA, flags, streamId, data);
    }

    public static Http2Frame headers(int streamId, ByteBuffer headerBlock, boolean endStream, boolean endHeaders) {
        byte flags = 0;
        if (endStream) flags = Http2Flags.setFlag(flags, Http2Flags.END_STREAM);
        if (endHeaders) flags = Http2Flags.setFlag(flags, Http2Flags.END_HEADERS);
        return new Http2Frame(Http2FrameType.HEADERS, flags, streamId, headerBlock);
    }

    public static Http2Frame priority(int streamId, int dependencyStreamId, int weight, boolean exclusive) {
        var buf = ByteBuffer.allocate(5);
        int dep = exclusive ? (dependencyStreamId | 0x80000000) : dependencyStreamId;
        buf.putInt(dep);
        buf.put((byte) (weight - 1));
        buf.flip();
        return new Http2Frame(Http2FrameType.PRIORITY, (byte) 0, streamId, buf);
    }

    public static Http2Frame rstStream(int streamId, Http2ErrorCode errorCode) {
        var buf = ByteBuffer.allocate(4);
        buf.putInt(errorCode.code());
        buf.flip();
        return new Http2Frame(Http2FrameType.RST_STREAM, (byte) 0, streamId, buf);
    }

    public static Http2Frame settings(ByteBuffer settingsPayload) {
        return new Http2Frame(Http2FrameType.SETTINGS, (byte) 0, 0, settingsPayload);
    }

    public static Http2Frame settingsAck() {
        return new Http2Frame(Http2FrameType.SETTINGS, Http2Flags.ACK, 0, null);
    }

    public static Http2Frame pushPromise(int streamId, int promisedStreamId, ByteBuffer headerBlock) {
        var buf = ByteBuffer.allocate(4 + headerBlock.remaining());
        buf.putInt(promisedStreamId & 0x7FFFFFFF);
        buf.put(headerBlock.duplicate());
        buf.flip();
        return new Http2Frame(Http2FrameType.PUSH_PROMISE, Http2Flags.END_HEADERS, streamId, buf);
    }

    public static Http2Frame ping(ByteBuffer opaqueData) {
        if (opaqueData.remaining() != 8) {
            throw new IllegalArgumentException("PING payload must be exactly 8 bytes");
        }
        return new Http2Frame(Http2FrameType.PING, (byte) 0, 0, opaqueData);
    }

    public static Http2Frame pingAck(ByteBuffer opaqueData) {
        if (opaqueData.remaining() != 8) {
            throw new IllegalArgumentException("PING ACK payload must be exactly 8 bytes");
        }
        return new Http2Frame(Http2FrameType.PING, Http2Flags.ACK, 0, opaqueData);
    }

    public static Http2Frame goaway(int lastStreamId, Http2ErrorCode errorCode, ByteBuffer debugData) {
        int debugLen = debugData != null ? debugData.remaining() : 0;
        var buf = ByteBuffer.allocate(8 + debugLen);
        buf.putInt(lastStreamId & 0x7FFFFFFF);
        buf.putInt(errorCode.code());
        if (debugData != null) buf.put(debugData.duplicate());
        buf.flip();
        return new Http2Frame(Http2FrameType.GOAWAY, (byte) 0, 0, buf);
    }

    public static Http2Frame windowUpdate(int streamId, int increment) {
        if (increment <= 0 || increment > 0x7FFFFFFF) {
            throw new IllegalArgumentException("Window increment must be between 1 and 2^31-1");
        }
        var buf = ByteBuffer.allocate(4);
        buf.putInt(increment & 0x7FFFFFFF);
        buf.flip();
        return new Http2Frame(Http2FrameType.WINDOW_UPDATE, (byte) 0, streamId, buf);
    }

    public static Http2Frame continuation(int streamId, ByteBuffer headerBlock, boolean endHeaders) {
        byte flags = endHeaders ? Http2Flags.END_HEADERS : 0;
        return new Http2Frame(Http2FrameType.CONTINUATION, flags, streamId, headerBlock);
    }

    public ByteBuffer encode() {
        var payloadDup = payload.duplicate();
        int length = payloadDup.remaining();
        var buf = ByteBuffer.allocate(HEADER_SIZE + length);
        buf.put((byte) ((length >> 16) & 0xFF));
        buf.put((byte) ((length >> 8) & 0xFF));
        buf.put((byte) (length & 0xFF));
        buf.put((byte) type.code());
        buf.put(flags);
        buf.putInt(streamId & 0x7FFFFFFF);
        buf.put(payloadDup);
        buf.flip();
        return buf;
    }

    public static Http2Frame decode(ByteBuffer data) {
        if (data.remaining() < HEADER_SIZE) {
            throw new IllegalArgumentException("Insufficient data for frame header");
        }
        var buf = data.duplicate();
        int length = ((buf.get() & 0xFF) << 16) | ((buf.get() & 0xFF) << 8) | (buf.get() & 0xFF);
        int typeCode = buf.get() & 0xFF;
        byte flags = buf.get();
        int streamId = buf.getInt() & 0x7FFFFFFF;

        if (buf.remaining() < length) {
            throw new IllegalArgumentException("Insufficient data for frame payload");
        }

        var payload = ByteBuffer.allocate(length);
        for (int i = 0; i < length; i++) {
            payload.put(buf.get());
        }
        payload.flip();

        return new Http2Frame(Http2FrameType.fromCode(typeCode), flags, streamId, payload);
    }
}
