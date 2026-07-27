package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.RData;

import java.util.Arrays;
import java.util.Objects;

/**
 * DS record RDATA: delegation signer (RFC 4034).
 *
 * @param keyTag     the key tag of the referenced DNSKEY
 * @param algorithm  the algorithm of the referenced DNSKEY
 * @param digestType the digest algorithm (1 = SHA-1, 2 = SHA-256)
 * @param digest     the digest bytes
 * @since 1.0.0
 */
public record DsRecord(int keyTag, int algorithm, int digestType, byte[] digest)
        implements RData {

    /** SHA-1 digest type. */
    public static final int DIGEST_SHA1 = 1;
    /** SHA-256 digest type. */
    public static final int DIGEST_SHA256 = 2;

    public DsRecord {
        digest = digest.clone();
    }

    @Override
    public RecordType type() {
        return RecordType.DS;
    }

    @Override
    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DsRecord other)) return false;
        return keyTag == other.keyTag && algorithm == other.algorithm
                && digestType == other.digestType
                && Arrays.equals(digest, other.digest);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(keyTag, algorithm, digestType);
        return 31 * h + Arrays.hashCode(digest);
    }
}
