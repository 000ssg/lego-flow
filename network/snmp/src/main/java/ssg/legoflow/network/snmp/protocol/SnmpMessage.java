package ssg.legoflow.network.snmp.protocol;

import java.util.Arrays;

/**
 * SNMPv3 message wrapper as defined in RFC 3412.
 *
 * <p>An SNMPv3 message consists of a header (msgVersion, msgID, msgMaxSize,
 * msgFlags, msgSecurityModel), security parameters, and a scoped PDU.
 *
 * @param msgVersion       the SNMP version (3)
 * @param msgId            the message identifier
 * @param msgMaxSize       the maximum message size the sender can accept
 * @param msgFlags         the security flags byte
 * @param msgSecurityModel the security model (3 = USM)
 * @param securityParams   the USM security parameters (encoded)
 * @param scopedPdu        the scoped PDU
 * @since 0.1.0
 */
public record SnmpMessage(
        int msgVersion,
        int msgId,
        int msgMaxSize,
        int msgFlags,
        int msgSecurityModel,
        byte[] securityParams,
        ScopedPdu scopedPdu
) {
    /** SNMPv3 version number. */
    public static final int VERSION_3 = 3;

    /** SNMPv2c version number. */
    public static final int VERSION_2C = 1;

    /** SNMPv1 version number. */
    public static final int VERSION_1 = 0;

    /** USM security model number. */
    public static final int SECURITY_MODEL_USM = 3;

    /**
     * Creates an SnmpMessage with validation and defensive copy.
     *
     * @param msgVersion       the SNMP version
     * @param msgId            the message identifier
     * @param msgMaxSize       the maximum message size
     * @param msgFlags         the security flags byte
     * @param msgSecurityModel the security model
     * @param securityParams   the security parameters (must not be null)
     * @param scopedPdu        the scoped PDU (must not be null)
     */
    public SnmpMessage {
        if (securityParams == null) {
            throw new IllegalArgumentException("Security parameters must not be null");
        }
        if (scopedPdu == null) {
            throw new IllegalArgumentException("Scoped PDU must not be null");
        }
        securityParams = securityParams.clone();
    }

    /**
     * Returns a copy of the security parameters.
     *
     * @return copy of the security parameters
     */
    @Override
    public byte[] securityParams() {
        return securityParams.clone();
    }

    /**
     * Returns the security level derived from msgFlags.
     *
     * @return the security level
     */
    public SecurityLevel securityLevel() {
        return SecurityLevel.fromFlags(msgFlags);
    }

    /**
     * Returns whether this message is reportable (msgFlags bit 2).
     *
     * @return true if the message is reportable
     */
    public boolean isReportable() {
        return (msgFlags & 0x04) != 0;
    }

    /**
     * Creates a new SNMPv3 message builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SnmpMessage other
                && msgVersion == other.msgVersion
                && msgId == other.msgId
                && msgMaxSize == other.msgMaxSize
                && msgFlags == other.msgFlags
                && msgSecurityModel == other.msgSecurityModel
                && Arrays.equals(securityParams, other.securityParams)
                && scopedPdu.equals(other.scopedPdu);
    }

    @Override
    public int hashCode() {
        int h = 31 * msgVersion + msgId;
        h = 31 * h + msgMaxSize;
        h = 31 * h + msgFlags;
        h = 31 * h + msgSecurityModel;
        h = 31 * h + Arrays.hashCode(securityParams);
        h = 31 * h + scopedPdu.hashCode();
        return h;
    }

    /**
     * Builder for constructing SNMPv3 messages.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private int msgVersion = VERSION_3;
        private int msgId;
        private int msgMaxSize = 65507;
        private int msgFlags;
        private int msgSecurityModel = SECURITY_MODEL_USM;
        private byte[] securityParams = new byte[0];
        private ScopedPdu scopedPdu;

        private Builder() {}

        /** Sets the message version. */
        public Builder msgVersion(int msgVersion) { this.msgVersion = msgVersion; return this; }
        /** Sets the message ID. */
        public Builder msgId(int msgId) { this.msgId = msgId; return this; }
        /** Sets the maximum message size. */
        public Builder msgMaxSize(int msgMaxSize) { this.msgMaxSize = msgMaxSize; return this; }
        /** Sets the message flags. */
        public Builder msgFlags(int msgFlags) { this.msgFlags = msgFlags; return this; }
        /** Sets the security model. */
        public Builder msgSecurityModel(int msgSecurityModel) { this.msgSecurityModel = msgSecurityModel; return this; }
        /** Sets the security parameters. */
        public Builder securityParams(byte[] securityParams) { this.securityParams = securityParams; return this; }
        /** Sets the scoped PDU. */
        public Builder scopedPdu(ScopedPdu scopedPdu) { this.scopedPdu = scopedPdu; return this; }

        /**
         * Sets the security level (updates msgFlags accordingly).
         *
         * @param level the security level
         * @return this builder
         */
        public Builder securityLevel(SecurityLevel level) {
            this.msgFlags = (this.msgFlags & ~0x03) | level.flags();
            return this;
        }

        /**
         * Sets the reportable flag (msgFlags bit 2).
         *
         * @param reportable whether the message is reportable
         * @return this builder
         */
        public Builder reportable(boolean reportable) {
            if (reportable) {
                this.msgFlags |= 0x04;
            } else {
                this.msgFlags &= ~0x04;
            }
            return this;
        }

        /**
         * Builds the SnmpMessage.
         *
         * @return the constructed message
         */
        public SnmpMessage build() {
            if (scopedPdu == null) {
                throw new IllegalStateException("Scoped PDU must be set");
            }
            return new SnmpMessage(msgVersion, msgId, msgMaxSize, msgFlags,
                    msgSecurityModel, securityParams, scopedPdu);
        }
    }
}
