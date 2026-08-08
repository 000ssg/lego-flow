package ssg.legoflow.database.mysql.protocol;

import java.nio.ByteBuffer;

/**
 * MySQL OK_Packet (0x00 or 0xFE header).
 *
 * <p>Sent by the server to indicate successful completion of a command.
 * Contains affected rows, last insert ID, status flags, warnings, and
 * optional info/session state change data.
 *
 * @param affectedRows number of rows affected by the command
 * @param lastInsertId last AUTO_INCREMENT value
 * @param statusFlags server status flags
 * @param warnings number of warnings
 * @param info human-readable status information
 * @since 0.1.0
 */
public record OkPacket(
        long affectedRows,
        long lastInsertId,
        int statusFlags,
        int warnings,
        String info
) {

    /** OK packet header byte. */
    public static final int HEADER = 0x00;

    /** EOF-as-OK header byte (when CLIENT_DEPRECATE_EOF is set). */
    public static final int EOF_HEADER = 0xFE;

    /**
     * Decodes an OK packet from payload bytes.
     *
     * @param payload the packet payload (starting after sequence header)
     * @param capabilities the negotiated capabilities
     * @return the decoded OK packet
     */
    public static OkPacket decode(byte[] payload, int capabilities) {
        var buf = ByteBuffer.wrap(payload);
        buf.get(); // skip header (0x00 or 0xFE)

        long affectedRows = LengthEncodedInt.read(buf);
        long lastInsertId = LengthEncodedInt.read(buf);

        int statusFlags = 0;
        int warnings = 0;
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PROTOCOL_41)) {
            statusFlags = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
            warnings = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
        }

        String info = "";
        if (buf.hasRemaining()) {
            if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_SESSION_TRACK)) {
                info = LengthEncodedString.read(buf);
                if (info == null) info = "";
            } else {
                info = LengthEncodedString.readRestOfPacket(buf);
            }
        }

        return new OkPacket(affectedRows, lastInsertId, statusFlags, warnings, info);
    }

    /**
     * Encodes this OK packet as payload bytes.
     *
     * @param capabilities the negotiated capabilities
     * @return the encoded payload
     */
    public byte[] encode(int capabilities) {
        var buf = ByteBuffer.allocate(1024);
        buf.put((byte) HEADER);
        LengthEncodedInt.write(buf, affectedRows);
        LengthEncodedInt.write(buf, lastInsertId);

        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PROTOCOL_41)) {
            buf.put((byte) (statusFlags & 0xFF));
            buf.put((byte) ((statusFlags >> 8) & 0xFF));
            buf.put((byte) (warnings & 0xFF));
            buf.put((byte) ((warnings >> 8) & 0xFF));
        }

        if (info != null && !info.isEmpty()) {
            if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_SESSION_TRACK)) {
                LengthEncodedString.write(buf, info);
            } else {
                buf.put(info.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Creates a simple OK packet with defaults.
     *
     * @return a basic OK packet with autocommit status
     */
    public static OkPacket ok() {
        return new OkPacket(0, 0, StatusFlags.DEFAULT_STATUS, 0, "");
    }

    /**
     * Creates an OK packet with affected rows.
     *
     * @param affectedRows the number of affected rows
     * @param lastInsertId the last insert ID
     * @return the OK packet
     */
    public static OkPacket ok(long affectedRows, long lastInsertId) {
        return new OkPacket(affectedRows, lastInsertId, StatusFlags.DEFAULT_STATUS, 0, "");
    }

    /**
     * Encodes this OK packet using the 0xFE header for use as end-of-result-set
     * marker when {@code CLIENT_DEPRECATE_EOF} is active. This avoids ambiguity
     * with binary protocol rows that also start with 0x00.
     *
     * @param capabilities the negotiated capabilities
     * @return the encoded payload with 0xFE header
     */
    public byte[] encodeAsEof(int capabilities) {
        byte[] encoded = encode(capabilities);
        encoded[0] = (byte) EOF_HEADER;
        return encoded;
    }
}
