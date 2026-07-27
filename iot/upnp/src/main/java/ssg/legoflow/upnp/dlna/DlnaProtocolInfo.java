package ssg.legoflow.upnp.dlna;

import java.util.Objects;

/**
 * Represents DLNA protocol info as used in ConnectionManager service descriptions.
 *
 * <p>The protocol info string follows the format:
 * {@code <protocol>:<network>:<contentFormat>:<additionalInfo>}
 * For example: {@code "http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"}
 *
 * @param protocol       the transport protocol, typically "http-get"
 * @param network        the network segment, typically "*"
 * @param contentFormat  the MIME type of the content
 * @param additionalInfo DLNA-specific info such as DLNA.ORG_PN, DLNA.ORG_FLAGS, DLNA.ORG_OP
 * @since 1.0.0
 */
public record DlnaProtocolInfo(
        String protocol,
        String network,
        String contentFormat,
        String additionalInfo
) {

    /**
     * Creates a new protocol info record with validation.
     *
     * @param protocol       the transport protocol
     * @param network        the network segment
     * @param contentFormat  the MIME content type
     * @param additionalInfo additional DLNA info
     * @since 1.0.0
     */
    public DlnaProtocolInfo {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(contentFormat, "contentFormat must not be null");
        Objects.requireNonNull(additionalInfo, "additionalInfo must not be null");
    }

    /**
     * Parses a protocol info string in the format {@code protocol:network:contentFormat:additionalInfo}.
     *
     * @param protocolInfoString the protocol info string to parse
     * @return a new {@link DlnaProtocolInfo} instance
     * @throws IllegalArgumentException if the string does not contain exactly four colon-separated parts
     * @since 1.0.0
     */
    public static DlnaProtocolInfo parse(String protocolInfoString) {
        Objects.requireNonNull(protocolInfoString, "protocolInfoString must not be null");
        String[] parts = protocolInfoString.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "Invalid protocol info string, expected 4 colon-separated parts: " + protocolInfoString);
        }
        return new DlnaProtocolInfo(parts[0], parts[1], parts[2], parts[3]);
    }

    /**
     * Creates a protocol info for HTTP GET with the given MIME type and DLNA profile name.
     *
     * @param mimeType    the MIME content type
     * @param dlnaProfile the DLNA.ORG_PN profile name (e.g. "MP3", "JPEG_SM")
     * @return a new {@link DlnaProtocolInfo} instance
     * @since 1.0.0
     */
    public static DlnaProtocolInfo httpGet(String mimeType, String dlnaProfile) {
        return new DlnaProtocolInfo("http-get", "*", mimeType, "DLNA.ORG_PN=" + dlnaProfile);
    }

    /**
     * Creates a protocol info for HTTP GET with the given MIME type, DLNA profile, and flags.
     *
     * @param mimeType    the MIME content type
     * @param dlnaProfile the DLNA.ORG_PN profile name
     * @param flags       the DLNA.ORG_FLAGS value
     * @return a new {@link DlnaProtocolInfo} instance
     * @since 1.0.0
     */
    public static DlnaProtocolInfo httpGetWithFlags(String mimeType, String dlnaProfile, String flags) {
        return new DlnaProtocolInfo("http-get", "*", mimeType,
                "DLNA.ORG_PN=" + dlnaProfile + ";DLNA.ORG_FLAGS=" + flags);
    }

    /**
     * Creates a protocol info for HTTP GET with streaming flags enabled.
     *
     * <p>The streaming flags include {@code DLNA.ORG_OP=01} (byte-based seek)
     * and standard streaming {@code DLNA.ORG_FLAGS}.
     *
     * @param mimeType    the MIME content type
     * @param dlnaProfile the DLNA.ORG_PN profile name
     * @return a new {@link DlnaProtocolInfo} instance configured for streaming
     * @since 1.0.0
     */
    public static DlnaProtocolInfo httpGetStreaming(String mimeType, String dlnaProfile) {
        return new DlnaProtocolInfo("http-get", "*", mimeType,
                "DLNA.ORG_PN=" + dlnaProfile
                        + ";DLNA.ORG_OP=01"
                        + ";DLNA.ORG_FLAGS=01700000000000000000000000000000");
    }

    /**
     * Creates a simple protocol info for HTTP GET with only the MIME type and
     * a wildcard for additional info.
     *
     * @param mimeType the MIME content type
     * @return a new {@link DlnaProtocolInfo} instance with wildcard additional info
     * @since 1.0.0
     */
    public static DlnaProtocolInfo httpGetSimple(String mimeType) {
        return new DlnaProtocolInfo("http-get", "*", mimeType, "*");
    }

    /**
     * Returns whether this protocol info supports byte-based seeking.
     *
     * <p>Byte seek is indicated by {@code DLNA.ORG_OP=01} or {@code DLNA.ORG_OP=11}.
     *
     * @return {@code true} if byte seeking is supported
     * @since 1.0.0
     */
    public boolean supportsByteSeek() {
        return additionalInfo.contains("DLNA.ORG_OP=01")
                || additionalInfo.contains("DLNA.ORG_OP=11");
    }

    /**
     * Returns whether this protocol info supports time-based seeking.
     *
     * <p>Time seek is indicated by {@code DLNA.ORG_OP=10} or {@code DLNA.ORG_OP=11}.
     *
     * @return {@code true} if time seeking is supported
     * @since 1.0.0
     */
    public boolean supportsTimeSeek() {
        return additionalInfo.contains("DLNA.ORG_OP=10")
                || additionalInfo.contains("DLNA.ORG_OP=11");
    }

    /**
     * Checks whether this protocol info is compatible with another, meaning
     * the protocols and content formats match.
     *
     * @param other the other protocol info to check
     * @return true if compatible
     * @since 1.0.0
     */
    public boolean isCompatibleWith(DlnaProtocolInfo other) {
        if (other == null) {
            return false;
        }
        return protocol.equals(other.protocol)
                && ("*".equals(network) || "*".equals(other.network) || network.equals(other.network))
                && contentFormat.equals(other.contentFormat);
    }

    /**
     * Serializes this protocol info to the standard colon-separated string format.
     *
     * @return the protocol info string
     * @since 1.0.0
     */
    @Override
    public String toString() {
        return protocol + ":" + network + ":" + contentFormat + ":" + additionalInfo;
    }
}
