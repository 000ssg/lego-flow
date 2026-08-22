package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.RData;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
/**
 * NSEC3 record RDATA: hashed denial of existence (RFC 5155).
 *
 * @param hashAlgorithm   the hash algorithm (1 = SHA-1)
 * @param flags           flags (bit 0 = opt-out)
 * @param iterations      additional hash iterations
 * @param salt            the salt bytes
 * @param nextHashedOwner the next hashed owner name
 * @param types           the set of record types at the original owner
 * @since 0.1.0
 */
public record Nsec3Record(
        int hashAlgorithm,
        int flags,
        int iterations,
        byte[] salt,
        byte[] nextHashedOwner,
        Set<RecordType> types
) implements RData {

    /** SHA-1 hash algorithm. */
    public static final int HASH_SHA1 = 1;
    /** Opt-out flag. */
    public static final int FLAG_OPT_OUT = 1;

    public Nsec3Record {
        salt = salt.clone();
        nextHashedOwner = nextHashedOwner.clone();
        types = Collections.unmodifiableSet(new TreeSet<>(types));
    }

    @Override
    public RecordType type() {
        return RecordType.NSEC3;
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] nextHashedOwner() {
        return nextHashedOwner.clone();
    }

    /**
     * Returns whether the opt-out flag is set.
     *
     * @return {@code true} if opt-out
     * @since 0.1.0
     */
    public boolean isOptOut() {
        return (flags & FLAG_OPT_OUT) != 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Nsec3Record other)) return false;
        return hashAlgorithm == other.hashAlgorithm
                && flags == other.flags
                && iterations == other.iterations
                && Arrays.equals(salt, other.salt)
                && Arrays.equals(nextHashedOwner, other.nextHashedOwner)
                && types.equals(other.types);
    }

    @Override
    public int hashCode() {
        int h = Objects.hash(hashAlgorithm, flags, iterations, types);
        h = 31 * h + Arrays.hashCode(salt);
        return 31 * h + Arrays.hashCode(nextHashedOwner);
    }
}
