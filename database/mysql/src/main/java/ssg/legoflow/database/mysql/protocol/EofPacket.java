package ssg.legoflow.database.mysql.protocol;

import java.nio.ByteBuffer;

/**
 * MySQL EOF_Packet (0xFE header, payload length &lt; 9).
 *
 * <p>Sent to mark the end of column definitions and rows in a result set.
 * When CLIENT_DEPRECATE_EOF is negotiated, OK packets are used instead.
 *
 * @param warnings number of warnings
 * @param statusFlags server status flags
 * @since 1.0.0
 */
public record EofPacket(int warnings, int statusFlags) {

    /** EOF packet header byte. */
    public static final int HEADER = 0xFE;

    /**
     * Checks if a payload represents an EOF packet.
     *
     * <p>An EOF packet has header 0xFE and payload length less than 9 bytes
     * (to distinguish from a length-encoded integer or OK-as-EOF).
     *
     * @param payload the packet payload
     * @return true if this is an EOF packet
     */
    public static boolean isEof(byte[] payload) {
        return payload.length > 0
                && (payload[0] & 0xFF) == HEADER
                && payload.length < 9;
    }

    /**
     * Decodes an EOF packet from payload bytes.
     *
     * @param payload the packet payload
     * @param capabilities the negotiated capabilities
     * @return the decoded EOF packet
     */
    public static EofPacket decode(byte[] payload, int capabilities) {
        var buf = ByteBuffer.wrap(payload);
        buf.get(); // skip header 0xFE

        int warnings = 0;
        int statusFlags = 0;
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PROTOCOL_41)) {
            if (buf.remaining() >= 4) {
                warnings = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
                statusFlags = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
            }
        }

        return new EofPacket(warnings, statusFlags);
    }

    /**
     * Encodes this EOF packet as payload bytes.
     *
     * @param capabilities the negotiated capabilities
     * @return the encoded payload
     */
    public byte[] encode(int capabilities) {
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PROTOCOL_41)) {
            var buf = ByteBuffer.allocate(5);
            buf.put((byte) HEADER);
            buf.put((byte) (warnings & 0xFF));
            buf.put((byte) ((warnings >> 8) & 0xFF));
            buf.put((byte) (statusFlags & 0xFF));
            buf.put((byte) ((statusFlags >> 8) & 0xFF));
            return buf.array();
        } else {
            return new byte[]{(byte) HEADER};
        }
    }

    /**
     * Creates a default EOF packet with autocommit status.
     *
     * @return a basic EOF packet
     */
    public static EofPacket eof() {
        return new EofPacket(0, StatusFlags.DEFAULT_STATUS);
    }
}
