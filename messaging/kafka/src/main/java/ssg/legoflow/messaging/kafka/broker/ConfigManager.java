package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.KafkaErrors;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages broker-level and per-topic configurations.
 *
 * <p>Supports describing and altering configurations for topics and brokers.
 * Broker configurations are read-only defaults; topic configurations can be
 * altered at runtime.
 *
 * @since 0.1.0
 */
public final class ConfigManager {

    /** Resource type constant for topics. */
    public static final byte RESOURCE_TYPE_TOPIC = 2;
    /** Resource type constant for brokers. */
    public static final byte RESOURCE_TYPE_BROKER = 4;

    private static final Map<String, String> DEFAULT_BROKER_CONFIGS = Map.of(
            "num.partitions", "1",
            "log.retention.ms", "604800000",
            "message.max.bytes", "1048576",
            "default.replication.factor", "1"
    );

    private static final Map<String, String> DEFAULT_TOPIC_CONFIGS = Map.of(
            "retention.ms", "604800000",
            "cleanup.policy", "delete",
            "max.message.bytes", "1048576",
            "segment.bytes", "1073741824",
            "min.insync.replicas", "1"
    );

    private static final Set<String> KNOWN_TOPIC_CONFIGS = Set.of(
            "retention.ms", "cleanup.policy", "max.message.bytes",
            "segment.bytes", "min.insync.replicas"
    );

    /** Internal storage keyed by resource name ("broker:0" for broker, topic name for topics). */
    private final Map<String, Map<String, String>> configs = new ConcurrentHashMap<>();

    /**
     * A configuration entry with metadata.
     *
     * @param value     the config value
     * @param readOnly  whether the config is read-only
     * @param sensitive whether the config value is sensitive
     */
    public record ConfigEntry(String value, boolean readOnly, boolean sensitive) {
    }

    /**
     * Describes the configuration for a resource.
     *
     * @param resourceType the resource type ({@link #RESOURCE_TYPE_TOPIC} or {@link #RESOURCE_TYPE_BROKER})
     * @param resourceName the resource name
     * @return the configuration entries
     */
    public Map<String, ConfigEntry> describeConfigs(byte resourceType, String resourceName) {
        Map<String, ConfigEntry> result = new LinkedHashMap<>();

        if (resourceType == RESOURCE_TYPE_BROKER) {
            for (var entry : DEFAULT_BROKER_CONFIGS.entrySet()) {
                result.put(entry.getKey(), new ConfigEntry(entry.getValue(), true, false));
            }
        } else if (resourceType == RESOURCE_TYPE_TOPIC) {
            Map<String, String> topicConfigs = configs.get(resourceName);
            Map<String, String> effective = new LinkedHashMap<>(DEFAULT_TOPIC_CONFIGS);
            if (topicConfigs != null) {
                effective.putAll(topicConfigs);
            }
            for (var entry : effective.entrySet()) {
                result.put(entry.getKey(), new ConfigEntry(entry.getValue(), false, false));
            }
        }

        return result;
    }

    /**
     * Alters the configuration for a resource.
     *
     * @param resourceType the resource type
     * @param resourceName the resource name
     * @param newConfigs   the new configuration values
     * @return the error code ({@link KafkaErrors#NONE} on success)
     */
    public short alterConfigs(byte resourceType, String resourceName, Map<String, String> newConfigs) {
        if (resourceType == RESOURCE_TYPE_BROKER) {
            return KafkaErrors.INVALID_CONFIG.code();
        }

        if (resourceType != RESOURCE_TYPE_TOPIC) {
            return KafkaErrors.INVALID_REQUEST.code();
        }

        // Validate all config keys
        for (String key : newConfigs.keySet()) {
            if (!KNOWN_TOPIC_CONFIGS.contains(key)) {
                return KafkaErrors.INVALID_CONFIG.code();
            }
        }

        configs.computeIfAbsent(resourceName, k -> new ConcurrentHashMap<>()).putAll(newConfigs);
        return KafkaErrors.NONE.code();
    }

    /**
     * Returns the effective value of a topic configuration key.
     *
     * @param topic the topic name
     * @param key   the config key
     * @return the config value, or null if not found
     */
    public String getTopicConfig(String topic, String key) {
        Map<String, String> topicConfigs = configs.get(topic);
        if (topicConfigs != null && topicConfigs.containsKey(key)) {
            return topicConfigs.get(key);
        }
        return DEFAULT_TOPIC_CONFIGS.get(key);
    }

    /**
     * Initializes default configuration for a newly created topic.
     *
     * @param topic the topic name
     */
    public void setDefaultTopicConfig(String topic) {
        configs.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
    }
}
