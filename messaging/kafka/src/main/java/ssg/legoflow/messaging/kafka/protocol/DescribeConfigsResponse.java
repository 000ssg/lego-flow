package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DescribeConfigs response (API key 32).
 *
 * @param resources the described resource configurations
 * @since 1.0.0
 */
public record DescribeConfigsResponse(List<ResourceResponse> resources) {

    /**
     * Configuration description for a single resource.
     *
     * @param errorCode    the error code
     * @param resourceName the resource name
     * @param configs      the configuration entries
     */
    public record ResourceResponse(short errorCode, String resourceName, List<ConfigEntry> configs) {
    }

    /**
     * A single configuration entry.
     *
     * @param name        the config name
     * @param value       the config value
     * @param readOnly    whether the config is read-only
     * @param isSensitive whether the config value is sensitive
     */
    public record ConfigEntry(String name, String value, boolean readOnly, boolean isSensitive) {
    }
}
