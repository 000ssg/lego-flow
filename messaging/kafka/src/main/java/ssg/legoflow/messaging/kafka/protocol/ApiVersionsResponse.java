package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ApiVersions response (API key 18).
 *
 * <p>Per-version layout (Kafka 3.6.1, {@code ApiVersionsResponse.json}):
 * v0 — {@code errorCode, []apiKeys}; v1/v2 — adds {@code throttleTimeMs} (int32);
 * v3 — flexible encoding + tagged feature fields.
 *
 * @param errorCode      the error code
 * @param apiKeys        the list of supported API versions
 * @param throttleTimeMs the time the request was throttled for in milliseconds (v1+; 0 when absent)
 * @since 0.1.0
 */
public record ApiVersionsResponse(short errorCode, List<ApiVersion> apiKeys, long throttleTimeMs) {

    /**
     * Convenience constructor for v0 (no throttle time).
     *
     * @param errorCode the error code
     * @param apiKeys   the list of supported API versions
     */
    public ApiVersionsResponse(short errorCode, List<ApiVersion> apiKeys) {
        this(errorCode, apiKeys, 0L);
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
}
