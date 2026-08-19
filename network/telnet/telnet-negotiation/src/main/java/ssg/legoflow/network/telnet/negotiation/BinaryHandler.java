package ssg.legoflow.network.telnet.negotiation;

import java.util.ArrayList;
import java.util.List;
/**
 * Handler for BINARY option (RFC 856).
 *
 * <p>Binary transmission disables byte translation between the local
 * character set and the network standard. When enabled, all 8 bits
 * of data are transmitted without modification.
 *
 * <p>This handler tracks local and remote binary mode and performs
 * actual byte-level translation when binary mode is off:
 * <ul>
 *   <li><b>Inbound (peer → local)</b>: CR NL → LF, CR NUL → CR, CR (alone) → CR NL</li>
 *   <li><b>Outbound (local → peer)</b>: LF → CR NL, CR NUL → CR, CR NL → CR NL</li>
 * </ul>
 *
 * <p>Translation uses a one-byte lookahead buffer to handle CR correctly.
 *
 * @since 0.2.0
 */
public class BinaryHandler {

    /** Whether the local side sends binary (WILL BINARY). */
    private boolean localBinary;
    /** Whether the remote side sends binary (DO BINARY). */
    private boolean remoteBinary;
    /** Lookahead byte for inbound translation (-1 if empty). */
    private int crLookahead;

    private BinaryHandler() {
        this.localBinary = false;
        this.remoteBinary = false;
        this.crLookahead = -1;
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

    /**
     * Check if inbound translation is needed (remote is NOT sending binary).
     */
    public boolean needsInboundTranslation() {
        return !remoteBinary;
    }

    /**
     * Check if outbound translation is needed (local is NOT sending binary).
     */
    public boolean needsOutboundTranslation() {
        return !localBinary;
    }

    /**
     * Translate inbound bytes from peer (network → local).
     *
     * <p>When binary mode is OFF on the receiving side:
     * <ul>
     *   <li>CR NL → LF</li>
     *   <li>CR NUL → CR (preserve the CR)</li>
     *   <li>CR (standalone) → CR NL (default to line ending)</li>
     * </ul>
     *
     * <p>Uses a one-byte lookahead to disambiguate CR.
     *
     * @param bytes the raw bytes from the peer
     * @return translated bytes, or the original bytes if binary mode is on
     */
    public byte[] translateInbound(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return bytes;
        if (remoteBinary) return bytes; // No translation in binary mode

        // If we have a CR lookahead from the previous batch
        int startOffset = 0;
        List<Byte> result = new ArrayList<>();

        if (crLookahead == 13) { // Previous batch ended with CR
            // We already consumed the CR; check the first byte of this batch
            if (bytes.length > 0 && bytes[0] == 10) { // CR NL → LF
                result.add((byte) 10);
                startOffset = 1;
            } else if (bytes.length > 0 && bytes[0] == 0) { // CR NUL → CR
                result.add((byte) 13);
                startOffset = 1;
            } else {
                // CR (standalone) → CR NL
                result.add((byte) 13);
                result.add((byte) 10);
            }
            crLookahead = -1;
        }

        // Process remaining bytes
        for (int i = startOffset; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;

            if (b == 13 && i + 1 < bytes.length) { // CR with next byte available
                int next = bytes[i + 1] & 0xFF;
                if (next == 10) { // CR NL → LF
                    result.add((byte) 10);
                    i++; // Skip NL
                } else if (next == 0) { // CR NUL → CR
                    result.add((byte) 13);
                } else {
                    // CR followed by something else — keep as CR, use lookahead
                    result.add((byte) 13);
                }
            } else if (b == 13) {
                // CR at end of buffer — use lookahead
                crLookahead = 13;
            } else {
                result.add(bytes[i]);
            }
        }

        return toByteArray(result);
    }

    /**
     * Translate outbound bytes to peer (local → network).
     *
     * <p>When binary mode is OFF on the sending side:
     * <ul>
     *   <li>LF → CR NL</li>
     *   <li>CR NUL → CR</li>
     *   <li>CR NL → CR NL (preserve)</li>
     *   <li>CR (standalone) → CR NL</li>
     * </ul>
     *
     * @param bytes the local bytes
     * @return translated bytes, or the original bytes if binary mode is on
     */
    public byte[] translateOutbound(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return bytes;
        if (localBinary) return bytes; // No translation in binary mode

        List<Byte> result = new ArrayList<>();

        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;

            if (b == 13) { // CR
                if (i + 1 < bytes.length) {
                    int next = bytes[i + 1] & 0xFF;
                    if (next == 10) { // CR NL → CR NL (preserve)
                        result.add((byte) 13);
                        result.add((byte) 10);
                        i++; // Skip NL
                    } else if (next == 0) { // CR NUL → CR
                        result.add((byte) 13);
                    } else {
                        // CR followed by something else → CR NL
                        result.add((byte) 13);
                        result.add((byte) 10);
                    }
                } else {
                    // CR at end → CR NL
                    result.add((byte) 13);
                    result.add((byte) 10);
                }
            } else if (b == 10) { // LF → CR NL
                result.add((byte) 13);
                result.add((byte) 10);
            } else {
                result.add(bytes[i]);
            }
        }

        return toByteArray(result);
    }

    /**
     * Flush any buffered CR lookahead (end of stream).
     * Converts pending CR to CR NL.
     *
     * @return buffered bytes, or empty array if none
     */
    public byte[] flushInbound() {
        if (crLookahead == 13) {
            crLookahead = -1;
            return new byte[]{13, 10};
        }
        crLookahead = -1;
        return new byte[0];
    }

    private static byte[] toByteArray(List<Byte> list) {
        byte[] bytes = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            bytes[i] = list.get(i);
        }
        return bytes;
    }
}
