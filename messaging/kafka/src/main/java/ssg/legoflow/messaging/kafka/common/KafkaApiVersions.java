package ssg.legoflow.messaging.kafka.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The set of Kafka API versions this build actually implements end-to-end
 * (broker handler + record model + client). This is the pinned floor used for
 * API version negotiation: the broker advertises up to {@link #implementedMax}
 * per API, and the client caps its chosen version at that value.
 *
 * <p>The dedicated per-API codecs are versioned <b>beyond</b> this floor (fully
 * implemented and tested up to each codec's own {@code MAX_VERSION}, then frozen).
 * Raising a pin here is the single change needed for negotiation to select a
 * higher version once the corresponding broker handler + record logic is
 * verified for that version — the codec itself is not touched.
 *
 * @since 0.1.0
 */
public final class KafkaApiVersions {

    /**
     * Pinned implemented-maximum version per numeric API key.
     *
     * <p>Every API is pinned to v0: the broker handlers and record models are
     * verified end-to-end for v0 only. Raise an individual entry (see below) as
     * each API's higher-version logic is verified.
     */
    private static final Map<Short, Short> PINNED = buildPinned();

    private static Map<Short, Short> buildPinned() {
        Map<Short, Short> m = new HashMap<>();
        for (ApiKey ak : ApiKey.values()) {
            // Pinned floor for every API: v0, until its higher-version logic is verified.
            m.put(ak.key(), (short) 0);
        }
        // Raise a pin here once that API's broker handler + record model is verified
        // for the higher version, e.g.:
        //   m.put(ApiKey.LIST_OFFSETS.key(), (short) 1);
        return Collections.unmodifiableMap(m);
    }

    private KafkaApiVersions() {}

    /**
     * The maximum API version this build implements end-to-end for the given key.
     *
     * @param apiKey the numeric API key
     * @return the implemented maximum version (>= 0)
     */
    public static short implementedMax(short apiKey) {
        return PINNED.getOrDefault(apiKey, (short) 0);
    }

    /**
     * Whether this build implements the given API version.
     *
     * @param apiKey   the numeric API key
     * @param version  the candidate version
     * @return true if {@code version} is within the implemented range
     */
    public static boolean supports(short apiKey, short version) {
        return version >= 0 && version <= implementedMax(apiKey);
    }
}
