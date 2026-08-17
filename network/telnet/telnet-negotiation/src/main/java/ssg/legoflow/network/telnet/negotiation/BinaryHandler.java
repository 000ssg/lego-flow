package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetOption;

/**
 * Handler for BINARY option (RFC 856).
 *
 * <p>Binary transmission disables byte translation between the local
 * character set and the network standard. When enabled, all 8 bits
 * of data are transmitted without modification.
 *
 * <p>This is a stateful handler that tracks local and remote binary mode.
 *
 * <p>Known limitations:
 * <ul>
 *   <li>No actual byte translation is performed — the parser handles raw bytes</li>
 *   <li>State tracking only (WILL/DO negotiation)</li>
 *   <li>No CR/NL → LF translation when binary is off</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class BinaryHandler {

    /** Whether the local side sends binary (WILL BINARY). */
    private boolean localBinary;
    /** Whether the remote side sends binary (DO BINARY). */
    private boolean remoteBinary;

    private BinaryHandler() {
        this.localBinary = false;
        this.remoteBinary = false;
    }

    /** Create a new BinaryHandler. */
    public static BinaryHandler create() {
        return new BinaryHandler();
    }

    /**
     * Check if the local side sends binary data.
     */
    public boolean isLocalBinary() {
        return localBinary;
    }

    /**
     * Check if the remote side sends binary data.
     */
    public boolean isRemoteBinary() {
        return remoteBinary;
    }

    /**
     * Set local binary mode (responding to DO BINARY from peer).
     *
     * @param enabled whether to enable
     */
    public void setLocalBinary(boolean enabled) {
        this.localBinary = enabled;
    }

    /**
     * Set remote binary mode (after receiving WILL BINARY from peer).
     *
     * @param enabled whether to enable
     */
    public void setRemoteBinary(boolean enabled) {
        this.remoteBinary = enabled;
    }

    /**
     * Check if both sides agreed on binary mode.
     */
    public boolean isNegotiated() {
        return localBinary && remoteBinary;
    }
}
