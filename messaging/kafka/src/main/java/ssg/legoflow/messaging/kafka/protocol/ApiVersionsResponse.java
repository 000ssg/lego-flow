package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ApiVersions response (API key 18).
 *
 * @param errorCode  the error code
 * @param apiKeys    the list of supported API versions
 * @since 1.0.0
 */
public record ApiVersionsResponse(short errorCode, List<ApiVersion> apiKeys) {

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
