package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.common.*;
import ssg.legoflow.messaging.kafka.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
/**
 * Kafka admin client for topic management and group inspection.
 *
 * @since 0.1.0
 */
public final class KafkaAdminClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAdminClient.class);

    private final KafkaConnection connection;

    /**
     * Creates a new admin client.
     *
     * @param host     the broker host
     * @param port     the broker port
     * @param clientId the client ID
     */
    public KafkaAdminClient(String host, int port, String clientId) {
        this.connection = new KafkaConnection(host, port, clientId);
    }

    /**
     * Connects to the broker.
     *
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        connection.connect();
    }

    /**
     * Negotiates API versions with the broker.
     *
     * @return the supported API versions
     * @throws IOException if the request fails
     */
    public ApiVersionsResponse apiVersions() throws IOException {
        byte[] payload = KafkaCodec.encodeApiVersionsRequest(new ApiVersionsRequest());
        ByteBuffer resp = connection.sendAndReceive(ApiKey.API_VERSIONS.key(), (short) 0, payload);
        return KafkaCodec.decodeApiVersionsResponse(resp);
    }

    /**
     * Fetches cluster metadata.
     *
     * @param topics the topics to query (null for all)
     * @return the metadata response
     * @throws IOException if the request fails
     */
    public MetadataResponse metadata(List<String> topics) throws IOException {
        byte[] payload = KafkaCodec.encodeMetadataRequest(new MetadataRequest(topics));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.METADATA.key(), (short) 0, payload);
        return KafkaCodec.decodeMetadataResponse(resp);
    }

    /**
     * Creates topics.
     *
     * @param topics the topics to create
     * @return the response
     * @throws IOException if the request fails
     */
    public CreateTopicsResponse createTopics(List<CreateTopicsRequest.TopicCreate> topics) throws IOException {
        byte[] payload = KafkaCodec.encodeCreateTopicsRequest(new CreateTopicsRequest(topics, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.CREATE_TOPICS.key(), (short) 0, payload);
        return KafkaCodec.decodeCreateTopicsResponse(resp);
    }

    /**
     * Creates a single topic.
     *
     * @param name          the topic name
     * @param numPartitions the number of partitions
     * @return the error code
     * @throws IOException if the request fails
     */
    public short createTopic(String name, int numPartitions) throws IOException {
        var resp = createTopics(List.of(
                new CreateTopicsRequest.TopicCreate(name, numPartitions, (short) 1, Map.of())));
        return resp.topics().isEmpty() ? KafkaErrors.UNKNOWN_SERVER_ERROR.code()
                : resp.topics().getFirst().errorCode();
    }

    /**
     * Deletes topics.
     *
     * @param topicNames the topic names to delete
     * @return the response
     * @throws IOException if the request fails
     */
    public DeleteTopicsResponse deleteTopics(List<String> topicNames) throws IOException {
        byte[] payload = KafkaCodec.encodeDeleteTopicsRequest(new DeleteTopicsRequest(topicNames, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DELETE_TOPICS.key(), (short) 0, payload);
        return KafkaCodec.decodeDeleteTopicsResponse(resp);
    }

    /**
     * Describes consumer groups.
     *
     * @param groupIds the group IDs to describe
     * @return the response
     * @throws IOException if the request fails
     */
    public DescribeGroupsResponse describeGroups(List<String> groupIds) throws IOException {
        byte[] payload = KafkaCodec.encodeDescribeGroupsRequest(new DescribeGroupsRequest(groupIds));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DESCRIBE_GROUPS.key(), (short) 0, payload);
        return KafkaCodec.decodeDescribeGroupsResponse(resp);
    }

    /**
     * Lists offsets for topic-partitions.
     *
     * @param request the list offsets request
     * @return the response
     * @throws IOException if the request fails
     */
    public ListOffsetsResponse listOffsets(ListOffsetsRequest request) throws IOException {
        byte[] payload = KafkaCodec.encodeListOffsetsRequest(request);
        ByteBuffer resp = connection.sendAndReceive(ApiKey.LIST_OFFSETS.key(), (short) 0, payload);
        return KafkaCodec.decodeListOffsetsResponse(resp);
    }

    /**
     * Finds the coordinator for a group or transactional ID.
     *
     * @param key     the coordinator key
     * @param keyType the key type (0=group, 1=transaction)
     * @return the response
     * @throws IOException if the request fails
     */
    public FindCoordinatorResponse findCoordinator(String key, byte keyType) throws IOException {
        byte[] payload = KafkaCodec.encodeFindCoordinatorRequest(new FindCoordinatorRequest(key, keyType));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.FIND_COORDINATOR.key(), (short) 0, payload);
        return KafkaCodec.decodeFindCoordinatorResponse(resp);
    }

    /**
     * Lists all consumer groups.
     *
     * @return the response
     * @throws IOException if the request fails
     */
    public ListGroupsResponse listGroups() throws IOException {
        byte[] payload = KafkaCodec.encodeListGroupsRequest(new ListGroupsRequest());
        ByteBuffer resp = connection.sendAndReceive(ApiKey.LIST_GROUPS.key(), (short) 0, payload);
        return KafkaCodec.decodeListGroupsResponse(resp);
    }

    /**
     * Deletes consumer groups.
     *
     * @param groupIds the group IDs to delete
     * @return the response
     * @throws IOException if the request fails
     */
    public DeleteGroupsResponse deleteGroups(List<String> groupIds) throws IOException {
        byte[] payload = KafkaCodec.encodeDeleteGroupsRequest(new DeleteGroupsRequest(groupIds));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DELETE_GROUPS.key(), (short) 0, payload);
        return KafkaCodec.decodeDeleteGroupsResponse(resp);
    }

    /**
     * Creates additional partitions for existing topics.
     *
     * @param topics the topics with new partition counts
     * @return the response
     * @throws IOException if the request fails
     */
    public CreatePartitionsResponse createPartitions(List<CreatePartitionsRequest.TopicNewPartitions> topics)
            throws IOException {
        byte[] payload = KafkaCodec.encodeCreatePartitionsRequest(
                new CreatePartitionsRequest(topics, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.CREATE_PARTITIONS.key(), (short) 0, payload);
        return KafkaCodec.decodeCreatePartitionsResponse(resp);
    }

    /**
     * Deletes records before the given offsets.
     *
     * @param topics the topics with partition offsets
     * @return the response
     * @throws IOException if the request fails
     */
    public DeleteRecordsResponse deleteRecords(List<DeleteRecordsRequest.TopicData> topics) throws IOException {
        byte[] payload = KafkaCodec.encodeDeleteRecordsRequest(
                new DeleteRecordsRequest(topics, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DELETE_RECORDS.key(), (short) 0, payload);
        return KafkaCodec.decodeDeleteRecordsResponse(resp);
    }

    /**
     * Deletes committed offsets for a consumer group.
     *
     * @param groupId the group ID
     * @param topics  the topics with partitions whose offsets to delete
     * @return the response
     * @throws IOException if the request fails
     */
    public OffsetDeleteResponse offsetDelete(String groupId, List<OffsetDeleteRequest.TopicData> topics)
            throws IOException {
        byte[] payload = KafkaCodec.encodeOffsetDeleteRequest(new OffsetDeleteRequest(groupId, topics));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.OFFSET_DELETE.key(), (short) 0, payload);
        return KafkaCodec.decodeOffsetDeleteResponse(resp);
    }

    /**
     * Describes configurations for the specified resources.
     *
     * @param resources the resources to describe
     * @return the response
     * @throws IOException if the request fails
     */
    public DescribeConfigsResponse describeConfigs(List<DescribeConfigsRequest.ResourceRequest> resources)
            throws IOException {
        byte[] payload = KafkaCodec.encodeDescribeConfigsRequest(new DescribeConfigsRequest(resources));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DESCRIBE_CONFIGS.key(), (short) 0, payload);
        return KafkaCodec.decodeDescribeConfigsResponse(resp);
    }

    /**
     * Alters configurations for the specified resources.
     *
     * @param resources the resources with new configurations
     * @return the response
     * @throws IOException if the request fails
     */
    public AlterConfigsResponse alterConfigs(List<AlterConfigsRequest.ResourceConfig> resources)
            throws IOException {
        byte[] payload = KafkaCodec.encodeAlterConfigsRequest(new AlterConfigsRequest(resources, false));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.ALTER_CONFIGS.key(), (short) 0, payload);
        return KafkaCodec.decodeAlterConfigsResponse(resp);
    }

    @Override
    public void close() {
        connection.close();
    }
}
