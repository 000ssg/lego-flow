package ssg.legoflow.media.sip.registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.sip.header.AddressHeader;
import ssg.legoflow.media.sip.header.SipHeaders;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;
import ssg.legoflow.media.sip.protocol.SipStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * SIP registrar server per RFC 3261 section 10.3.
 *
 * <p>Maintains Contact bindings for Addresses-of-Record (AORs).
 * Supports registration, re-registration, unregistration (expires=0),
 * and binding queries.
 *
 * @since 0.1.0
 */
public final class SipRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(SipRegistrar.class);

    /** Default registration expiration in seconds. */
    public static final int DEFAULT_EXPIRES = 3600;

    /** Minimum registration expiration in seconds. */
    public static final int MIN_EXPIRES = 60;

    private final Map<String, List<RegistrationBinding>> bindings;
    private final String domain;

    /**
     * Creates a registrar for the given domain.
     *
     * @param domain the registrar domain
     * @since 0.1.0
     */
    public SipRegistrar(String domain) {
        this.domain = domain;
        this.bindings = new ConcurrentHashMap<>();
    }

    /**
     * Handles a REGISTER request.
     *
     * @param request the REGISTER request
     * @return the response
     * @since 0.1.0
     */
    public SipResponse handleRegister(SipRequest request) {
        if (request.method() != SipMethod.REGISTER) {
            return SipResponse.builder(SipStatus.METHOD_NOT_ALLOWED)
                    .fromRequest(request)
                    .build();
        }

        SipHeaders headers = request.headers();
        AddressHeader to = headers.to();
        String aor = to.uri().format();

        // Get requested expiration
        int expires = headers.expires().orElse(DEFAULT_EXPIRES);
        if (expires > 0 && expires < MIN_EXPIRES) {
            return SipResponse.builder(SipStatus.INTERVAL_TOO_BRIEF)
                    .fromRequest(request)
                    .header(SipHeaders.MIN_EXPIRES, String.valueOf(MIN_EXPIRES))
                    .build();
        }

        // Get Contact headers
        List<String> contacts = headers.all(SipHeaders.CONTACT);

        if (contacts.isEmpty()) {
            // Query: return current bindings
            return buildBindingResponse(request, aor);
        }

        // Check for wildcard unregistration
        if (contacts.size() == 1 && "*".equals(contacts.getFirst().strip())) {
            if (expires != 0) {
                return SipResponse.builder(SipStatus.BAD_REQUEST)
                        .fromRequest(request)
                        .build();
            }
            removeAllBindings(aor);
            return SipResponse.builder(SipStatus.OK)
                    .fromRequest(request)
                    .build();
        }

        String callId = headers.callId();
        long cseq = headers.cseq().sequence();

        // Process each Contact
        for (String contactStr : contacts) {
            AddressHeader contact = AddressHeader.parse(contactStr);
            String contactUri = contact.uri().format();

            // Check for per-contact expires parameter
            int contactExpires = contact.params().containsKey("expires")
                    ? Integer.parseInt(contact.params().get("expires"))
                    : expires;

            if (contactExpires == 0) {
                removeBinding(aor, contactUri);
            } else {
                Instant expiresAt = Instant.now().plusSeconds(contactExpires);
                addOrUpdateBinding(aor, contactUri, expiresAt, callId, cseq);
            }
        }

        return buildBindingResponse(request, aor);
    }

    /**
     * Looks up current bindings for an AOR.
     *
     * @param aor the Address-of-Record
     * @return the current non-expired bindings
     * @since 0.1.0
     */
    public List<RegistrationBinding> lookup(String aor) {
        List<RegistrationBinding> aorBindings = bindings.get(aor);
        if (aorBindings == null) return List.of();

        synchronized (aorBindings) {
            return aorBindings.stream()
                    .filter(b -> !b.isExpired())
                    .toList();
        }
    }

    /**
     * Returns the total number of active bindings across all AORs.
     *
     * @return the binding count
     * @since 0.1.0
     */
    public int bindingCount() {
        return bindings.values().stream()
                .mapToInt(list -> {
                    synchronized (list) {
                        return (int) list.stream().filter(b -> !b.isExpired()).count();
                    }
                })
                .sum();
    }

    /**
     * Returns the registrar domain.
     *
     * @return the domain
     * @since 0.1.0
     */
    public String domain() {
        return domain;
    }

    private void addOrUpdateBinding(String aor, String contactUri,
                                     Instant expiresAt, String callId, long cseq) {
        var binding = new RegistrationBinding(aor, contactUri, expiresAt, callId, cseq);
        bindings.compute(aor, (key, existing) -> {
            if (existing == null) {
                var list = Collections.synchronizedList(new ArrayList<RegistrationBinding>());
                list.add(binding);
                return list;
            }
            synchronized (existing) {
                existing.removeIf(b -> b.contactUri().equals(contactUri));
                existing.add(binding);
            }
            return existing;
        });
        LOG.debug("Added/updated binding: {} -> {} (expires {})", aor, contactUri, expiresAt);
    }

    private void removeBinding(String aor, String contactUri) {
        bindings.computeIfPresent(aor, (key, existing) -> {
            synchronized (existing) {
                existing.removeIf(b -> b.contactUri().equals(contactUri));
            }
            return existing.isEmpty() ? null : existing;
        });
        LOG.debug("Removed binding: {} -> {}", aor, contactUri);
    }

    private void removeAllBindings(String aor) {
        bindings.remove(aor);
        LOG.debug("Removed all bindings for: {}", aor);
    }

    private SipResponse buildBindingResponse(SipRequest request, String aor) {
        var builder = SipResponse.builder(SipStatus.OK)
                .fromRequest(request);

        List<RegistrationBinding> current = lookup(aor);
        for (RegistrationBinding b : current) {
            builder.addHeader(SipHeaders.CONTACT,
                    "<" + b.contactUri() + ">;expires=" + b.ttlSeconds());
        }

        return builder.build();
    }
}
