package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Metadata request (API key 3) for topic/partition/broker discovery.
 *
 * @param topics                    the list of topic names (null = all topics)
 * @param allowAutoTopicCreation    whether to auto-create requested topics
 * @since 0.1.0
 */
public record MetadataRequest(List<String> topics, boolean allowAutoTopicCreation) {

    /** Creates a metadata request for all topics. */
    public MetadataRequest() {
        this(null, false);
    }

    /**
     * Creates a metadata request for specific topics.
     *
     * @param topics the topics to query
     */
    public MetadataRequest(List<String> topics) {
        this(topics, false);
    }
}
