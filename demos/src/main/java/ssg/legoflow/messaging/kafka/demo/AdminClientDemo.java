package ssg.legoflow.messaging.kafka.demo;

import ssg.legoflow.messaging.kafka.broker.KafkaBroker;
import ssg.legoflow.messaging.kafka.client.KafkaAdminClient;
import ssg.legoflow.messaging.kafka.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Demo: admin client operations — topic CRUD, metadata, API versions.
 *
 * @since 0.1.0
 */
public final class AdminClientDemo {

    private static final Logger LOG = LoggerFactory.getLogger(AdminClientDemo.class);

    private AdminClientDemo() {
    }

    /**
     * Runs the admin client demo.
     *
     * @return the metadata response
     * @throws IOException if an error occurs
     */
    public static MetadataResponse run() throws IOException {
        try (KafkaBroker broker = new KafkaBroker("localhost", 0)) {
            broker.start();

            try (KafkaAdminClient admin = new KafkaAdminClient("localhost", broker.port(), "admin-client")) {
                admin.connect();

                // API versions
                var versions = admin.apiVersions();
                LOG.info("Supported APIs: {}", versions.apiKeys().size());

                // Create topics
                admin.createTopics(List.of(
                        new CreateTopicsRequest.TopicCreate("topic-a", 3, (short) 1, Map.of()),
                        new CreateTopicsRequest.TopicCreate("topic-b", 2, (short) 1, Map.of())));
                LOG.info("Topics created");

                // Get metadata
                var metadata = admin.metadata(null);
                LOG.info("Brokers: {}, Topics: {}", metadata.brokers().size(), metadata.topics().size());

                // Delete topic
                admin.deleteTopics(List.of("topic-b"));
                LOG.info("Deleted topic-b");

                // Final metadata
                var finalMetadata = admin.metadata(null);
                LOG.info("Remaining topics: {}", finalMetadata.topics().size());

                return finalMetadata;
            }
        }
    }
}
