package ssg.legoflow.ssh.transport;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SSH version string exchange per RFC 4253 section 4.2.
 *
 * <p>The version string format is: {@code SSH-protoversion-softwareversion SP comments CR LF}.
 * This implementation identifies as {@code SSH-2.0-legoflow_1.0}.
 *
 * @since 1.0.0
 */
public final class SshVersion {

    /** The SSH protocol version supported by this implementation. */
    public static final String PROTOCOL_VERSION = "2.0";

    /** The software version string identifying this implementation. */
    public static final String SOFTWARE_VERSION = "legoflow_1.0";

    /** The full version string sent during version exchange. */
    public static final String VERSION_STRING = "SSH-" + PROTOCOL_VERSION + "-" + SOFTWARE_VERSION;

    /** Maximum allowed length for a version string per RFC 4253 (255 bytes including CR LF). */
    public static final int MAX_VERSION_LENGTH = 255;

    private final String protocolVersion;
    private final String softwareVersion;
    private final String comments;

    /**
     * Creates a new SSH version with the given components.
     *
     * @param protocolVersion the SSH protocol version (e.g., "2.0")
     * @param softwareVersion the software version identifier
     * @param comments        optional comments (may be null)
     * @throws NullPointerException if protocolVersion or softwareVersion is null
     */
    public SshVersion(String protocolVersion, String softwareVersion, String comments) {
        this.protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
        this.softwareVersion = Objects.requireNonNull(softwareVersion, "softwareVersion");
        this.comments = comments;
    }

    /**
     * Parses a version string received from a remote peer.
     *
     * @param versionString the raw version string (without trailing CR LF)
     * @return the parsed SSH version
     * @throws IllegalArgumentException if the string is malformed
     */
    public static SshVersion parse(String versionString) {
        if (versionString == null || !versionString.startsWith("SSH-")) {
            throw new IllegalArgumentException("Invalid SSH version string: " + versionString);
        }
        String remainder = versionString.substring(4);
        int firstDash = remainder.indexOf('-');
        if (firstDash < 0) {
            throw new IllegalArgumentException("Missing software version in: " + versionString);
        }
        String protoVer = remainder.substring(0, firstDash);
        String rest = remainder.substring(firstDash + 1);

        String softVer;
        String cmts = null;
        int spaceIdx = rest.indexOf(' ');
        if (spaceIdx >= 0) {
            softVer = rest.substring(0, spaceIdx);
            cmts = rest.substring(spaceIdx + 1);
        } else {
            softVer = rest;
        }

        return new SshVersion(protoVer, softVer, cmts);
    }

    /**
     * Formats this version as a version string suitable for transmission.
     *
     * @return the formatted version string (without trailing CR LF)
     */
    public String format() {
        StringBuilder sb = new StringBuilder("SSH-")
                .append(protocolVersion).append('-').append(softwareVersion);
        if (comments != null && !comments.isEmpty()) {
            sb.append(' ').append(comments);
        }
        return sb.toString();
    }

    /**
     * Formats this version string as bytes for transmission (with CR LF terminator).
     *
     * @return the version string bytes including CR LF
     */
    public byte[] toBytes() {
        return (format() + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Checks whether this version is compatible with SSH-2.0.
     *
     * @return true if the protocol version is "2.0" or "1.99"
     */
    public boolean isCompatible() {
        return "2.0".equals(protocolVersion) || "1.99".equals(protocolVersion);
    }

    /**
     * Returns the SSH protocol version.
     *
     * @return the protocol version string (e.g., "2.0")
     */
    public String protocolVersion() {
        return protocolVersion;
    }

    /**
     * Returns the software version identifier.
     *
     * @return the software version string
     */
    public String softwareVersion() {
        return softwareVersion;
    }

    /**
     * Returns the optional comments.
     *
     * @return comments, or null if none
     */
    public String comments() {
        return comments;
    }

    /**
     * Creates the default version for this implementation.
     *
     * @return the default SshVersion
     */
    public static SshVersion defaultVersion() {
        return new SshVersion(PROTOCOL_VERSION, SOFTWARE_VERSION, null);
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SshVersion that)) return false;
        return protocolVersion.equals(that.protocolVersion)
                && softwareVersion.equals(that.softwareVersion)
                && Objects.equals(comments, that.comments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolVersion, softwareVersion, comments);
    }
}
