package ssg.legoflow.http2.frame;

public final class Http2Flags {

    public static final byte END_STREAM = 0x1;
    public static final byte END_HEADERS = 0x4;
    public static final byte PADDED = 0x8;
    public static final byte PRIORITY_FLAG = 0x20;
    public static final byte ACK = 0x1;

    private Http2Flags() {}

    public static boolean hasFlag(byte flags, byte flag) {
        return (flags & flag) != 0;
    }

    public static byte setFlag(byte flags, byte flag) {
        return (byte) (flags | flag);
    }

    public static byte clearFlag(byte flags, byte flag) {
        return (byte) (flags & ~flag);
    }
}
