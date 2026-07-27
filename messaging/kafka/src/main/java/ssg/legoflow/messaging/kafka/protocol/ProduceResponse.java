package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Produce response (API key 0).
 *
 * @param responses    the per-topic responses
 * @param throttleTimeMs the throttle time in milliseconds
 * @since 1.0.0
 */
public record ProduceResponse(List<TopicResponse> responses, int throttleTimeMs) {

    /**
     * Per-topic produce response.
     *
     * @param name              the topic name
     * @param partitionResponses the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitionResponses) {
    }

    /**
     * Per-partition produce response.
     *
     * @param partitionIndex the partition index
     * @param errorCode      the error code
     * @param baseOffset     the base offset of the appended records
     * @param logAppendTimeMs the log append time (-1 if not available)
     */
    public record PartitionResponse(int partitionIndex, short errorCode, long baseOffset,
                                    long logAppendTimeMs) {
    }
}
