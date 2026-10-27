package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.codec.MetadataCodec;
import ssg.legoflow.messaging.kafka.common.*;
import ssg.legoflow.messaging.kafka.protocol.*;
import ssg.legoflow.service.channel.ByteTransport;
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
    private final Map<Short, Short> negotiatedVersions;

    /**
     * Creates a new admin client over an injected transport.
     *
     * @param transport  the connection transport (service layer or in-memory for tests)
     * @param clientId   the client identifier
     */
    public KafkaAdminClient(ByteTransport transport, String clientId) {
        this.connection = new KafkaConnection(transport, clientId);
        this.negotiatedVersions = new HashMap<>();
    }

    /**
     * Returns the negotiated maximum version for the given API key.
     *
     * @param apiKey the API key
     * @return the negotiated version, or 0 if not yet negotiated
     */
    public short getNegotiatedVersion(short apiKey) {
        return negotiatedVersions.getOrDefault(apiKey, (short) 0);
    }

    /**
     * Verifies the connection is open (no-op when already connected; the transport is
     * injected at construction).
     *
     * @throws IOException if the transport is not open
     */
    public void connect() throws IOException {
        if (!connection.isConnected()) {
            throw new IOException("Connection not open");
        }
    }

    /** Exposes the underlying connection (for tests). */
    KafkaConnection connection() {
        return connection;
    }

    /**
     * Negotiates API versions with the broker.
     *
     * @return the supported API versions
     * @throws IOException if the request fails
     */
    public ApiVersionsResponse apiVersions() throws IOException {
        byte[] payload = KafkaCodec.encodeApiVersionsRequest((short) 0, new ApiVersionsRequest());
        ByteBuffer resp = connection.sendAndReceive(ApiKey.API_VERSIONS.key(), (short) 0, payload);
        return KafkaCodec.decodeApiVersionsResponse((short) 0, resp);
    }

    /**
     * Fetches cluster metadata.
     *
     * @param topics the topics to query (null for all)
     * @return the metadata response
     * @throws IOException if the request fails
     */
    public MetadataResponse metadata(List<String> topics) throws IOException {
        short version = getNegotiatedVersion(ApiKey.METADATA.key());
        byte[] payload = KafkaCodec.encodeMetadataRequest(version, new MetadataRequest(topics));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.METADATA.key(), version, payload);
        return KafkaCodec.decodeMetadataResponse(version, resp);
    }

    /**
     * Creates topics.
     *
     * @param topics the topics to create
     * @return the response
     * @throws IOException if the request fails
     */
    public CreateTopicsResponse createTopics(List<CreateTopicsRequest.TopicCreate> topics) throws IOException {
        short version = getNegotiatedVersion(ApiKey.CREATE_TOPICS.key());
        byte[] payload = KafkaCodec.encodeCreateTopicsRequest(version, new CreateTopicsRequest(topics, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.CREATE_TOPICS.key(), version, payload);
        return KafkaCodec.decodeCreateTopicsResponse(version, resp);
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
        short version = getNegotiatedVersion(ApiKey.DELETE_TOPICS.key());
        byte[] payload = KafkaCodec.encodeDeleteTopicsRequest(version, new DeleteTopicsRequest(topicNames, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DELETE_TOPICS.key(), version, payload);
        return KafkaCodec.decodeDeleteTopicsResponse(version, resp);
    }

    /**
     * Describes consumer groups.
     *
     * @param groupIds the group IDs to describe
     * @return the response
     * @throws IOException if the request fails
     */
    public DescribeGroupsResponse describeGroups(List<String> groupIds) throws IOException {
        short version = getNegotiatedVersion(ApiKey.DESCRIBE_GROUPS.key());
        byte[] payload = KafkaCodec.encodeDescribeGroupsRequest(version, new DescribeGroupsRequest(groupIds));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DESCRIBE_GROUPS.key(), version, payload);
        return KafkaCodec.decodeDescribeGroupsResponse(version, resp);
    }

    /**
     * Lists offsets for topic-partitions.
     *
     * @param request the list offsets request
     * @return the response
     * @throws IOException if the request fails
     */
    public ListOffsetsResponse listOffsets(ListOffsetsRequest request) throws IOException {
        short version = getNegotiatedVersion(ApiKey.LIST_OFFSETS.key());
        byte[] payload = KafkaCodec.encodeListOffsetsRequest(version, request);
        ByteBuffer resp = connection.sendAndReceive(ApiKey.LIST_OFFSETS.key(), version, payload);
        return KafkaCodec.decodeListOffsetsResponse(version, resp);
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
        short version = getNegotiatedVersion(ApiKey.FIND_COORDINATOR.key());
        byte[] payload = KafkaCodec.encodeFindCoordinatorRequest(version, new FindCoordinatorRequest(key, keyType));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.FIND_COORDINATOR.key(), version, payload);
        return KafkaCodec.decodeFindCoordinatorResponse(version, resp);
    }

    /**
     * Lists all consumer groups.
     *
     * @return the response
     * @throws IOException if the request fails
     */
    public ListGroupsResponse listGroups() throws IOException {
        short version = getNegotiatedVersion(ApiKey.LIST_GROUPS.key());
        byte[] payload = KafkaCodec.encodeListGroupsRequest(version, new ListGroupsRequest());
        ByteBuffer resp = connection.sendAndReceive(ApiKey.LIST_GROUPS.key(), version, payload);
        return KafkaCodec.decodeListGroupsResponse(version, resp);
    }

    /**
     * Deletes consumer groups.
     *
     * @param groupIds the group IDs to delete
     * @return the response
     * @throws IOException if the request fails
     */
    public DeleteGroupsResponse deleteGroups(List<String> groupIds) throws IOException {
        short version = getNegotiatedVersion(ApiKey.DELETE_GROUPS.key());
        byte[] payload = KafkaCodec.encodeDeleteGroupsRequest(version, new DeleteGroupsRequest(groupIds));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DELETE_GROUPS.key(), version, payload);
        return KafkaCodec.decodeDeleteGroupsResponse(version, resp);
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
        short version = getNegotiatedVersion(ApiKey.CREATE_PARTITIONS.key());
        byte[] payload = KafkaCodec.encodeCreatePartitionsRequest(version,
                new CreatePartitionsRequest(topics, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.CREATE_PARTITIONS.key(), version, payload);
        return KafkaCodec.decodeCreatePartitionsResponse(version, resp);
    }

    /**
     * Deletes records before the given offsets.
     *
     * @param topics the topics with partition offsets
     * @return the response
     * @throws IOException if the request fails
     */
    public DeleteRecordsResponse deleteRecords(List<DeleteRecordsRequest.TopicData> topics) throws IOException {
        short version = getNegotiatedVersion(ApiKey.DELETE_RECORDS.key());
        byte[] payload = KafkaCodec.encodeDeleteRecordsRequest(version,
                new DeleteRecordsRequest(topics, 30000));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DELETE_RECORDS.key(), version, payload);
        return KafkaCodec.decodeDeleteRecordsResponse(version, resp);
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
        short version = getNegotiatedVersion(ApiKey.OFFSET_DELETE.key());
        byte[] payload = KafkaCodec.encodeOffsetDeleteRequest(new OffsetDeleteRequest(groupId, topics));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.OFFSET_DELETE.key(), version, payload);
        return KafkaCodec.decodeOffsetDeleteResponse(version, resp);
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
        short version = getNegotiatedVersion(ApiKey.DESCRIBE_CONFIGS.key());
        byte[] payload = KafkaCodec.encodeDescribeConfigsRequest(version, new DescribeConfigsRequest(resources));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.DESCRIBE_CONFIGS.key(), version, payload);
        return KafkaCodec.decodeDescribeConfigsResponse(version, resp);
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
        short version = getNegotiatedVersion(ApiKey.ALTER_CONFIGS.key());
        byte[] payload = KafkaCodec.encodeAlterConfigsRequest(version, new AlterConfigsRequest(resources, false));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.ALTER_CONFIGS.key(), version, payload);
        return KafkaCodec.decodeAlterConfigsResponse(version, resp);
    }

    @Override
    public void close() {
        connection.close();
    }
}
