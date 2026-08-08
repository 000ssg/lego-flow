package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DescribeConfigs request (API key 32).
 *
 * @param resources the resources whose configurations to describe
 * @since 0.1.0
 */
public record DescribeConfigsRequest(List<ResourceRequest> resources) {

    /**
     * A resource whose configuration is to be described.
     *
     * @param resourceType the resource type (2=TOPIC, 4=BROKER)
     * @param resourceName the resource name
     * @param configNames  specific config names to describe (null = all)
     */
    public record ResourceRequest(byte resourceType, String resourceName, List<String> configNames) {
    }
}
