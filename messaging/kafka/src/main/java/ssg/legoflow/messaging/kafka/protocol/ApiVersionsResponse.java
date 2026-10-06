package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ApiVersions response (API key 18).
 *
 * <p>Per-version layout (Kafka 3.6.1, {@code ApiVersionsResponse.json}):
 * v0 — {@code errorCode, []apiKeys}; v1/v2 — adds {@code throttleTimeMs} (int32);
 * v3 — flexible encoding; the four feature fields travel in the trailing
 * tagged-fields section (tags 0–3) and are absent (defaults) when not present:
 * {@code supportedFeatures} (tag 0), {@code finalizedFeaturesEpoch} (tag 1,
 * -1 = absent), {@code finalizedFeatures} (tag 2), {@code zkMigrationReady}
 * (tag 3, false = absent).
 *
 * @param errorCode             the error code
 * @param apiKeys               the list of supported API versions
 * @param throttleTimeMs        the time the request was throttled for in milliseconds (v1+; 0 when absent)
 * @param supportedFeatures     the features supported by the broker (v3+; empty when the tag is absent)
 * @param finalizedFeaturesEpoch the finalized-features epoch (v3+; -1 when the tag is absent)
 * @param finalizedFeatures     the finalized features (v3+; empty when the tag is absent)
 * @param zkMigrationReady      whether the cluster is ready for the ZK→KRaft migration (v3+; false when the tag is absent)
 * @since 0.1.0
 */
public record ApiVersionsResponse(short errorCode, List<ApiVersion> apiKeys, long throttleTimeMs,
                                  List<SupportedFeatureKey> supportedFeatures, long finalizedFeaturesEpoch,
                                  List<FinalizedFeatureKey> finalizedFeatures, boolean zkMigrationReady) {

    /**
     * Sentinel for an absent {@code FinalizedFeaturesEpoch} tag on the wire
     * (Kafka 3.6.1 omits the tag entirely when the epoch is -1).
     */
    public static final long ABSENT_FINALIZED_EPOCH = -1L;

    /**
     * Convenience constructor for v0 (no throttle time, no feature data).
     *
     * @param errorCode the error code
     * @param apiKeys   the list of supported API versions
     */
    public ApiVersionsResponse(short errorCode, List<ApiVersion> apiKeys) {
        this(errorCode, apiKeys, 0L, List.of(), -1L, List.of(), false);
    }

    /**
     * Convenience constructor for v1/v2 (no feature data).
     *
     * @param errorCode      the error code
     * @param apiKeys        the list of supported API versions
     * @param throttleTimeMs the time the request was throttled for in milliseconds
     */
    public ApiVersionsResponse(short errorCode, List<ApiVersion> apiKeys, long throttleTimeMs) {
        this(errorCode, apiKeys, throttleTimeMs, List.of(), -1L, List.of(), false);
    }

    /**
     * Entry point for the {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Hybrid builder for {@link ApiVersionsResponse}: the convenience constructors remain; this
     * named-field builder is the preferred entry point for new call sites. Each field defaults to
     * the value the spec takes when its version feature is absent, so a builder sets only the
     * fields a given version carries. {@code build()} delegates to the canonical constructor.
     */
    public static final class Builder {
        private short errorCode = 0;
        private List<ApiVersion> apiKeys = List.of();
        private long throttleTimeMs = 0L; // v1+; absent default
        private List<SupportedFeatureKey> supportedFeatures = List.of(); // v3+ tag 0; absent = empty
        private long finalizedFeaturesEpoch = ABSENT_FINALIZED_EPOCH; // v3+ tag 1; -1 = absent
        private List<FinalizedFeatureKey> finalizedFeatures = List.of(); // v3+ tag 2; absent = empty
        private boolean zkMigrationReady = false; // v3+ tag 3; false = absent

        public Builder errorCode(short v) { this.errorCode = v; return this; }
        public Builder apiKeys(List<ApiVersion> v) { this.apiKeys = v; return this; }
        public Builder throttleTimeMs(long v) { this.throttleTimeMs = v; return this; }
        public Builder supportedFeatures(List<SupportedFeatureKey> v) { this.supportedFeatures = v; return this; }
        public Builder finalizedFeaturesEpoch(long v) { this.finalizedFeaturesEpoch = v; return this; }
        public Builder finalizedFeatures(List<FinalizedFeatureKey> v) { this.finalizedFeatures = v; return this; }
        public Builder zkMigrationReady(boolean v) { this.zkMigrationReady = v; return this; }

        public ApiVersionsResponse build() {
            return new ApiVersionsResponse(errorCode, apiKeys, throttleTimeMs, supportedFeatures,
                    finalizedFeaturesEpoch, finalizedFeatures, zkMigrationReady);
        }
    }

    /**
     * A single API key version range.
     *
     * @param apiKey     the API key
     * @param minVersion the minimum supported version
     * @param maxVersion the maximum supported version
     */
    public record ApiVersion(short apiKey, short minVersion, short maxVersion) {
    }

    /**
     * A feature supported by the broker (v3, response tag 0).
     *
     * @param name       the feature name
     * @param minVersion the minimum supported version level
     * @param maxVersion the maximum supported version level
     */
    public record SupportedFeatureKey(String name, short minVersion, short maxVersion) {
    }

    /**
     * A finalized feature (v3, response tag 2). Wire field order is
     * {@code name, maxVersionLevel, minVersionLevel} per the 3.6.1 schema.
     *
     * @param name            the feature name
     * @param maxVersionLevel the maximum finalized version level
     * @param minVersionLevel the minimum finalized version level
     */
    public record FinalizedFeatureKey(String name, short maxVersionLevel, short minVersionLevel) {
    }
}
