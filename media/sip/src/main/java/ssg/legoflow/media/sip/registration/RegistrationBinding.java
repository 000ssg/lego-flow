package ssg.legoflow.media.sip.registration;

import java.time.Instant;
import java.util.Objects;

/**
 * A registration binding mapping an Address-of-Record (AOR) to a Contact URI.
 *
 * <p>Represents a single binding stored by a SIP registrar per RFC 3261 section 10.
 *
 * @param aor         the Address-of-Record (the public SIP URI)
 * @param contactUri  the contact URI (where to reach the user)
 * @param expires     the expiration time
 * @param callId      the Call-ID of the REGISTER request
 * @param cseq        the CSeq of the REGISTER request
 * @since 0.1.0
 */
public record RegistrationBinding(
        String aor,
        String contactUri,
        Instant expires,
        String callId,
        long cseq
) {

    /**
     * Creates a registration binding.
     *
     * @since 0.1.0
     */
    public RegistrationBinding {
        Objects.requireNonNull(aor, "aor");
        Objects.requireNonNull(contactUri, "contactUri");
        Objects.requireNonNull(expires, "expires");
        Objects.requireNonNull(callId, "callId");
    }

    /**
     * Returns true if this binding has expired.
     *
     * @return true if expired
     * @since 0.1.0
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expires);
    }

    /**
     * Returns the remaining time-to-live in seconds.
     *
     * @return the TTL in seconds, or 0 if expired
     * @since 0.1.0
     */
    public long ttlSeconds() {
        long remaining = expires.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}
