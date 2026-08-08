package ssg.legoflow.database.mysql.auth;

import ssg.legoflow.database.mysql.protocol.LengthEncodedString;

import java.nio.ByteBuffer;

/**
 * MySQL AuthSwitchRequest packet (0xFE header in auth context).
 *
 * <p>Sent by the server to request the client to switch to a different
 * authentication plugin.
 *
 * @param pluginName the name of the auth plugin to switch to
 * @param pluginData the auth plugin data (scramble) for the new plugin
 * @since 0.1.0
 */
public record AuthSwitchRequest(String pluginName, byte[] pluginData) {

    /** AuthSwitchRequest header byte. */
    public static final int HEADER = 0xFE;

    /** AuthMoreData header byte. */
    public static final int MORE_DATA_HEADER = 0x01;

    /**
     * Decodes an AuthSwitchRequest from payload bytes.
     *
     * @param payload the packet payload
     * @return the decoded auth switch request
     */
    public static AuthSwitchRequest decode(byte[] payload) {
        var buf = ByteBuffer.wrap(payload);
        buf.get(); // skip header 0xFE

        String pluginName = LengthEncodedString.readNullTerminated(buf);
        byte[] pluginData = LengthEncodedString.readRestOfPacketBytes(buf);

        // Remove trailing null byte if present
        if (pluginData.length > 0 && pluginData[pluginData.length - 1] == 0) {
            var trimmed = new byte[pluginData.length - 1];
            System.arraycopy(pluginData, 0, trimmed, 0, trimmed.length);
            pluginData = trimmed;
        }

        return new AuthSwitchRequest(pluginName, pluginData);
    }

    /**
     * Encodes this AuthSwitchRequest as payload bytes.
     *
     * @return the encoded payload
     */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(1 + pluginName.length() + 1 + pluginData.length + 1);
        buf.put((byte) HEADER);
        LengthEncodedString.writeNullTerminated(buf, pluginName);
        buf.put(pluginData);
        buf.put((byte) 0); // trailing null

        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Checks if a payload is an AuthSwitchRequest.
     *
     * @param payload the packet payload
     * @return true if this is an auth switch request (0xFE with length >= 2)
     */
    public static boolean isAuthSwitch(byte[] payload) {
        return payload.length >= 2 && (payload[0] & 0xFF) == HEADER;
    }

    /**
     * Checks if a payload is an AuthMoreData packet.
     *
     * @param payload the packet payload
     * @return true if this is an auth more data packet (0x01 header)
     */
    public static boolean isAuthMoreData(byte[] payload) {
        return payload.length >= 2 && (payload[0] & 0xFF) == MORE_DATA_HEADER;
    }
}
