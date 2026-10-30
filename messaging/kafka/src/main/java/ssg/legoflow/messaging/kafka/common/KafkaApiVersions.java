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
        // Pinned floor for every API: v0, until its higher-version logic is verified.
        for (ApiKey ak : ApiKey.values()) {
            m.put(ak.key(), (short) 0);
        }
        // Raised pins for APIs with verified broker handler + record model:
        m.put(ApiKey.METADATA.key(), (short) 12);
        m.put(ApiKey.LEADER_AND_ISR.key(), (short) 7);
        m.put(ApiKey.STOP_REPLICA.key(), (short) 4);
        m.put(ApiKey.UPDATE_METADATA.key(), (short) 8);
        m.put(ApiKey.CONTROLLED_SHUTDOWN.key(), (short) 1);
        m.put(ApiKey.OFFSET_FOR_LEADER_EPOCH.key(), (short) 4);
        m.put(ApiKey.PRODUCE.key(), (short) 9);
        m.put(ApiKey.FETCH.key(), (short) 15);
        m.put(ApiKey.LIST_OFFSETS.key(), (short) 8);
        m.put(ApiKey.FIND_COORDINATOR.key(), (short) 4);
        m.put(ApiKey.JOIN_GROUP.key(), (short) 9);
        m.put(ApiKey.SYNC_GROUP.key(), (short) 5);
        m.put(ApiKey.HEARTBEAT.key(), (short) 4);
        m.put(ApiKey.LEAVE_GROUP.key(), (short) 5);
        m.put(ApiKey.OFFSET_COMMIT.key(), (short) 9);
        m.put(ApiKey.OFFSET_FETCH.key(), (short) 8);
        m.put(ApiKey.CREATE_TOPICS.key(), (short) 7);
        m.put(ApiKey.DELETE_TOPICS.key(), (short) 6);
        m.put(ApiKey.DESCRIBE_GROUPS.key(), (short) 5);
        m.put(ApiKey.LIST_GROUPS.key(), (short) 4);
        m.put(ApiKey.SASL_HANDSHAKE.key(), (short) 1);
        m.put(ApiKey.DELETE_RECORDS.key(), (short) 2);
        m.put(ApiKey.CREATE_PARTITIONS.key(), (short) 3);
        m.put(ApiKey.SASL_AUTHENTICATE.key(), (short) 2);
        m.put(ApiKey.DELETE_GROUPS.key(), (short) 2);
        m.put(ApiKey.OFFSET_DELETE.key(), (short) 0);
        m.put(ApiKey.DESCRIBE_CONFIGS.key(), (short) 4);
        m.put(ApiKey.ALTER_CONFIGS.key(), (short) 2);
        m.put(ApiKey.INIT_PRODUCER_ID.key(), (short) 4);
        m.put(ApiKey.ADD_PARTITIONS_TO_TXN.key(), (short) 4);
        m.put(ApiKey.ADD_OFFSETS_TO_TXN.key(), (short) 3);
        m.put(ApiKey.END_TXN.key(), (short) 3);
        m.put(ApiKey.WRITE_TXN_MARKERS.key(), (short) 1);
        m.put(ApiKey.TXN_OFFSET_COMMIT.key(), (short) 3);
        m.put(ApiKey.ALTER_PARTITION_REASSIGNMENTS.key(), (short) 0);
        m.put(ApiKey.LIST_PARTITION_REASSIGNMENTS.key(), (short) 0);
        m.put(ApiKey.API_VERSIONS.key(), (short) 3);
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
