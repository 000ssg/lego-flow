package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.RData;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * RRSIG record RDATA: DNSSEC signature (RFC 4034).
 *
 * @param typeCovered   the record type covered by this signature
 * @param algorithm     the cryptographic algorithm
 * @param labels        the number of labels in the original owner name
 * @param originalTtl   the original TTL
 * @param expiration    signature expiration time
 * @param inception     signature inception time
 * @param keyTag        the key tag of the signing DNSKEY
 * @param signerName    the signer's domain name
 * @param signature     the cryptographic signature
 * @since 1.0.0
 */
public record RrsigRecord(
        RecordType typeCovered,
        int algorithm,
        int labels,
        long originalTtl,
        Instant expiration,
        Instant inception,
        int keyTag,
        DnsName signerName,
        byte[] signature
) implements RData {

    public RrsigRecord {
        Objects.requireNonNull(typeCovered, "typeCovered must not be null");
        Objects.requireNonNull(expiration, "expiration must not be null");
        Objects.requireNonNull(inception, "inception must not be null");
        Objects.requireNonNull(signerName, "signerName must not be null");
        signature = signature.clone();
    }

    @Override
    public RecordType type() {
        return RecordType.RRSIG;
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    /**
     * Returns whether the signature is currently valid (not expired and past inception).
     *
     * @return {@code true} if the signature is temporally valid
     * @since 1.0.0
     */
    public boolean isTemporallyValid() {
        Instant now = Instant.now();
        return !now.isBefore(inception) && now.isBefore(expiration);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RrsigRecord other)) return false;
        return typeCovered == other.typeCovered
                && algorithm == other.algorithm
                && labels == other.labels
                && originalTtl == other.originalTtl
                && expiration.equals(other.expiration)
                && inception.equals(other.inception)
                && keyTag == other.keyTag
                && signerName.equals(other.signerName)
                && Arrays.equals(signature, other.signature);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(typeCovered, algorithm, labels, originalTtl,
                expiration, inception, keyTag, signerName);
        return 31 * h + Arrays.hashCode(signature);
    }
}
