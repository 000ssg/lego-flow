package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.RData;
import java.util.Arrays;
import java.util.Objects;
/**
 * NSEC3PARAM record RDATA: NSEC3 parameters (RFC 5155).
 *
 * @param hashAlgorithm the hash algorithm (1 = SHA-1)
 * @param flags         flags (must be 0)
 * @param iterations    additional hash iterations
 * @param salt          the salt bytes
 * @since 0.1.0
 */
public record Nsec3ParamRecord(int hashAlgorithm, int flags, int iterations, byte[] salt)
        implements RData {

    public Nsec3ParamRecord {
        salt = salt.clone();
    }

    @Override
    public RecordType type() {
        return RecordType.NSEC3PARAM;
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Nsec3ParamRecord other)) return false;
        return hashAlgorithm == other.hashAlgorithm
                && flags == other.flags
                && iterations == other.iterations
                && Arrays.equals(salt, other.salt);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(hashAlgorithm, flags, iterations);
        return 31 * h + Arrays.hashCode(salt);
    }
}
