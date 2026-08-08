package ssg.legoflow.database.mysql.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * MySQL HandshakeV10 packet — the initial server greeting.
 *
 * <p>Sent by the server immediately after a client connects. Contains
 * protocol version, server version string, connection ID, auth data,
 * capabilities, charset, status flags, and auth plugin name.
 *
 * @param protocolVersion protocol version (always 10)
 * @param serverVersion server version string (e.g., "8.0.33-legoflow")
 * @param connectionId unique connection identifier
 * @param authPluginDataPart1 first 8 bytes of auth plugin data (scramble)
 * @param capabilityFlagsLower lower 2 bytes of capability flags
 * @param characterSet default character set
 * @param statusFlags server status flags
 * @param capabilityFlagsUpper upper 2 bytes of capability flags
 * @param authPluginDataLength total length of auth plugin data
 * @param authPluginDataPart2 remaining auth plugin data
 * @param authPluginName name of the default auth plugin
 * @since 0.1.0
 */
public record HandshakeV10(
        int protocolVersion,
        String serverVersion,
        int connectionId,
        byte[] authPluginDataPart1,
        int capabilityFlagsLower,
        int characterSet,
        int statusFlags,
        int capabilityFlagsUpper,
        int authPluginDataLength,
        byte[] authPluginDataPart2,
        String authPluginName
) {

    /** Default protocol version. */
    public static final int PROTOCOL_VERSION = 10;

    /** Default server version string. */
    public static final String DEFAULT_SERVER_VERSION = "8.0.35-legoflow";

    /** Default charset: utf8mb4 general ci (45). */
    public static final int DEFAULT_CHARSET = 45;

    /**
     * Returns the full capability flags (lower + upper combined).
     *
     * @return 32-bit capability flags
     */
    public int capabilityFlags() {
        return capabilityFlagsLower | (capabilityFlagsUpper << 16);
    }

    /**
     * Returns the full auth plugin data (part1 + part2 concatenated).
     *
     * @return the complete scramble data
     */
    public byte[] authPluginData() {
        if (authPluginDataPart2 == null || authPluginDataPart2.length == 0) {
            return authPluginDataPart1;
        }
        var combined = new byte[authPluginDataPart1.length + authPluginDataPart2.length];
        System.arraycopy(authPluginDataPart1, 0, combined, 0, authPluginDataPart1.length);
        System.arraycopy(authPluginDataPart2, 0, combined, authPluginDataPart1.length,
                authPluginDataPart2.length);
        return combined;
    }

    /**
     * Decodes a HandshakeV10 from payload bytes.
     *
     * @param payload the packet payload
     * @return the decoded handshake
     */
    public static HandshakeV10 decode(byte[] payload) {
        var buf = ByteBuffer.wrap(payload);

        int protocolVersion = buf.get() & 0xFF;
        String serverVersion = LengthEncodedString.readNullTerminated(buf);
        int connectionId = (buf.get() & 0xFF)
                | ((buf.get() & 0xFF) << 8)
                | ((buf.get() & 0xFF) << 16)
                | ((buf.get() & 0xFF) << 24);

        var authPart1 = new byte[8];
        buf.get(authPart1);
        buf.get(); // filler

        int capLower = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);

        int characterSet = 0;
        int statusFlags = 0;
        int capUpper = 0;
        int authDataLength = 0;
        byte[] authPart2 = new byte[0];
        String authPluginName = "";

        if (buf.hasRemaining()) {
            characterSet = buf.get() & 0xFF;
            statusFlags = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
            capUpper = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);

            int capabilities = capLower | (capUpper << 16);

            if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
                authDataLength = buf.get() & 0xFF;
            } else {
                buf.get(); // skip 0x00
                authDataLength = 0;
            }

            // 10 reserved bytes
            for (int i = 0; i < 10; i++) {
                buf.get();
            }

            if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
                int part2Len = Math.max(13, authDataLength - 8);
                authPart2 = new byte[part2Len];
                buf.get(authPart2);
                // Remove trailing null byte if present
                if (authPart2[authPart2.length - 1] == 0) {
                    var trimmed = new byte[authPart2.length - 1];
                    System.arraycopy(authPart2, 0, trimmed, 0, trimmed.length);
                    authPart2 = trimmed;
                }
            }

            if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
                if (buf.hasRemaining()) {
                    authPluginName = LengthEncodedString.readNullTerminated(buf);
                }
            }
        }

        return new HandshakeV10(protocolVersion, serverVersion, connectionId,
                authPart1, capLower, characterSet, statusFlags, capUpper,
                authDataLength, authPart2, authPluginName);
    }

    /**
     * Encodes this HandshakeV10 as payload bytes.
     *
     * @return the encoded payload
     */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(1024);

        buf.put((byte) protocolVersion);
        LengthEncodedString.writeNullTerminated(buf, serverVersion);

        // connection id (4 bytes LE)
        buf.put((byte) (connectionId & 0xFF));
        buf.put((byte) ((connectionId >> 8) & 0xFF));
        buf.put((byte) ((connectionId >> 16) & 0xFF));
        buf.put((byte) ((connectionId >> 24) & 0xFF));

        buf.put(authPluginDataPart1);
        buf.put((byte) 0); // filler

        buf.put((byte) (capabilityFlagsLower & 0xFF));
        buf.put((byte) ((capabilityFlagsLower >> 8) & 0xFF));

        buf.put((byte) characterSet);

        buf.put((byte) (statusFlags & 0xFF));
        buf.put((byte) ((statusFlags >> 8) & 0xFF));

        buf.put((byte) (capabilityFlagsUpper & 0xFF));
        buf.put((byte) ((capabilityFlagsUpper >> 8) & 0xFF));

        int capabilities = capabilityFlags();
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
            buf.put((byte) authPluginDataLength);
        } else {
            buf.put((byte) 0);
        }

        // 10 reserved bytes
        for (int i = 0; i < 10; i++) {
            buf.put((byte) 0);
        }

        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
            buf.put(authPluginDataPart2);
            buf.put((byte) 0); // trailing null
        }

        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
            LengthEncodedString.writeNullTerminated(buf, authPluginName);
        }

        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Creates a default HandshakeV10 with random scramble data.
     *
     * @param connectionId the connection ID
     * @return a new handshake packet
     */
    public static HandshakeV10 create(int connectionId) {
        return create(connectionId, "mysql_native_password");
    }

    /**
     * Creates a HandshakeV10 with specified auth plugin and random scramble.
     *
     * @param connectionId the connection ID
     * @param authPlugin the auth plugin name
     * @return a new handshake packet
     */
    public static HandshakeV10 create(int connectionId, String authPlugin) {
        var random = new SecureRandom();
        var authPart1 = new byte[8];
        var authPart2 = new byte[12];
        random.nextBytes(authPart1);
        random.nextBytes(authPart2);

        int capabilities = CapabilityFlags.DEFAULT_SERVER_CAPABILITIES;

        return new HandshakeV10(
                PROTOCOL_VERSION,
                DEFAULT_SERVER_VERSION,
                connectionId,
                authPart1,
                capabilities & 0xFFFF,
                DEFAULT_CHARSET,
                StatusFlags.DEFAULT_STATUS,
                (capabilities >> 16) & 0xFFFF,
                20, // total auth data length
                authPart2,
                authPlugin
        );
    }
}
