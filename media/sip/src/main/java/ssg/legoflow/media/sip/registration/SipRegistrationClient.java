package ssg.legoflow.media.sip.registration;

import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
/**
 * SIP registration client per RFC 3261 section 10.2.
 *
 * <p>Builds REGISTER requests for initial registration, refresh, and
 * unregistration. Tracks Call-ID and CSeq for the registration sequence.
 *
 * @since 0.1.0
 */
public final class SipRegistrationClient {

    private final String registrarUri;
    private final String aor;
    private final String contactUri;
    private final String callId;
    private final AtomicLong cseq;

    /**
     * Creates a registration client.
     *
     * @param registrarUri the registrar URI (e.g., "sip:registrar.example.com")
     * @param aor          the Address-of-Record to register
     * @param contactUri   the Contact URI (where to reach this UA)
     * @since 0.1.0
     */
    public SipRegistrationClient(String registrarUri, String aor, String contactUri) {
        this.registrarUri = Objects.requireNonNull(registrarUri, "registrarUri");
        this.aor = Objects.requireNonNull(aor, "aor");
        this.contactUri = Objects.requireNonNull(contactUri, "contactUri");
        this.callId = UUID.randomUUID().toString();
        this.cseq = new AtomicLong(0);
    }

    /**
     * Builds a REGISTER request for initial registration.
     *
     * @param expires the registration expiration in seconds
     * @return the REGISTER request
     * @since 0.1.0
     */
    public SipRequest register(int expires) {
        return SipRequest.builder(SipMethod.REGISTER, registrarUri)
                .from("<" + aor + ">;tag=" + generateTag())
                .to("<" + aor + ">")
                .callId(callId)
                .cseq(cseq.incrementAndGet(), SipMethod.REGISTER)
                .maxForwards(70)
                .contact("<" + contactUri + ">")
                .expires(expires)
                .userAgent("LegoFlow-SIP/1.0")
                .build();
    }

    /**
     * Builds a REGISTER request for unregistration (expires=0).
     *
     * @return the unregister request
     * @since 0.1.0
     */
    public SipRequest unregister() {
        return register(0);
    }

    /**
     * Returns the Call-ID used for this registration sequence.
     *
     * @return the Call-ID
     * @since 0.1.0
     */
    public String callId() {
        return callId;
    }

    /**
     * Returns the current CSeq value.
     *
     * @return the CSeq
     * @since 0.1.0
     */
    public long currentCSeq() {
        return cseq.get();
    }

    private String generateTag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
