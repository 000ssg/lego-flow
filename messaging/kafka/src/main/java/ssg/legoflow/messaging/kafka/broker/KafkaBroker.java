package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.auth.*;
import ssg.legoflow.messaging.kafka.broker.storage.LogStorageFactory;
import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.common.*;
import ssg.legoflow.messaging.kafka.protocol.*;
import ssg.legoflow.messaging.kafka.record.RecordBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka broker implementation using virtual threads.
 *
 * <p>Provides a complete single-node Kafka broker with:
 * <ul>
 *   <li>Topic management (create/delete)</li>
 *   <li>Partition log storage (in-memory append-only)</li>
 *   <li>Consumer group coordination (rebalance protocol)</li>
 *   <li>Offset storage and management</li>
 *   <li>Idempotent producer dedup</li>
 *   <li>Transaction support</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class KafkaBroker implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBroker.class);

    private final int brokerId;
    private final String host;
    private final int requestedPort;
    private final int defaultPartitions;
    private final LogStorageFactory storageFactory;

    private final Map<TopicPartition, PartitionLog> partitionLogs = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> topicPartitions = new ConcurrentHashMap<>();
    private final ConsumerGroupCoordinator groupCoordinator = new ConsumerGroupCoordinator();
    private final TransactionManager transactionManager = new TransactionManager();
    private final ConfigManager configManager = new ConfigManager();
    private final CredentialStore credentialStore = new CredentialStore();
    private final ReplicaManager replicaManager;
    private final Map<TopicPartition, List<Integer>> reassignments = new ConcurrentHashMap<>();
    private final Set<String> enabledMechanisms = Set.of("PLAIN", "SCRAM-SHA-256");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Cluster metadata cache for UpdateMetadata requests.
     *
     * @param liveBrokers          the set of live brokers
     * @param partitionAssignments partition to replica assignments
     */
    record ClusterMetadata(List<Node> liveBrokers, Map<TopicPartition, int[]> partitionAssignments) {
    }

    private volatile ClusterMetadata clusterMetadata;

    private volatile ServerSocketChannel serverChannel;
    private volatile int boundPort;

    /**
     * Creates a new Kafka broker with a custom storage backend.
     *
     * @param host              the bind host
     * @param port              the bind port (0 for ephemeral)
     * @param brokerId          the broker ID
     * @param defaultPartitions the default number of partitions for new topics
     * @param storageFactory    the factory for creating partition log storage instances
     */
    public KafkaBroker(String host, int port, int brokerId, int defaultPartitions,
                        LogStorageFactory storageFactory) {
        this.host = host;
        this.requestedPort = port;
        this.brokerId = brokerId;
        this.defaultPartitions = defaultPartitions;
        this.storageFactory = storageFactory;
        this.replicaManager = new ReplicaManager(brokerId);
    }

    /**
     * Creates a new Kafka broker with in-memory storage.
     *
     * @param host              the bind host
     * @param port              the bind port (0 for ephemeral)
     * @param brokerId          the broker ID
     * @param defaultPartitions the default number of partitions for new topics
     */
    public KafkaBroker(String host, int port, int brokerId, int defaultPartitions) {
        this(host, port, brokerId, defaultPartitions, LogStorageFactory.inMemory());
    }

    /**
     * Creates a new Kafka broker with defaults.
     *
     * @param host the bind host
     * @param port the bind port (0 for ephemeral)
     */
    public KafkaBroker(String host, int port) {
        this(host, port, 0, 1);
    }

    /**
     * Starts the broker.
     *
     * @throws IOException if binding fails
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Broker already running");
        }

        transactionManager.setGroupCoordinator(groupCoordinator);

        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(host, requestedPort));
        boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

        LOG.info("Kafka broker started on {}:{} (brokerId={})", host, boundPort, brokerId);

        executor.submit(this::acceptLoop);
    }

    /**
     * Returns the actual port the broker is bound to.
     *
     * @return the bound port
     */
    public int port() {
        return boundPort;
    }

    /**
     * Creates a topic with the given number of partitions.
     *
     * @param name          the topic name
     * @param numPartitions the number of partitions
     * @return the error code
     */
    public short createTopic(String name, int numPartitions) {
        if (name == null || name.isEmpty()) return KafkaErrors.INVALID_TOPIC_EXCEPTION.code();
        if (numPartitions < 1) return KafkaErrors.INVALID_PARTITIONS.code();
        if (topicPartitions.containsKey(name)) return KafkaErrors.TOPIC_ALREADY_EXISTS.code();

        List<Integer> partitions = new ArrayList<>();
        for (int i = 0; i < numPartitions; i++) {
            partitions.add(i);
            partitionLogs.put(new TopicPartition(name, i),
                    new PartitionLog(name, i, storageFactory.create(name, i)));
        }
        topicPartitions.put(name, partitions);
        configManager.setDefaultTopicConfig(name);
        LOG.info("Created topic '{}' with {} partitions", name, numPartitions);
        return KafkaErrors.NONE.code();
    }

    /**
     * Deletes a topic.
     *
     * @param name the topic name
     * @return the error code
     */
    public short deleteTopic(String name) {
        List<Integer> parts = topicPartitions.remove(name);
        if (parts == null) return KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code();
        for (int p : parts) {
            partitionLogs.remove(new TopicPartition(name, p));
        }
        LOG.info("Deleted topic '{}'", name);
        return KafkaErrors.NONE.code();
    }

    /**
     * Returns the set of topic names.
     *
     * @return the topic names
     */
    public Set<String> topicNames() {
        return Collections.unmodifiableSet(topicPartitions.keySet());
    }

    /**
     * Returns the partition count for a topic.
     *
     * @param topic the topic name
     * @return the partition count, or -1 if topic doesn't exist
     */
    public int partitionCount(String topic) {
        List<Integer> parts = topicPartitions.get(topic);
        return parts != null ? parts.size() : -1;
    }

    /**
     * Returns the consumer group coordinator.
     *
     * @return the coordinator
     */
    public ConsumerGroupCoordinator groupCoordinator() {
        return groupCoordinator;
    }

    /**
     * Returns the transaction manager.
     *
     * @return the transaction manager
     */
    public TransactionManager transactionManager() {
        return transactionManager;
    }

    /**
     * Returns the configuration manager.
     *
     * @return the config manager
     */
    public ConfigManager configManager() {
        return configManager;
    }

    /**
     * Returns the credential store for SASL authentication.
     *
     * @return the credential store
     */
    public CredentialStore credentialStore() {
        return credentialStore;
    }

    /**
     * Returns the replica manager.
     *
     * @return the replica manager
     */
    public ReplicaManager replicaManager() {
        return replicaManager;
    }

    /**
     * Returns the broker ID.
     *
     * @return the broker ID
     */
    public int brokerId() {
        return brokerId;
    }

    /**
     * Returns the partition log for the given topic-partition.
     *
     * @param tp the topic-partition
     * @return the partition log, or null
     */
    public PartitionLog getPartitionLog(TopicPartition tp) {
        return partitionLogs.get(tp);
    }

    /**
     * Compacts all partition logs for topics with cleanup.policy=compact.
     *
     * @return the total number of records removed
     */
    public int compactAll() {
        int totalRemoved = 0;
        for (var entry : topicPartitions.entrySet()) {
            String topic = entry.getKey();
            String policy = configManager.getTopicConfig(topic, "cleanup.policy");
            if ("compact".equals(policy)) {
                for (int partition : entry.getValue()) {
                    PartitionLog log = partitionLogs.get(new TopicPartition(topic, partition));
                    if (log != null) {
                        totalRemoved += log.compact();
                    }
                }
            }
        }
        return totalRemoved;
    }

    /**
     * Per-connection authentication state.
     */
    static final class ConnectionState {
        SaslMechanism currentMechanism;
        boolean authenticated;
    }

    // --- Connection handling ---

    private void acceptLoop() {
        while (running.get()) {
            try {
                SocketChannel client = serverChannel.accept();
                executor.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleConnection(SocketChannel client) {
        try (client) {
            client.configureBlocking(true);
            LOG.debug("Client connected: {}", client.getRemoteAddress());
            var connState = new ConnectionState();

            while (running.get() && client.isOpen()) {
                // Read 4-byte length prefix
                ByteBuffer lenBuf = ByteBuffer.allocate(4);
                if (readFully(client, lenBuf) < 0) break;
                lenBuf.flip();
                int messageLen = lenBuf.getInt();

                if (messageLen <= 0 || messageLen > 100_000_000) {
                    LOG.warn("Invalid message length: {}", messageLen);
                    break;
                }

                // Read message body
                ByteBuffer msgBuf = ByteBuffer.allocate(messageLen);
                if (readFully(client, msgBuf) < 0) break;
                msgBuf.flip();

                // Decode header
                RequestHeader header = KafkaCodec.decodeRequestHeader(msgBuf);

                // Process request
                byte[] responseBody = processRequest(header, msgBuf, connState);

                // Send response
                ByteBuffer response = KafkaCodec.encodeResponse(
                        new ResponseHeader(header.correlationId()), responseBody);
                writeFully(client, response);
            }
        } catch (IOException e) {
            LOG.debug("Client disconnected: {}", e.getMessage());
        }
    }

    private byte[] processRequest(RequestHeader header, ByteBuffer body, ConnectionState connState) {
        ApiKey apiKey = ApiKey.forKey(header.apiKey());
        if (apiKey == null) {
            LOG.warn("Unknown API key: {}", header.apiKey());
            return new byte[0];
        }

        return switch (apiKey) {
            case API_VERSIONS -> handleApiVersions();
            case METADATA -> handleMetadata(body);
            case LEADER_AND_ISR -> handleLeaderAndIsr(body);
            case STOP_REPLICA -> handleStopReplica(body);
            case UPDATE_METADATA -> handleUpdateMetadata(body);
            case CONTROLLED_SHUTDOWN -> handleControlledShutdown(body);
            case PRODUCE -> handleProduce(body);
            case FETCH -> handleFetch(body);
            case LIST_OFFSETS -> handleListOffsets(body);
            case FIND_COORDINATOR -> handleFindCoordinator(body);
            case JOIN_GROUP -> handleJoinGroup(body, header.clientId());
            case SYNC_GROUP -> handleSyncGroup(body);
            case HEARTBEAT -> handleHeartbeat(body);
            case LEAVE_GROUP -> handleLeaveGroup(body);
            case OFFSET_COMMIT -> handleOffsetCommit(body);
            case OFFSET_FETCH -> handleOffsetFetch(body);
            case CREATE_TOPICS -> handleCreateTopics(body);
            case DELETE_TOPICS -> handleDeleteTopics(body);
            case DESCRIBE_GROUPS -> handleDescribeGroups(body);
            case LIST_GROUPS -> handleListGroups();
            case SASL_HANDSHAKE -> handleSaslHandshake(body, connState);
            case DELETE_RECORDS -> handleDeleteRecords(body);
            case CREATE_PARTITIONS -> handleCreatePartitions(body);
            case SASL_AUTHENTICATE -> handleSaslAuthenticate(body, connState);
            case DELETE_GROUPS -> handleDeleteGroups(body);
            case OFFSET_DELETE -> handleOffsetDelete(body);
            case DESCRIBE_CONFIGS -> handleDescribeConfigs(body);
            case ALTER_CONFIGS -> handleAlterConfigs(body);
            case INIT_PRODUCER_ID -> handleInitProducerId(body);
            case OFFSET_FOR_LEADER_EPOCH -> handleOffsetForLeaderEpoch(body);
            case ADD_PARTITIONS_TO_TXN -> handleAddPartitionsToTxn(body);
            case ADD_OFFSETS_TO_TXN -> handleAddOffsetsToTxn(body);
            case END_TXN -> handleEndTxn(body);
            case WRITE_TXN_MARKERS -> handleWriteTxnMarkers(body);
            case TXN_OFFSET_COMMIT -> handleTxnOffsetCommit(body);
            case ALTER_PARTITION_REASSIGNMENTS -> handleAlterPartitionReassignments(body);
            case LIST_PARTITION_REASSIGNMENTS -> handleListPartitionReassignments(body);
        };
    }

    private byte[] handleApiVersions() {
        List<ApiVersionsResponse.ApiVersion> versions = new ArrayList<>();
        for (ApiKey ak : ApiKey.values()) {
            versions.add(new ApiVersionsResponse.ApiVersion(ak.key(), ak.minVersion(), ak.maxVersion()));
        }
        return KafkaCodec.encodeApiVersionsResponse(
                new ApiVersionsResponse(KafkaErrors.NONE.code(), versions));
    }

    private byte[] handleMetadata(ByteBuffer body) {
        MetadataRequest req = KafkaCodec.decodeMetadataRequest(body);
        List<MetadataResponse.BrokerMetadata> brokers = List.of(
                new MetadataResponse.BrokerMetadata(brokerId, host, boundPort));

        Collection<String> topicsToDescribe = req.topics() != null ? req.topics() : topicPartitions.keySet();
        List<MetadataResponse.TopicMetadata> topicMetas = new ArrayList<>();
        for (String topic : topicsToDescribe) {
            List<Integer> parts = topicPartitions.get(topic);
            if (parts == null) {
                topicMetas.add(new MetadataResponse.TopicMetadata(
                        KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code(), topic, List.of()));
                continue;
            }
            List<MetadataResponse.PartitionMetadata> partMetas = new ArrayList<>();
            for (int p : parts) {
                partMetas.add(new MetadataResponse.PartitionMetadata(
                        KafkaErrors.NONE.code(), p, brokerId,
                        List.of(brokerId), List.of(brokerId)));
            }
            topicMetas.add(new MetadataResponse.TopicMetadata(KafkaErrors.NONE.code(), topic, partMetas));
        }
        return KafkaCodec.encodeMetadataResponse(new MetadataResponse(brokers, topicMetas));
    }

    private byte[] handleProduce(ByteBuffer body) {
        ProduceRequest req = KafkaCodec.decodeProduceRequest(body);
        List<ProduceResponse.TopicResponse> responses = new ArrayList<>();

        for (var td : req.topicData()) {
            List<ProduceResponse.PartitionResponse> partResps = new ArrayList<>();
            // Auto-create topic if needed
            if (!topicPartitions.containsKey(td.name())) {
                createTopic(td.name(), defaultPartitions);
            }
            for (var pd : td.partitionData()) {
                TopicPartition tp = new TopicPartition(td.name(), pd.index());
                PartitionLog log = partitionLogs.get(tp);
                if (log == null) {
                    partResps.add(new ProduceResponse.PartitionResponse(
                            pd.index(), KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code(), -1, -1));
                    continue;
                }

                // Check idempotency if producer ID is set
                if (pd.records() != null && pd.records().length > 0) {
                    try {
                        RecordBatch batch = RecordBatch.decode(pd.records());
                        if (batch.producerId() >= 0) {
                            short idempotentError = transactionManager.checkIdempotent(
                                    batch.producerId(), batch.producerEpoch(), tp,
                                    batch.baseSequence(), batch.records().size());
                            if (idempotentError != KafkaErrors.NONE.code()) {
                                partResps.add(new ProduceResponse.PartitionResponse(
                                        pd.index(), idempotentError, -1, -1));
                                continue;
                            }
                        }
                    } catch (Exception e) {
                        // Non-fatal — proceed with append
                        LOG.debug("Could not check idempotency: {}", e.getMessage());
                    }
                }

                long baseOffset = log.append(pd.records());
                partResps.add(new ProduceResponse.PartitionResponse(
                        pd.index(), KafkaErrors.NONE.code(), baseOffset, System.currentTimeMillis()));
            }
            responses.add(new ProduceResponse.TopicResponse(td.name(), partResps));
        }
        return KafkaCodec.encodeProduceResponse(new ProduceResponse(responses, 0));
    }

    private byte[] handleFetch(ByteBuffer body) {
        FetchRequest req = KafkaCodec.decodeFetchRequest(body);
        List<FetchResponse.TopicResponse> responses = new ArrayList<>();

        for (var tf : req.topics()) {
            List<FetchResponse.PartitionResponse> partResps = new ArrayList<>();
            for (var pf : tf.partitions()) {
                TopicPartition tp = new TopicPartition(tf.name(), pf.partition());
                PartitionLog log = partitionLogs.get(tp);
                if (log == null) {
                    partResps.add(new FetchResponse.PartitionResponse(
                            pf.partition(), KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code(), 0, null));
                    continue;
                }
                byte[] records = log.fetch(pf.fetchOffset(), pf.partitionMaxBytes());
                partResps.add(new FetchResponse.PartitionResponse(
                        pf.partition(), KafkaErrors.NONE.code(), log.highWatermark(),
                        records.length > 0 ? records : null));
            }
            responses.add(new FetchResponse.TopicResponse(tf.name(), partResps));
        }
        return KafkaCodec.encodeFetchResponse(new FetchResponse(0, responses));
    }

    private byte[] handleListOffsets(ByteBuffer body) {
        ListOffsetsRequest req = KafkaCodec.decodeListOffsetsRequest(body);
        List<ListOffsetsResponse.TopicResponse> responses = new ArrayList<>();

        for (var t : req.topics()) {
            List<ListOffsetsResponse.PartitionResponse> partResps = new ArrayList<>();
            for (var p : t.partitions()) {
                TopicPartition tp = new TopicPartition(t.name(), p.partitionIndex());
                PartitionLog log = partitionLogs.get(tp);
                if (log == null) {
                    partResps.add(new ListOffsetsResponse.PartitionResponse(
                            p.partitionIndex(), KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code(), -1, -1));
                    continue;
                }
                long offset = log.offsetForTimestamp(p.timestamp());
                partResps.add(new ListOffsetsResponse.PartitionResponse(
                        p.partitionIndex(), KafkaErrors.NONE.code(), -1, offset));
            }
            responses.add(new ListOffsetsResponse.TopicResponse(t.name(), partResps));
        }
        return KafkaCodec.encodeListOffsetsResponse(new ListOffsetsResponse(responses));
    }

    private byte[] handleFindCoordinator(ByteBuffer body) {
        KafkaCodec.decodeFindCoordinatorRequest(body);
        return KafkaCodec.encodeFindCoordinatorResponse(
                new FindCoordinatorResponse(KafkaErrors.NONE.code(), brokerId, host, boundPort));
    }

    private byte[] handleJoinGroup(ByteBuffer body, String clientId) {
        JoinGroupRequest req = KafkaCodec.decodeJoinGroupRequest(body);
        List<Map.Entry<String, byte[]>> protocols = new ArrayList<>();
        for (var p : req.protocols()) {
            protocols.add(Map.entry(p.name(), p.metadata()));
        }
        var result = groupCoordinator.joinGroup(
                req.groupId(), req.memberId(), req.protocolType(),
                req.sessionTimeoutMs(), protocols, clientId != null ? clientId : "unknown");

        List<JoinGroupResponse.Member> members = new ArrayList<>();
        for (var entry : result.memberMetadata().entrySet()) {
            members.add(new JoinGroupResponse.Member(entry.getKey(), entry.getValue()));
        }
        return KafkaCodec.encodeJoinGroupResponse(
                new JoinGroupResponse(result.errorCode(), result.generationId(),
                        result.protocolName(), result.leader(), result.memberId(), members));
    }

    private byte[] handleSyncGroup(ByteBuffer body) {
        SyncGroupRequest req = KafkaCodec.decodeSyncGroupRequest(body);
        Map<String, byte[]> assignments = new LinkedHashMap<>();
        for (var a : req.assignments()) {
            assignments.put(a.memberId(), a.assignment());
        }
        var result = groupCoordinator.syncGroup(
                req.groupId(), req.generationId(), req.memberId(), assignments);
        return KafkaCodec.encodeSyncGroupResponse(
                new SyncGroupResponse(result.getKey(), result.getValue()));
    }

    private byte[] handleHeartbeat(ByteBuffer body) {
        HeartbeatRequest req = KafkaCodec.decodeHeartbeatRequest(body);
        short errorCode = groupCoordinator.heartbeat(req.groupId(), req.generationId(), req.memberId());
        return KafkaCodec.encodeHeartbeatResponse(new HeartbeatResponse(errorCode));
    }

    private byte[] handleLeaveGroup(ByteBuffer body) {
        LeaveGroupRequest req = KafkaCodec.decodeLeaveGroupRequest(body);
        short errorCode = groupCoordinator.leaveGroup(req.groupId(), req.memberId());
        return KafkaCodec.encodeLeaveGroupResponse(new LeaveGroupResponse(errorCode));
    }

    private byte[] handleOffsetCommit(ByteBuffer body) {
        OffsetCommitRequest req = KafkaCodec.decodeOffsetCommitRequest(body);
        Map<TopicPartition, Long> offsets = new LinkedHashMap<>();
        List<OffsetCommitResponse.TopicResponse> responses = new ArrayList<>();

        for (var t : req.topics()) {
            List<OffsetCommitResponse.PartitionResponse> partResps = new ArrayList<>();
            for (var p : t.partitions()) {
                offsets.put(new TopicPartition(t.name(), p.partitionIndex()), p.committedOffset());
                partResps.add(new OffsetCommitResponse.PartitionResponse(
                        p.partitionIndex(), KafkaErrors.NONE.code()));
            }
            responses.add(new OffsetCommitResponse.TopicResponse(t.name(), partResps));
        }
        groupCoordinator.commitOffsets(req.groupId(), offsets);
        return KafkaCodec.encodeOffsetCommitResponse(new OffsetCommitResponse(responses));
    }

    private byte[] handleOffsetFetch(ByteBuffer body) {
        OffsetFetchRequest req = KafkaCodec.decodeOffsetFetchRequest(body);
        List<TopicPartition> partitions = new ArrayList<>();
        for (var t : req.topics()) {
            for (int idx : t.partitionIndexes()) {
                partitions.add(new TopicPartition(t.name(), idx));
            }
        }
        Map<TopicPartition, Long> offsets = groupCoordinator.fetchOffsets(req.groupId(), partitions);

        // Group results by topic
        Map<String, List<OffsetFetchResponse.PartitionResponse>> byTopic = new LinkedHashMap<>();
        for (var entry : offsets.entrySet()) {
            byTopic.computeIfAbsent(entry.getKey().topic(), k -> new ArrayList<>())
                    .add(new OffsetFetchResponse.PartitionResponse(
                            entry.getKey().partition(), entry.getValue(), null, KafkaErrors.NONE.code()));
        }
        List<OffsetFetchResponse.TopicResponse> responses = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            responses.add(new OffsetFetchResponse.TopicResponse(entry.getKey(), entry.getValue()));
        }
        return KafkaCodec.encodeOffsetFetchResponse(new OffsetFetchResponse(responses));
    }

    private byte[] handleCreateTopics(ByteBuffer body) {
        CreateTopicsRequest req = KafkaCodec.decodeCreateTopicsRequest(body);
        List<CreateTopicsResponse.TopicResult> results = new ArrayList<>();
        for (var t : req.topics()) {
            int numParts = t.numPartitions() > 0 ? t.numPartitions() : defaultPartitions;
            short errorCode = createTopic(t.name(), numParts);
            results.add(new CreateTopicsResponse.TopicResult(t.name(), errorCode));
        }
        return KafkaCodec.encodeCreateTopicsResponse(new CreateTopicsResponse(results));
    }

    private byte[] handleDeleteTopics(ByteBuffer body) {
        DeleteTopicsRequest req = KafkaCodec.decodeDeleteTopicsRequest(body);
        List<DeleteTopicsResponse.TopicResult> results = new ArrayList<>();
        for (String name : req.topicNames()) {
            short errorCode = deleteTopic(name);
            results.add(new DeleteTopicsResponse.TopicResult(name, errorCode));
        }
        return KafkaCodec.encodeDeleteTopicsResponse(new DeleteTopicsResponse(results));
    }

    private byte[] handleDescribeGroups(ByteBuffer body) {
        DescribeGroupsRequest req = KafkaCodec.decodeDescribeGroupsRequest(body);
        List<DescribeGroupsResponse.GroupDescription> descriptions = new ArrayList<>();
        for (String groupId : req.groups()) {
            var group = groupCoordinator.describeGroup(groupId);
            if (group == null) {
                descriptions.add(new DescribeGroupsResponse.GroupDescription(
                        KafkaErrors.NONE.code(), groupId, "Empty", "", "", List.of()));
            } else {
                List<DescribeGroupsResponse.MemberDescription> members = new ArrayList<>();
                for (var m : group.members.entrySet()) {
                    byte[] assignment = group.assignments.getOrDefault(m.getKey(), new byte[0]);
                    members.add(new DescribeGroupsResponse.MemberDescription(
                            m.getKey(), m.getValue().clientId(), "/127.0.0.1",
                            m.getValue().metadata(), assignment));
                }
                descriptions.add(new DescribeGroupsResponse.GroupDescription(
                        KafkaErrors.NONE.code(), groupId, group.state.name(),
                        group.protocolType, group.protocol, members));
            }
        }
        return KafkaCodec.encodeDescribeGroupsResponse(new DescribeGroupsResponse(descriptions));
    }

    private byte[] handleInitProducerId(ByteBuffer body) {
        InitProducerIdRequest req = KafkaCodec.decodeInitProducerIdRequest(body);
        var result = transactionManager.initProducerId(req.transactionalId());
        return KafkaCodec.encodeInitProducerIdResponse(
                new InitProducerIdResponse(result.errorCode(), result.producerId(), result.epoch()));
    }

    private byte[] handleAddPartitionsToTxn(ByteBuffer body) {
        AddPartitionsToTxnRequest req = KafkaCodec.decodeAddPartitionsToTxnRequest(body);
        List<TopicPartition> partitions = new ArrayList<>();
        for (var t : req.topics()) {
            for (int idx : t.partitionIndexes()) {
                partitions.add(new TopicPartition(t.name(), idx));
            }
        }
        Map<TopicPartition, Short> results = transactionManager.addPartitionsToTxn(
                req.transactionalId(), req.producerId(), req.producerEpoch(), partitions);

        // Group by topic
        Map<String, List<AddPartitionsToTxnResponse.PartitionResponse>> byTopic = new LinkedHashMap<>();
        for (var entry : results.entrySet()) {
            byTopic.computeIfAbsent(entry.getKey().topic(), k -> new ArrayList<>())
                    .add(new AddPartitionsToTxnResponse.PartitionResponse(
                            entry.getKey().partition(), entry.getValue()));
        }
        List<AddPartitionsToTxnResponse.TopicResponse> responses = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            responses.add(new AddPartitionsToTxnResponse.TopicResponse(entry.getKey(), entry.getValue()));
        }
        return KafkaCodec.encodeAddPartitionsToTxnResponse(new AddPartitionsToTxnResponse(responses));
    }

    private byte[] handleListGroups() {
        var groups = groupCoordinator.listGroups();
        List<ListGroupsResponse.GroupListing> listings = new ArrayList<>();
        for (var g : groups) {
            listings.add(new ListGroupsResponse.GroupListing(g.groupId(), g.protocolType(), g.state()));
        }
        return KafkaCodec.encodeListGroupsResponse(
                new ListGroupsResponse(KafkaErrors.NONE.code(), listings));
    }

    private byte[] handleDeleteRecords(ByteBuffer body) {
        DeleteRecordsRequest req = KafkaCodec.decodeDeleteRecordsRequest(body);
        List<DeleteRecordsResponse.TopicData> responses = new ArrayList<>();
        for (var t : req.topics()) {
            List<DeleteRecordsResponse.PartitionData> partResps = new ArrayList<>();
            for (var p : t.partitions()) {
                TopicPartition tp = new TopicPartition(t.name(), p.partitionIndex());
                PartitionLog log = partitionLogs.get(tp);
                if (log == null) {
                    partResps.add(new DeleteRecordsResponse.PartitionData(
                            p.partitionIndex(), -1, KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code()));
                    continue;
                }
                long lowWatermark = log.truncateBefore(p.offset());
                partResps.add(new DeleteRecordsResponse.PartitionData(
                        p.partitionIndex(), lowWatermark, KafkaErrors.NONE.code()));
            }
            responses.add(new DeleteRecordsResponse.TopicData(t.name(), partResps));
        }
        return KafkaCodec.encodeDeleteRecordsResponse(new DeleteRecordsResponse(responses));
    }

    private byte[] handleCreatePartitions(ByteBuffer body) {
        CreatePartitionsRequest req = KafkaCodec.decodeCreatePartitionsRequest(body);
        List<CreatePartitionsResponse.TopicResult> results = new ArrayList<>();
        for (var t : req.topics()) {
            List<Integer> existing = topicPartitions.get(t.name());
            if (existing == null) {
                results.add(new CreatePartitionsResponse.TopicResult(
                        t.name(), KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code()));
                continue;
            }
            int currentCount = existing.size();
            if (t.newCount() <= currentCount) {
                results.add(new CreatePartitionsResponse.TopicResult(
                        t.name(), KafkaErrors.INVALID_PARTITIONS.code()));
                continue;
            }
            for (int i = currentCount; i < t.newCount(); i++) {
                existing.add(i);
                partitionLogs.put(new TopicPartition(t.name(), i),
                        new PartitionLog(t.name(), i, storageFactory.create(t.name(), i)));
            }
            LOG.info("Increased partitions for topic '{}' from {} to {}", t.name(), currentCount, t.newCount());
            results.add(new CreatePartitionsResponse.TopicResult(t.name(), KafkaErrors.NONE.code()));
        }
        return KafkaCodec.encodeCreatePartitionsResponse(new CreatePartitionsResponse(results));
    }

    private byte[] handleDeleteGroups(ByteBuffer body) {
        DeleteGroupsRequest req = KafkaCodec.decodeDeleteGroupsRequest(body);
        List<DeleteGroupsResponse.GroupResult> results = new ArrayList<>();
        for (String groupId : req.groups()) {
            short errorCode = groupCoordinator.deleteGroup(groupId);
            results.add(new DeleteGroupsResponse.GroupResult(groupId, errorCode));
        }
        return KafkaCodec.encodeDeleteGroupsResponse(new DeleteGroupsResponse(results));
    }

    private byte[] handleOffsetDelete(ByteBuffer body) {
        OffsetDeleteRequest req = KafkaCodec.decodeOffsetDeleteRequest(body);
        List<TopicPartition> partitions = new ArrayList<>();
        for (var t : req.topics()) {
            for (var p : t.partitions()) {
                partitions.add(new TopicPartition(t.name(), p.partitionIndex()));
            }
        }
        Map<TopicPartition, Short> results = groupCoordinator.deleteOffsets(req.groupId(), partitions);

        // Group by topic
        Map<String, List<OffsetDeleteResponse.PartitionData>> byTopic = new LinkedHashMap<>();
        for (var entry : results.entrySet()) {
            byTopic.computeIfAbsent(entry.getKey().topic(), k -> new ArrayList<>())
                    .add(new OffsetDeleteResponse.PartitionData(
                            entry.getKey().partition(), entry.getValue()));
        }
        List<OffsetDeleteResponse.TopicData> topics = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            topics.add(new OffsetDeleteResponse.TopicData(entry.getKey(), entry.getValue()));
        }
        return KafkaCodec.encodeOffsetDeleteResponse(
                new OffsetDeleteResponse(KafkaErrors.NONE.code(), topics));
    }

    private byte[] handleDescribeConfigs(ByteBuffer body) {
        DescribeConfigsRequest req = KafkaCodec.decodeDescribeConfigsRequest(body);
        List<DescribeConfigsResponse.ResourceResponse> resources = new ArrayList<>();
        for (var r : req.resources()) {
            var configEntries = configManager.describeConfigs(r.resourceType(), r.resourceName());
            List<DescribeConfigsResponse.ConfigEntry> entries = new ArrayList<>();
            for (var entry : configEntries.entrySet()) {
                entries.add(new DescribeConfigsResponse.ConfigEntry(
                        entry.getKey(), entry.getValue().value(),
                        entry.getValue().readOnly(), entry.getValue().sensitive()));
            }
            resources.add(new DescribeConfigsResponse.ResourceResponse(
                    KafkaErrors.NONE.code(), r.resourceName(), entries));
        }
        return KafkaCodec.encodeDescribeConfigsResponse(new DescribeConfigsResponse(resources));
    }

    private byte[] handleAlterConfigs(ByteBuffer body) {
        AlterConfigsRequest req = KafkaCodec.decodeAlterConfigsRequest(body);
        List<AlterConfigsResponse.ResourceResponse> resources = new ArrayList<>();
        for (var r : req.resources()) {
            Map<String, String> configMap = new LinkedHashMap<>();
            for (var c : r.configs()) {
                configMap.put(c.name(), c.value());
            }
            short errorCode = configManager.alterConfigs(r.resourceType(), r.resourceName(), configMap);
            resources.add(new AlterConfigsResponse.ResourceResponse(errorCode, r.resourceName()));
        }
        return KafkaCodec.encodeAlterConfigsResponse(new AlterConfigsResponse(resources));
    }

    private byte[] handleEndTxn(ByteBuffer body) {
        EndTxnRequest req = KafkaCodec.decodeEndTxnRequest(body);
        short errorCode = transactionManager.endTransaction(
                req.transactionalId(), req.producerId(), req.producerEpoch(), req.committed());
        return KafkaCodec.encodeEndTxnResponse(new EndTxnResponse(errorCode));
    }

    private byte[] handleAddOffsetsToTxn(ByteBuffer body) {
        AddOffsetsToTxnRequest req = KafkaCodec.decodeAddOffsetsToTxnRequest(body);
        short errorCode = transactionManager.addOffsetsToTxn(
                req.transactionalId(), req.producerId(), req.producerEpoch(), req.groupId());
        return KafkaCodec.encodeAddOffsetsToTxnResponse(new AddOffsetsToTxnResponse(errorCode));
    }

    private byte[] handleTxnOffsetCommit(ByteBuffer body) {
        TxnOffsetCommitRequest req = KafkaCodec.decodeTxnOffsetCommitRequest(body);

        // Collect offsets into a map
        Map<TopicPartition, Long> offsets = new LinkedHashMap<>();
        List<TxnOffsetCommitResponse.TopicData> responseTopics = new ArrayList<>();

        for (var t : req.topics()) {
            List<TxnOffsetCommitResponse.PartitionData> partResps = new ArrayList<>();
            for (var p : t.partitions()) {
                offsets.put(new TopicPartition(t.name(), p.partitionIndex()), p.committedOffset());
                partResps.add(new TxnOffsetCommitResponse.PartitionData(
                        p.partitionIndex(), KafkaErrors.NONE.code()));
            }
            responseTopics.add(new TxnOffsetCommitResponse.TopicData(t.name(), partResps));
        }

        // Store offsets as pending in the transaction manager
        short errorCode = transactionManager.addPendingTxnOffsets(
                req.transactionalId(), req.producerId(), req.producerEpoch(), offsets);

        // If there was an error, override the per-partition error codes
        if (errorCode != KafkaErrors.NONE.code()) {
            responseTopics = new ArrayList<>();
            for (var t : req.topics()) {
                List<TxnOffsetCommitResponse.PartitionData> partResps = new ArrayList<>();
                for (var p : t.partitions()) {
                    partResps.add(new TxnOffsetCommitResponse.PartitionData(
                            p.partitionIndex(), errorCode));
                }
                responseTopics.add(new TxnOffsetCommitResponse.TopicData(t.name(), partResps));
            }
        }

        return KafkaCodec.encodeTxnOffsetCommitResponse(new TxnOffsetCommitResponse(responseTopics));
    }

    private byte[] handleLeaderAndIsr(ByteBuffer body) {
        LeaderAndIsrRequest req = KafkaCodec.decodeLeaderAndIsrRequest(body);
        List<LeaderAndIsrResponse.PartitionResult> results = new ArrayList<>();
        for (var ps : req.partitionStates()) {
            TopicPartition tp = new TopicPartition(ps.topic(), ps.partition());
            replicaManager.updateLeaderAndIsr(tp, ps.leader(), ps.leaderEpoch(), ps.isr());
            // Auto-create topic/partition log if needed
            if (!topicPartitions.containsKey(ps.topic())) {
                createTopic(ps.topic(), ps.partition() + 1);
            } else if (partitionLogs.get(tp) == null) {
                List<Integer> parts = topicPartitions.get(ps.topic());
                if (parts != null && !parts.contains(ps.partition())) {
                    parts.add(ps.partition());
                    partitionLogs.put(tp, new PartitionLog(ps.topic(), ps.partition(),
                            storageFactory.create(ps.topic(), ps.partition())));
                }
            }
            results.add(new LeaderAndIsrResponse.PartitionResult(ps.topic(), ps.partition(), KafkaErrors.NONE.code()));
        }
        return KafkaCodec.encodeLeaderAndIsrResponse(
                new LeaderAndIsrResponse(KafkaErrors.NONE.code(), results));
    }

    private byte[] handleStopReplica(ByteBuffer body) {
        StopReplicaRequest req = KafkaCodec.decodeStopReplicaRequest(body);
        List<StopReplicaResponse.PartitionResult> results = new ArrayList<>();
        for (var p : req.partitions()) {
            TopicPartition tp = new TopicPartition(p.topic(), p.partition());
            replicaManager.stopReplica(tp, req.deletePartitions());
            if (req.deletePartitions()) {
                partitionLogs.remove(tp);
            }
            results.add(new StopReplicaResponse.PartitionResult(p.topic(), p.partition(), KafkaErrors.NONE.code()));
        }
        return KafkaCodec.encodeStopReplicaResponse(
                new StopReplicaResponse(KafkaErrors.NONE.code(), results));
    }

    private byte[] handleUpdateMetadata(ByteBuffer body) {
        UpdateMetadataRequest req = KafkaCodec.decodeUpdateMetadataRequest(body);
        List<Node> liveBrokers = new ArrayList<>();
        for (var b : req.liveBrokers()) {
            liveBrokers.add(new Node(b.brokerId(), b.host(), b.port()));
        }
        Map<TopicPartition, int[]> assignments = new LinkedHashMap<>();
        for (var ps : req.partitionStates()) {
            TopicPartition tp = new TopicPartition(ps.topic(), ps.partition());
            assignments.put(tp, ps.replicas().stream().mapToInt(Integer::intValue).toArray());
        }
        clusterMetadata = new ClusterMetadata(liveBrokers, assignments);
        return KafkaCodec.encodeUpdateMetadataResponse(
                new UpdateMetadataResponse(KafkaErrors.NONE.code()));
    }

    private byte[] handleControlledShutdown(ByteBuffer body) {
        ControlledShutdownRequest req = KafkaCodec.decodeControlledShutdownRequest(body);
        // Find partitions where the shutting-down broker is leader
        List<ControlledShutdownResponse.TopicPartitionData> remaining = new ArrayList<>();
        for (var entry : replicaManager.allReplicas().entrySet()) {
            if (entry.getValue().leaderBrokerId() == req.brokerId()) {
                remaining.add(new ControlledShutdownResponse.TopicPartitionData(
                        entry.getKey().topic(), entry.getKey().partition()));
            }
        }
        return KafkaCodec.encodeControlledShutdownResponse(
                new ControlledShutdownResponse(KafkaErrors.NONE.code(), remaining));
    }

    private byte[] handleOffsetForLeaderEpoch(ByteBuffer body) {
        OffsetForLeaderEpochRequest req = KafkaCodec.decodeOffsetForLeaderEpochRequest(body);
        List<OffsetForLeaderEpochResponse.TopicData> responseTopics = new ArrayList<>();
        for (var t : req.topics()) {
            List<OffsetForLeaderEpochResponse.PartitionData> parts = new ArrayList<>();
            for (var p : t.partitions()) {
                TopicPartition tp = new TopicPartition(t.topic(), p.partition());
                long endOffset = replicaManager.offsetForLeaderEpoch(tp, p.leaderEpoch());
                int epoch = replicaManager.leaderEpoch(tp);
                short errorCode = endOffset >= 0 ? KafkaErrors.NONE.code()
                        : KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code();
                parts.add(new OffsetForLeaderEpochResponse.PartitionData(
                        errorCode, p.partition(), epoch, Math.max(endOffset, 0)));
            }
            responseTopics.add(new OffsetForLeaderEpochResponse.TopicData(t.topic(), parts));
        }
        return KafkaCodec.encodeOffsetForLeaderEpochResponse(
                new OffsetForLeaderEpochResponse(responseTopics));
    }

    private byte[] handleWriteTxnMarkers(ByteBuffer body) {
        WriteTxnMarkersRequest req = KafkaCodec.decodeWriteTxnMarkersRequest(body);
        List<WriteTxnMarkersResponse.MarkerResult> markerResults = new ArrayList<>();
        for (var marker : req.markers()) {
            Map<String, List<WriteTxnMarkersResponse.PartitionResult>> byTopic = new LinkedHashMap<>();
            for (var p : marker.partitions()) {
                TopicPartition tp = new TopicPartition(p.topic(), p.partition());
                PartitionLog log = partitionLogs.get(tp);
                short errorCode;
                if (log != null) {
                    // Append a zero-length marker record to the partition
                    try {
                        log.append(new byte[0]);
                    } catch (Exception e) {
                        // Zero-length append may fail; that's OK for a marker
                    }
                    errorCode = KafkaErrors.NONE.code();
                } else {
                    errorCode = KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code();
                }
                byTopic.computeIfAbsent(p.topic(), k -> new ArrayList<>())
                        .add(new WriteTxnMarkersResponse.PartitionResult(p.partition(), errorCode));
            }
            List<WriteTxnMarkersResponse.TopicResult> topics = new ArrayList<>();
            for (var entry : byTopic.entrySet()) {
                topics.add(new WriteTxnMarkersResponse.TopicResult(entry.getKey(), entry.getValue()));
            }
            markerResults.add(new WriteTxnMarkersResponse.MarkerResult(marker.producerId(), topics));
        }
        return KafkaCodec.encodeWriteTxnMarkersResponse(new WriteTxnMarkersResponse(markerResults));
    }

    private byte[] handleAlterPartitionReassignments(ByteBuffer body) {
        AlterPartitionReassignmentsRequest req = KafkaCodec.decodeAlterPartitionReassignmentsRequest(body);
        List<AlterPartitionReassignmentsResponse.TopicResult> topicResults = new ArrayList<>();
        for (var t : req.topics()) {
            List<AlterPartitionReassignmentsResponse.PartitionResult> partResults = new ArrayList<>();
            for (var p : t.partitions()) {
                TopicPartition tp = new TopicPartition(t.topic(), p.partition());
                if (p.replicas() == null) {
                    // Cancel reassignment
                    reassignments.remove(tp);
                } else {
                    reassignments.put(tp, List.copyOf(p.replicas()));
                }
                partResults.add(new AlterPartitionReassignmentsResponse.PartitionResult(
                        p.partition(), KafkaErrors.NONE.code()));
            }
            topicResults.add(new AlterPartitionReassignmentsResponse.TopicResult(t.topic(), partResults));
        }
        return KafkaCodec.encodeAlterPartitionReassignmentsResponse(
                new AlterPartitionReassignmentsResponse(KafkaErrors.NONE.code(), topicResults));
    }

    private byte[] handleListPartitionReassignments(ByteBuffer body) {
        ListPartitionReassignmentsRequest req = KafkaCodec.decodeListPartitionReassignmentsRequest(body);
        Map<String, List<ListPartitionReassignmentsResponse.PartitionResult>> byTopic = new LinkedHashMap<>();
        for (var entry : reassignments.entrySet()) {
            TopicPartition tp = entry.getKey();
            // Filter by requested topics if specified
            if (req.topics() != null) {
                boolean match = false;
                for (var t : req.topics()) {
                    if (t.topic().equals(tp.topic()) && t.partitions().contains(tp.partition())) {
                        match = true;
                        break;
                    }
                }
                if (!match) continue;
            }
            byTopic.computeIfAbsent(tp.topic(), k -> new ArrayList<>())
                    .add(new ListPartitionReassignmentsResponse.PartitionResult(
                            tp.partition(), entry.getValue(), List.of(), List.of()));
        }
        List<ListPartitionReassignmentsResponse.TopicResult> topics = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            topics.add(new ListPartitionReassignmentsResponse.TopicResult(entry.getKey(), entry.getValue()));
        }
        return KafkaCodec.encodeListPartitionReassignmentsResponse(
                new ListPartitionReassignmentsResponse(KafkaErrors.NONE.code(), topics));
    }

    private byte[] handleSaslHandshake(ByteBuffer body, ConnectionState connState) {
        SaslHandshakeRequest req = KafkaCodec.decodeSaslHandshakeRequest(body);
        List<String> mechanisms = new ArrayList<>(enabledMechanisms);

        if (!enabledMechanisms.contains(req.mechanism())) {
            return KafkaCodec.encodeSaslHandshakeResponse(
                    new SaslHandshakeResponse(KafkaErrors.UNSUPPORTED_SASL_MECHANISM.code(), mechanisms));
        }

        // Create the appropriate mechanism instance
        SaslMechanism mechanism = switch (req.mechanism()) {
            case "PLAIN" -> new PlainSaslServer(credentialStore);
            case "SCRAM-SHA-256" -> new ScramSha256Server(credentialStore);
            default -> null;
        };
        connState.currentMechanism = mechanism;
        connState.authenticated = false;

        return KafkaCodec.encodeSaslHandshakeResponse(
                new SaslHandshakeResponse(KafkaErrors.NONE.code(), mechanisms));
    }

    private byte[] handleSaslAuthenticate(ByteBuffer body, ConnectionState connState) {
        SaslAuthenticateRequest req = KafkaCodec.decodeSaslAuthenticateRequest(body);

        if (connState.currentMechanism == null) {
            return KafkaCodec.encodeSaslAuthenticateResponse(
                    new SaslAuthenticateResponse(KafkaErrors.ILLEGAL_SASL_STATE.code(), new byte[0], 0));
        }

        try {
            byte[] responseBytes = connState.currentMechanism.evaluateResponse(req.authBytes());
            if (connState.currentMechanism.isComplete()) {
                connState.authenticated = true;
                LOG.info("SASL authentication successful for user: {}",
                        connState.currentMechanism.authenticatedUser());
            }
            return KafkaCodec.encodeSaslAuthenticateResponse(
                    new SaslAuthenticateResponse(KafkaErrors.NONE.code(), responseBytes, 0));
        } catch (AuthenticationException e) {
            LOG.debug("SASL authentication failed: {}", e.getMessage());
            return KafkaCodec.encodeSaslAuthenticateResponse(
                    new SaslAuthenticateResponse(KafkaErrors.ILLEGAL_SASL_STATE.code(),
                            e.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8), 0));
        }
    }

    // --- I/O helpers ---

    private int readFully(SocketChannel channel, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = channel.read(buf);
            if (n < 0) return -1;
        }
        return buf.position();
    }

    private void writeFully(SocketChannel channel, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            try {
                if (serverChannel != null) serverChannel.close();
            } catch (IOException e) {
                LOG.debug("Error closing server channel", e);
            }
            executor.close();
            LOG.info("Kafka broker stopped");
        }
    }
}
