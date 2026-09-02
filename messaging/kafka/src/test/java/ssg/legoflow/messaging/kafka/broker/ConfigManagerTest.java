package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class ConfigManagerTest {

    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        configManager = new ConfigManager();
    }

    @Test
    void testDescribeBrokerConfigs() {
        var configs = configManager.describeConfigs(ConfigManager.RESOURCE_TYPE_BROKER, "0");
        assertThat(configs).containsKey("num.partitions");
        assertThat(configs).containsKey("log.retention.ms");
        assertThat(configs).containsKey("message.max.bytes");
        assertThat(configs).containsKey("default.replication.factor");
        assertThat(configs.get("num.partitions").readOnly()).isTrue();
    }

    @Test
    void testDescribeTopicConfigsDefaults() {
        configManager.setDefaultTopicConfig("test-topic");
        var configs = configManager.describeConfigs(ConfigManager.RESOURCE_TYPE_TOPIC, "test-topic");
        assertThat(configs).containsKey("retention.ms");
        assertThat(configs).containsKey("cleanup.policy");
        assertThat(configs).containsKey("max.message.bytes");
        assertThat(configs).containsKey("segment.bytes");
        assertThat(configs).containsKey("min.insync.replicas");
        assertThat(configs.get("retention.ms").readOnly()).isFalse();
        assertThat(configs.get("cleanup.policy").value()).isEqualTo("delete");
    }

    @Test
    void testAlterTopicConfigs() {
        configManager.setDefaultTopicConfig("test-topic");
        short error = configManager.alterConfigs(ConfigManager.RESOURCE_TYPE_TOPIC, "test-topic",
                Map.of("retention.ms", "3600000"));
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());

        var configs = configManager.describeConfigs(ConfigManager.RESOURCE_TYPE_TOPIC, "test-topic");
        assertThat(configs.get("retention.ms").value()).isEqualTo("3600000");
    }

    @Test
    void testAlterBrokerConfigsRejected() {
        short error = configManager.alterConfigs(ConfigManager.RESOURCE_TYPE_BROKER, "0",
                Map.of("num.partitions", "4"));
        assertThat(error).isEqualTo(KafkaErrors.INVALID_CONFIG.code());
    }

    @Test
    void testAlterUnknownConfigRejected() {
        configManager.setDefaultTopicConfig("test-topic");
        short error = configManager.alterConfigs(ConfigManager.RESOURCE_TYPE_TOPIC, "test-topic",
                Map.of("unknown.config", "value"));
        assertThat(error).isEqualTo(KafkaErrors.INVALID_CONFIG.code());
    }

    @Test
    void testGetTopicConfigDefault() {
        configManager.setDefaultTopicConfig("test-topic");
        String value = configManager.getTopicConfig("test-topic", "cleanup.policy");
        assertThat(value).isEqualTo("delete");
    }

    @Test
    void testGetTopicConfigOverridden() {
        configManager.setDefaultTopicConfig("test-topic");
        configManager.alterConfigs(ConfigManager.RESOURCE_TYPE_TOPIC, "test-topic",
                Map.of("retention.ms", "1000"));
        assertThat(configManager.getTopicConfig("test-topic", "retention.ms")).isEqualTo("1000");
    }

    @Test
    void testAlterInvalidResourceType() {
        short error = configManager.alterConfigs((byte) 99, "unknown", Map.of("key", "value"));
        assertThat(error).isEqualTo(KafkaErrors.INVALID_REQUEST.code());
    }
}
