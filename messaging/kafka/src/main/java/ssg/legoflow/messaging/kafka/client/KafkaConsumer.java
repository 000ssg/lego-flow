package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.common.*;
import ssg.legoflow.messaging.kafka.broker.ConsumerGroupCoordinator;
import ssg.legoflow.messaging.kafka.broker.PartitionAssigner;
import ssg.legoflow.messaging.kafka.broker.RangeAssigner;
import ssg.legoflow.messaging.kafka.broker.StickyAssigner;
import ssg.legoflow.messaging.kafka.protocol.*;
import ssg.legoflow.messaging.kafka.record.RecordBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka consumer client with group membership support.
 *
 * <p>Features:
 * <ul>
 *   <li>Consumer group join/sync/heartbeat</li>
 *   <li>Partition assignment</li>
 *   <li>Offset management (auto-commit or manual)</li>
 *   <li>Seek to offset/beginning/end</li>
 *   <li>Poll loop</li>
 *   <li>Rebalance listener</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class KafkaConsumer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumer.class);

    private final KafkaConnection connection;
    private final String groupId;
    private final List<String> subscribedTopics = new ArrayList<>();
    private final boolean autoCommit;
    private final long autoCommitIntervalMs;
    private final int sessionTimeoutMs;
    private final int maxPollRecords;

    // Group state
    private volatile String memberId = "";
    private volatile int generationId = -1;
    private volatile boolean isLeader = false;

    // Partition assignments and offsets
    private final Map<TopicPartition, Long> currentPositions = new ConcurrentHashMap<>();
    private final Set<TopicPartition> assignedPartitions = ConcurrentHashMap.newKeySet();

    // Heartbeat
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("kafka-heartbeat-", 0).factory());
    private volatile ScheduledFuture<?> heartbeatFuture;

    // Auto-commit
    private volatile long lastCommitTime = System.currentTimeMillis();

    // Assignment strategy
    private volatile String assignmentStrategy = "range";

    // State
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile RebalanceListener rebalanceListener;

    /**
     * Creates a new Kafka consumer.
     *
     * @param host                  the broker host
     * @param port                  the broker port
     * @param clientId              the client ID
     * @param groupId               the consumer group ID
     * @param autoCommit            whether to auto-commit offsets
     * @param autoCommitIntervalMs  the auto-commit interval
     * @param sessionTimeoutMs      the session timeout
     * @param maxPollRecords        the max records per poll
     */
    public KafkaConsumer(String host, int port, String clientId, String groupId,
                         boolean autoCommit, long autoCommitIntervalMs,
                         int sessionTimeoutMs, int maxPollRecords) {
        this.connection = new KafkaConnection(host, port, clientId);
        this.groupId = groupId;
        this.autoCommit = autoCommit;
        this.autoCommitIntervalMs = autoCommitIntervalMs;
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.maxPollRecords = maxPollRecords;
    }

    /**
     * Creates a consumer with defaults.
     *
     * @param host     the broker host
     * @param port     the broker port
     * @param clientId the client ID
     * @param groupId  the consumer group ID
     */
    public KafkaConsumer(String host, int port, String clientId, String groupId) {
        this(host, port, clientId, groupId, true, 5000, 10000, 500);
    }

    /**
     * Sets the rebalance listener.
     *
     * @param listener the listener
     */
    public void setRebalanceListener(RebalanceListener listener) {
        this.rebalanceListener = listener;
    }

    /**
     * Sets the partition assignment strategy.
     *
     * <p>Supported strategies:
     * <ul>
     *   <li>{@code "range"} — default range-based assignment</li>
     *   <li>{@code "sticky"} — sticky assignment that minimizes partition movement</li>
     *   <li>{@code "cooperative-sticky"} — cooperative sticky rebalance (KIP-429):
     *       only revokes actually-moved partitions instead of all partitions</li>
     * </ul>
     *
     * @param strategy the assignment strategy name
     */
    public void setAssignmentStrategy(String strategy) {
        this.assignmentStrategy = strategy;
    }

    /**
     * Returns the current assignment strategy.
     *
     * @return the assignment strategy name
     */
    public String assignmentStrategy() {
        return assignmentStrategy;
    }

    /**
     * Subscribes to topics and joins the consumer group.
     *
     * @param topics the topics to subscribe to
     * @throws IOException if subscription fails
     */
    public void subscribe(List<String> topics) throws IOException {
        connection.connect();
        subscribedTopics.clear();
        subscribedTopics.addAll(topics);
        joinGroup();
    }

    /**
     * Polls for records.
     *
     * @param timeoutMs the poll timeout in milliseconds
     * @return the consumed records
     * @throws IOException if the poll fails
     */
    public List<ConsumerRecord> poll(long timeoutMs) throws IOException {
        if (closed.get()) throw new IllegalStateException("Consumer is closed");

        // Auto-commit if needed
        if (autoCommit && System.currentTimeMillis() - lastCommitTime > autoCommitIntervalMs) {
            commitSync();
        }

        if (assignedPartitions.isEmpty()) {
            return List.of();
        }

        // Build fetch request
        Map<String, List<FetchRequest.PartitionFetch>> byTopic = new LinkedHashMap<>();
        for (TopicPartition tp : assignedPartitions) {
            long offset = currentPositions.getOrDefault(tp, 0L);
            byTopic.computeIfAbsent(tp.topic(), k -> new ArrayList<>())
                    .add(new FetchRequest.PartitionFetch(tp.partition(), offset, 1048576));
        }
        List<FetchRequest.TopicFetch> topics = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            topics.add(new FetchRequest.TopicFetch(entry.getKey(), entry.getValue()));
        }

        FetchRequest request = new FetchRequest((int) timeoutMs, 1, 10485760, topics);
        byte[] payload = KafkaCodec.encodeFetchRequest(request);
        ByteBuffer resp = connection.sendAndReceive(ApiKey.FETCH.key(), (short) 0, payload);
        FetchResponse response = KafkaCodec.decodeFetchResponse(resp);

        List<ConsumerRecord> records = new ArrayList<>();
        for (var tr : response.topics()) {
            for (var pr : tr.partitions()) {
                if (pr.errorCode() != KafkaErrors.NONE.code()) continue;
                if (pr.records() == null || pr.records().length == 0) continue;

                // Decode record batches
                ByteBuffer recBuf = ByteBuffer.wrap(pr.records());
                while (recBuf.hasRemaining() && records.size() < maxPollRecords) {
                    try {
                        RecordBatch batch = RecordBatch.decode(recBuf);
                        for (var rec : batch.records()) {
                            long offset = batch.baseOffset() + rec.offsetDelta();
                            records.add(new ConsumerRecord(
                                    tr.name(), pr.partitionIndex(), offset,
                                    rec.key(), rec.value(), rec.headers()));

                            // Update position
                            TopicPartition tp = new TopicPartition(tr.name(), pr.partitionIndex());
                            currentPositions.put(tp, offset + 1);
                        }
                    } catch (Exception e) {
                        LOG.debug("Error decoding record batch: {}", e.getMessage());
                        break;
                    }
                }
            }
        }

        return records;
    }

    /**
     * Commits current offsets synchronously.
     *
     * @throws IOException if the commit fails
     */
    public void commitSync() throws IOException {
        if (currentPositions.isEmpty()) return;

        Map<String, List<OffsetCommitRequest.PartitionOffset>> byTopic = new LinkedHashMap<>();
        for (var entry : currentPositions.entrySet()) {
            byTopic.computeIfAbsent(entry.getKey().topic(), k -> new ArrayList<>())
                    .add(new OffsetCommitRequest.PartitionOffset(
                            entry.getKey().partition(), entry.getValue(), null));
        }
        List<OffsetCommitRequest.TopicOffsets> topics = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            topics.add(new OffsetCommitRequest.TopicOffsets(entry.getKey(), entry.getValue()));
        }

        OffsetCommitRequest request = new OffsetCommitRequest(groupId, generationId, memberId, topics);
        byte[] payload = KafkaCodec.encodeOffsetCommitRequest(request);
        connection.sendAndReceive(ApiKey.OFFSET_COMMIT.key(), (short) 0, payload);
        lastCommitTime = System.currentTimeMillis();
    }

    /**
     * Seeks to a specific offset for a partition.
     *
     * @param tp     the topic-partition
     * @param offset the offset to seek to
     */
    public void seek(TopicPartition tp, long offset) {
        currentPositions.put(tp, offset);
    }

    /**
     * Seeks to the beginning of all assigned partitions.
     */
    public void seekToBeginning() {
        for (TopicPartition tp : assignedPartitions) {
            currentPositions.put(tp, 0L);
        }
    }

    /**
     * Returns the current position for a partition.
     *
     * @param tp the topic-partition
     * @return the current position, or -1 if not assigned
     */
    public long position(TopicPartition tp) {
        return currentPositions.getOrDefault(tp, -1L);
    }

    /**
     * Returns the assigned partitions.
     *
     * @return the assigned partitions
     */
    public Set<TopicPartition> assignment() {
        return Collections.unmodifiableSet(assignedPartitions);
    }

    /**
     * Returns the member ID.
     *
     * @return the member ID
     */
    public String memberId() {
        return memberId;
    }

    /**
     * Returns the generation ID.
     *
     * @return the generation ID
     */
    public int generationId() {
        return generationId;
    }

    // --- Group protocol ---

    private void joinGroup() throws IOException {
        // Encode subscription metadata
        byte[] metadata = ConsumerGroupCoordinator.encodeSubscription(subscribedTopics);

        JoinGroupRequest request = new JoinGroupRequest(
                groupId, sessionTimeoutMs, sessionTimeoutMs, memberId, "consumer",
                List.of(new JoinGroupRequest.Protocol(assignmentStrategy, metadata)));

        byte[] payload = KafkaCodec.encodeJoinGroupRequest(request);
        ByteBuffer resp = connection.sendAndReceive(ApiKey.JOIN_GROUP.key(), (short) 0, payload);
        JoinGroupResponse response = KafkaCodec.decodeJoinGroupResponse(resp);

        if (response.errorCode() != KafkaErrors.NONE.code()) {
            throw new IOException("JoinGroup failed: " + KafkaErrors.forCode(response.errorCode()));
        }

        this.memberId = response.memberId();
        this.generationId = response.generationId();
        this.isLeader = response.leader().equals(memberId);

        LOG.info("Joined group '{}': memberId={}, generationId={}, isLeader={}",
                groupId, memberId, generationId, isLeader);

        // Sync group
        syncGroup(response);

        // Start heartbeat
        startHeartbeat();

        // Fetch committed offsets
        fetchCommittedOffsets();
    }

    private void syncGroup(JoinGroupResponse joinResponse) throws IOException {
        List<SyncGroupRequest.Assignment> assignments = new ArrayList<>();

        if (isLeader) {
            // Leader performs partition assignment (range assignor)
            assignments = assignPartitions(joinResponse);
        }

        SyncGroupRequest request = new SyncGroupRequest(groupId, generationId, memberId, assignments);
        byte[] payload = KafkaCodec.encodeSyncGroupRequest(request);
        ByteBuffer resp = connection.sendAndReceive(ApiKey.SYNC_GROUP.key(), (short) 0, payload);
        SyncGroupResponse response = KafkaCodec.decodeSyncGroupResponse(resp);

        if (response.errorCode() != KafkaErrors.NONE.code()) {
            throw new IOException("SyncGroup failed: " + KafkaErrors.forCode(response.errorCode()));
        }

        // Parse assignment
        Set<TopicPartition> oldPartitions = new LinkedHashSet<>(assignedPartitions);
        List<TopicPartition> newPartitions = ConsumerGroupCoordinator.decodeAssignment(response.assignment());
        Set<TopicPartition> newPartitionSet = new LinkedHashSet<>(newPartitions);

        if ("cooperative-sticky".equals(assignmentStrategy)) {
            // Cooperative rebalance: only revoke partitions that actually moved
            Set<TopicPartition> revoked = new LinkedHashSet<>(oldPartitions);
            revoked.removeAll(newPartitionSet);

            Set<TopicPartition> added = new LinkedHashSet<>(newPartitionSet);
            added.removeAll(oldPartitions);

            // Update assignments
            assignedPartitions.clear();
            assignedPartitions.addAll(newPartitions);

            // Initialize positions for new partitions
            for (TopicPartition tp : newPartitions) {
                currentPositions.putIfAbsent(tp, 0L);
            }

            // Notify listener — only for actually changed partitions
            if (rebalanceListener != null) {
                if (!revoked.isEmpty()) {
                    rebalanceListener.onPartitionsRevoked(revoked);
                }
                if (!added.isEmpty()) {
                    rebalanceListener.onPartitionsAssigned(added);
                }
            }
        } else {
            // Eager rebalance: revoke all, assign all (existing behavior)
            assignedPartitions.clear();
            assignedPartitions.addAll(newPartitions);

            // Initialize positions for new partitions
            for (TopicPartition tp : newPartitions) {
                currentPositions.putIfAbsent(tp, 0L);
            }

            // Notify listener
            if (rebalanceListener != null) {
                if (!oldPartitions.isEmpty()) {
                    rebalanceListener.onPartitionsRevoked(oldPartitions);
                }
                if (!newPartitions.isEmpty()) {
                    rebalanceListener.onPartitionsAssigned(newPartitions);
                }
            }
        }

        LOG.info("Assigned partitions: {}", assignedPartitions);
    }

    private List<SyncGroupRequest.Assignment> assignPartitions(JoinGroupResponse joinResponse) throws IOException {
        // Fetch metadata for subscribed topics
        byte[] metaPayload = KafkaCodec.encodeMetadataRequest(new MetadataRequest(subscribedTopics));
        ByteBuffer metaResp = connection.sendAndReceive(ApiKey.METADATA.key(), (short) 0, metaPayload);
        MetadataResponse metadata = KafkaCodec.decodeMetadataResponse(metaResp);

        // Collect all topic-partitions
        List<TopicPartition> allPartitions = new ArrayList<>();
        for (var t : metadata.topics()) {
            if (t.errorCode() == KafkaErrors.NONE.code()) {
                for (var p : t.partitions()) {
                    allPartitions.add(new TopicPartition(t.name(), p.partitionIndex()));
                }
            }
        }

        List<String> memberIds = joinResponse.members().stream()
                .map(JoinGroupResponse.Member::memberId).sorted().toList();

        // Build current assignment from member metadata (for sticky/cooperative)
        Map<String, List<TopicPartition>> currentAssignment = new LinkedHashMap<>();
        for (String mid : memberIds) {
            currentAssignment.put(mid, new ArrayList<>()); // default empty
        }

        // Select assigner based on strategy
        PartitionAssigner assigner = switch (assignmentStrategy) {
            case "sticky", "cooperative-sticky" -> new StickyAssigner();
            default -> new RangeAssigner();
        };

        Map<String, List<TopicPartition>> memberPartitions = assigner.assign(memberIds, allPartitions, currentAssignment);

        // Encode assignments
        List<SyncGroupRequest.Assignment> assignments = new ArrayList<>();
        for (var entry : memberPartitions.entrySet()) {
            byte[] assignmentBytes = ConsumerGroupCoordinator.encodeAssignment(entry.getValue());
            assignments.add(new SyncGroupRequest.Assignment(entry.getKey(), assignmentBytes));
        }
        return assignments;
    }

    private void fetchCommittedOffsets() throws IOException {
        if (assignedPartitions.isEmpty()) return;

        Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
        for (TopicPartition tp : assignedPartitions) {
            byTopic.computeIfAbsent(tp.topic(), k -> new ArrayList<>()).add(tp.partition());
        }
        List<OffsetFetchRequest.TopicPartitions> topics = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            topics.add(new OffsetFetchRequest.TopicPartitions(entry.getKey(), entry.getValue()));
        }

        byte[] payload = KafkaCodec.encodeOffsetFetchRequest(new OffsetFetchRequest(groupId, topics));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.OFFSET_FETCH.key(), (short) 0, payload);
        OffsetFetchResponse response = KafkaCodec.decodeOffsetFetchResponse(resp);

        for (var t : response.topics()) {
            for (var p : t.partitions()) {
                if (p.committedOffset() >= 0) {
                    currentPositions.put(new TopicPartition(t.name(), p.partitionIndex()),
                            p.committedOffset());
                }
            }
        }
    }

    private void startHeartbeat() {
        if (heartbeatFuture != null) heartbeatFuture.cancel(false);
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (IOException e) {
                LOG.warn("Heartbeat failed: {}", e.getMessage());
            }
        }, sessionTimeoutMs / 3, sessionTimeoutMs / 3, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() throws IOException {
        byte[] payload = KafkaCodec.encodeHeartbeatRequest(
                new HeartbeatRequest(groupId, generationId, memberId));
        ByteBuffer resp = connection.sendAndReceive(ApiKey.HEARTBEAT.key(), (short) 0, payload);
        HeartbeatResponse response = KafkaCodec.decodeHeartbeatResponse(resp);

        if (response.errorCode() == KafkaErrors.REBALANCE_IN_PROGRESS.code()) {
            LOG.info("Rebalance triggered, rejoining group");
            joinGroup();
        } else if (response.errorCode() != KafkaErrors.NONE.code()) {
            LOG.warn("Heartbeat error: {}", KafkaErrors.forCode(response.errorCode()));
        }
    }

    /**
     * Leaves the consumer group and closes the consumer.
     *
     * @throws IOException if the leave request fails
     */
    public void leaveGroup() throws IOException {
        if (!memberId.isEmpty()) {
            byte[] payload = KafkaCodec.encodeLeaveGroupRequest(
                    new LeaveGroupRequest(groupId, memberId));
            connection.sendAndReceive(ApiKey.LEAVE_GROUP.key(), (short) 0, payload);
            LOG.info("Left group '{}'", groupId);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (heartbeatFuture != null) heartbeatFuture.cancel(false);
            heartbeatExecutor.close();
            try {
                if (autoCommit) commitSync();
            } catch (IOException e) {
                LOG.debug("Error committing on close", e);
            }
            try {
                leaveGroup();
            } catch (IOException e) {
                LOG.debug("Error leaving group on close", e);
            }
            connection.close();
        }
    }
}
