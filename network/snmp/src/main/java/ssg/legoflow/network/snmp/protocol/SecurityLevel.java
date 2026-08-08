package ssg.legoflow.network.snmp.protocol;

/**
 * SNMPv3 security levels as defined in RFC 3411.
 *
 * <p>The security level determines what security services (authentication,
 * privacy) are applied to an SNMP message.
 *
 * @since 0.1.0
 */
public enum SecurityLevel {

    /**
     * No authentication, no privacy (noAuthNoPriv).
     * msgFlags bit 0 = 0, bit 1 = 0.
     */
    NO_AUTH_NO_PRIV(0x00),

    /**
     * Authentication, no privacy (authNoPriv).
     * msgFlags bit 0 = 1, bit 1 = 0.
     */
    AUTH_NO_PRIV(0x01),

    /**
     * Authentication and privacy (authPriv).
     * msgFlags bit 0 = 1, bit 1 = 1.
     */
    AUTH_PRIV(0x03);

    private final int flags;

    SecurityLevel(int flags) {
        this.flags = flags;
    }

    /**
     * Returns the msgFlags bits for this security level.
     *
     * @return the flags byte (bits 0-1)
     */
    public int flags() {
        return flags;
    }

    /**
     * Returns whether authentication is required.
     *
     * @return true if authentication is used
     */
    public boolean isAuthenticated() {
        return (flags & 0x01) != 0;
    }

    /**
     * Returns whether privacy (encryption) is required.
     *
     * @return true if privacy is used
     */
    public boolean isPrivate() {
        return (flags & 0x02) != 0;
    }

    /**
     * Returns the security level for the given msgFlags byte.
     *
     * @param flags the msgFlags byte
     * @return the security level
     * @throws IllegalArgumentException if the flags are invalid
     */
    public static SecurityLevel fromFlags(int flags) {
        int level = flags & 0x03;
        return switch (level) {
            case 0x00 -> NO_AUTH_NO_PRIV;
            case 0x01 -> AUTH_NO_PRIV;
            case 0x03 -> AUTH_PRIV;
            default -> throw new IllegalArgumentException(
                    "Invalid security level flags: 0x%02X (privacy without authentication)".formatted(flags));
        };
    }
}
