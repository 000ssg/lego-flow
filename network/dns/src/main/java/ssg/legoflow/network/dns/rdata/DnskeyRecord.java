package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.RData;

import java.util.Arrays;
import java.util.Objects;

/**
 * DNSKEY record RDATA: DNS public key (RFC 4034).
 *
 * @param flags     key flags (bit 7 = zone key, bit 15 = SEP)
 * @param protocol  must be 3
 * @param algorithm the algorithm number
 * @param publicKey the public key bytes
 * @since 0.1.0
 */
public record DnskeyRecord(int flags, int protocol, int algorithm, byte[] publicKey)
        implements RData {

    /** Zone key flag (bit 7). */
    public static final int FLAG_ZONE_KEY = 256;
    /** Secure entry point flag (bit 15). */
    public static final int FLAG_SEP = 1;

    /** RSA/SHA-256 algorithm (RFC 5702). */
    public static final int ALGORITHM_RSA_SHA256 = 8;
    /** ECDSA P-256 with SHA-256 (RFC 6605). */
    public static final int ALGORITHM_ECDSA_P256_SHA256 = 13;

    public DnskeyRecord {
        publicKey = publicKey.clone();
    }

    @Override
    public RecordType type() {
        return RecordType.DNSKEY;
    }

    @Override
    public byte[] publicKey() {
        return publicKey.clone();
    }

    /**
     * Returns whether this is a zone signing key.
     *
     * @return {@code true} if the zone key flag is set
     * @since 0.1.0
     */
    public boolean isZoneKey() {
        return (flags & FLAG_ZONE_KEY) != 0;
    }

    /**
     * Returns whether this is a secure entry point (KSK).
     *
     * @return {@code true} if the SEP flag is set
     * @since 0.1.0
     */
    public boolean isSecureEntryPoint() {
        return (flags & FLAG_SEP) != 0;
    }

    /**
     * Computes the key tag per RFC 4034 Appendix B.
     *
     * @return the 16-bit key tag
     * @since 0.1.0
     */
    public int keyTag() {
        // Encode the RDATA: flags(2) + protocol(1) + algorithm(1) + publicKey
        byte[] rdata = new byte[4 + publicKey.length];
        rdata[0] = (byte) (flags >> 8);
        rdata[1] = (byte) (flags & 0xFF);
        rdata[2] = (byte) protocol;
        rdata[3] = (byte) algorithm;
        System.arraycopy(publicKey, 0, rdata, 4, publicKey.length);

        long ac = 0;
        for (int i = 0; i < rdata.length; i++) {
            if ((i & 1) == 0) {
                ac += (rdata[i] & 0xFF) << 8;
            } else {
                ac += rdata[i] & 0xFF;
            }
        }
        ac += (ac >> 16) & 0xFFFF;
        return (int) (ac & 0xFFFF);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DnskeyRecord other)) return false;
        return flags == other.flags && protocol == other.protocol
                && algorithm == other.algorithm
                && Arrays.equals(publicKey, other.publicKey);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(flags, protocol, algorithm);
        return 31 * h + Arrays.hashCode(publicKey);
    }
}
