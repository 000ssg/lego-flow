package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * AlterConfigs request (API key 33).
 *
 * @param resources    the resources whose configurations to alter
 * @param validateOnly if true, validate the configuration without applying
 * @since 0.1.0
 */
public record AlterConfigsRequest(List<ResourceConfig> resources, boolean validateOnly) {

    /**
     * Configuration alteration for a single resource.
     *
     * @param resourceType the resource type (2=TOPIC, 4=BROKER)
     * @param resourceName the resource name
     * @param configs      the configuration entries to set
     */
    public record ResourceConfig(byte resourceType, String resourceName, List<ConfigEntry> configs) {
    }

    /**
     * A configuration entry to set.
     *
     * @param name  the config name
     * @param value the config value
     */
    public record ConfigEntry(String name, String value) {
    }
}
