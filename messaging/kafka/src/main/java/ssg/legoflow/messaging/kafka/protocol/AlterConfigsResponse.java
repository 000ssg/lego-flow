package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * AlterConfigs response (API key 33).
 *
 * @param resources the results of each resource configuration alteration
 * @since 0.1.0
 */
public record AlterConfigsResponse(List<ResourceResponse> resources) {

    /**
     * Result of altering configuration for a single resource.
     *
     * @param errorCode    the error code
     * @param resourceName the resource name
     */
    public record ResourceResponse(short errorCode, String resourceName) {
    }
}
