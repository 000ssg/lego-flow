package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.codec.KafkaCodec;
import ssg.legoflow.messaging.kafka.common.ApiKey;
import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import ssg.legoflow.messaging.kafka.protocol.*;
import ssg.legoflow.messaging.kafka.record.Compression;
import ssg.legoflow.messaging.kafka.record.Record;
import ssg.legoflow.messaging.kafka.record.RecordBatch;
import ssg.legoflow.messaging.kafka.transport.KafkaTransport;
import ssg.legoflow.messaging.kafka.client.InMemoryKafka;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class KafkaBrokerTest {

    @Test
    void testStartAndStop() {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            // Double-start while running must be rejected
            assertThatThrownBy(broker::start).isInstanceOf(IllegalStateException.class);
            broker.close();
        }
    }

    @Test
    void testCreateTopic() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            short error = broker.createTopic("test", 3);
            assertThat(error).isEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.partitionCount("test")).isEqualTo(3);
            assertThat(broker.topicNames()).contains("test");
        }
    }

    @Test
    void testCreateTopicAlreadyExists() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("test", 1);
            short error = broker.createTopic("test", 1);
            assertThat(error).isEqualTo(KafkaErrors.TOPIC_ALREADY_EXISTS.code());
        }
    }

    @Test
    void testCreateTopicInvalidPartitions() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            short error = broker.createTopic("test", 0);
            assertThat(error).isEqualTo(KafkaErrors.INVALID_PARTITIONS.code());
        }
    }

    @Test
    void testCreateTopicInvalidName() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            assertThat(broker.createTopic(null, 1)).isNotEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.createTopic("", 1)).isNotEqualTo(KafkaErrors.NONE.code());
        }
    }

    @Test
    void testDeleteTopic() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("test", 2);
            short error = broker.deleteTopic("test");
            assertThat(error).isEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.topicNames()).doesNotContain("test");
            assertThat(broker.partitionCount("test")).isEqualTo(-1);
        }
    }

    @Test
    void testDeleteTopicNotFound() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            short error = broker.deleteTopic("nonexistent");
            assertThat(error).isEqualTo(KafkaErrors.UNKNOWN_TOPIC_OR_PARTITION.code());
        }
    }

    @Test
    void testGetPartitionLog() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("test", 2);
            assertThat(broker.getPartitionLog(new TopicPartition("test", 0))).isNotNull();
            assertThat(broker.getPartitionLog(new TopicPartition("test", 1))).isNotNull();
            assertThat(broker.getPartitionLog(new TopicPartition("test", 2))).isNull();
        }
    }

    @Test
    void testMultipleTopics() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("a", 1);
            broker.createTopic("b", 2);
            broker.createTopic("c", 3);
            assertThat(broker.topicNames()).containsExactlyInAnyOrder("a", "b", "c");
            assertThat(broker.partitionCount("a")).isEqualTo(1);
            assertThat(broker.partitionCount("b")).isEqualTo(2);
            assertThat(broker.partitionCount("c")).isEqualTo(3);
        }
    }

    @Test
    void testCreatePartitions() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("test", 2);
            assertThat(broker.partitionCount("test")).isEqualTo(2);

            // The createPartitions method is tested via the broker handler through the admin client
            // Direct test: verify partition logs exist after creating the topic
            assertThat(broker.getPartitionLog(new TopicPartition("test", 0))).isNotNull();
            assertThat(broker.getPartitionLog(new TopicPartition("test", 1))).isNotNull();
        }
    }

    @Test
    void testDoubleStartRejected() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            assertThatThrownBy(broker::start).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testGroupCoordinator() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            assertThat(broker.groupCoordinator()).isNotNull();
        }
    }

    @Test
    void testTransactionManager() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            assertThat(broker.transactionManager()).isNotNull();
        }
    }

    @Test
    void testTransactionManagerHasGroupCoordinator() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            // Verify the wiring: transactionManager should be able to flush offsets on commit
            var txnManager = broker.transactionManager();
            var init = txnManager.initProducerId("txn-test");
            txnManager.addPartitionsToTxn("txn-test", init.producerId(), init.epoch(),
                    List.of(new TopicPartition("t", 0)));
            txnManager.addOffsetsToTxn("txn-test", init.producerId(), init.epoch(), "test-group");

            var offsets = Map.of(new TopicPartition("t", 0), 50L);
            txnManager.addPendingTxnOffsets("txn-test", init.producerId(), init.epoch(), offsets);
            txnManager.endTransaction("txn-test", init.producerId(), init.epoch(), true);

            // Verify offsets were flushed to group coordinator
            var committed = broker.groupCoordinator().fetchOffsets("test-group",
                    List.of(new TopicPartition("t", 0)));
            assertThat(committed).containsEntry(new TopicPartition("t", 0), 50L);
        }
    }

    @Test
    void testConfigManager() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            assertThat(broker.configManager()).isNotNull();
        }
    }

    @Test
    void testCreateTopicInitializesConfig() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("config-test", 1);
            var configs = broker.configManager().describeConfigs(
                    ConfigManager.RESOURCE_TYPE_TOPIC, "config-test");
            assertThat(configs).isNotEmpty();
            assertThat(configs).containsKey("retention.ms");
        }
    }

    @Test
    void testCredentialStoreAccessor() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            assertThat(broker.credentialStore()).isNotNull();
        }
    }

    @Test
    void testSaslHandshakeViaWire() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            // Send SaslHandshake request
            var handshakeReq = new SaslHandshakeRequest("PLAIN");
            byte[] payload = KafkaCodec.encodeSaslHandshakeRequest(handshakeReq);
            ByteBuffer resp = sendAndReceive(ch, ApiKey.SASL_HANDSHAKE.key(), payload);

            var handshakeResp = KafkaCodec.decodeSaslHandshakeResponse(resp);
            assertThat(handshakeResp.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(handshakeResp.mechanisms()).contains("PLAIN", "SCRAM-SHA-256");
        }
    }

    @Test
    void testSaslHandshakeUnsupportedMechanism() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            var handshakeReq = new SaslHandshakeRequest("GSSAPI");
            byte[] payload = KafkaCodec.encodeSaslHandshakeRequest(handshakeReq);
            ByteBuffer resp = sendAndReceive(ch, ApiKey.SASL_HANDSHAKE.key(), payload);

            var handshakeResp = KafkaCodec.decodeSaslHandshakeResponse(resp);
            assertThat(handshakeResp.errorCode()).isEqualTo(KafkaErrors.UNSUPPORTED_SASL_MECHANISM.code());
            assertThat(handshakeResp.mechanisms()).contains("PLAIN", "SCRAM-SHA-256");
        }
    }

    @Test
    void testSaslPlainAuthFlow() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.credentialStore().addPlainUser("testuser", "testpass");
            broker.start();
            var ch = inMemoryPair(broker);

            // Step 1: Handshake
            byte[] hsPayload = KafkaCodec.encodeSaslHandshakeRequest(new SaslHandshakeRequest("PLAIN"));
            ByteBuffer hsResp = sendAndReceive(ch, ApiKey.SASL_HANDSHAKE.key(), hsPayload);
            assertThat(KafkaCodec.decodeSaslHandshakeResponse(hsResp).errorCode())
                    .isEqualTo(KafkaErrors.NONE.code());

            // Step 2: Authenticate
            byte[] authBytes = "\0testuser\0testpass".getBytes(StandardCharsets.UTF_8);
            byte[] authPayload = KafkaCodec.encodeSaslAuthenticateRequest(new SaslAuthenticateRequest(authBytes));
            ByteBuffer authResp = sendAndReceive(ch, ApiKey.SASL_AUTHENTICATE.key(), authPayload);

            var saslResp = KafkaCodec.decodeSaslAuthenticateResponse(authResp);
            assertThat(saslResp.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        }
    }

    @Test
    void testSaslAuthenticateWithoutHandshake() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            // Try to authenticate without handshake first
            byte[] authBytes = "\0user\0pass".getBytes(StandardCharsets.UTF_8);
            byte[] authPayload = KafkaCodec.encodeSaslAuthenticateRequest(new SaslAuthenticateRequest(authBytes));
            ByteBuffer authResp = sendAndReceive(ch, ApiKey.SASL_AUTHENTICATE.key(), authPayload);

            var saslResp = KafkaCodec.decodeSaslAuthenticateResponse(authResp);
            assertThat(saslResp.errorCode()).isEqualTo(KafkaErrors.ILLEGAL_SASL_STATE.code());
        }
    }

    @Test
    void testReplicaManagerAccessor() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0, 5, 1)) {
            broker.start();
            assertThat(broker.replicaManager()).isNotNull();
            assertThat(broker.replicaManager().brokerId()).isEqualTo(5);
        }
    }

    @Test
    void testBrokerIdAccessor() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0, 7, 1)) {
            broker.start();
            assertThat(broker.brokerId()).isEqualTo(7);
        }
    }

    @Test
    void testLeaderAndIsrViaWire() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            var req = new LeaderAndIsrRequest(0, 1, List.of(
                    new LeaderAndIsrRequest.PartitionState("test-topic", 0, 0, 1, List.of(0, 1))));
            byte[] payload = KafkaCodec.encodeLeaderAndIsrRequest(req);
            ByteBuffer resp = sendAndReceive(ch, ApiKey.LEADER_AND_ISR.key(), payload);

            var leaderResp = KafkaCodec.decodeLeaderAndIsrResponse(resp);
            assertThat(leaderResp.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(leaderResp.partitions()).hasSize(1);
            assertThat(leaderResp.partitions().getFirst().errorCode()).isEqualTo(KafkaErrors.NONE.code());

            // Verify replica manager was updated
            var state = broker.replicaManager().getReplicaState(new TopicPartition("test-topic", 0));
            assertThat(state).isNotNull();
            assertThat(state.leaderBrokerId()).isZero();
            assertThat(state.leaderEpoch()).isEqualTo(1);
        }
    }

    @Test
    void testAlterPartitionReassignmentsViaWire() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            // Alter reassignments
            var alterReq = new AlterPartitionReassignmentsRequest(30000, List.of(
                    new AlterPartitionReassignmentsRequest.TopicReassignment("t1", List.of(
                            new AlterPartitionReassignmentsRequest.PartitionReassignment(0, List.of(0, 1, 2))))));
            byte[] alterPayload = KafkaCodec.encodeAlterPartitionReassignmentsRequest(alterReq);
            ByteBuffer alterResp = sendAndReceive(ch, ApiKey.ALTER_PARTITION_REASSIGNMENTS.key(), alterPayload);
            var alterResult = KafkaCodec.decodeAlterPartitionReassignmentsResponse(alterResp);
            assertThat(alterResult.errorCode()).isEqualTo(KafkaErrors.NONE.code());

            // List reassignments
            var listReq = new ListPartitionReassignmentsRequest(30000, List.of(
                    new ListPartitionReassignmentsRequest.TopicData("t1", List.of(0))));
            byte[] listPayload = KafkaCodec.encodeListPartitionReassignmentsRequest(listReq);
            ByteBuffer listResp = sendAndReceive(ch, ApiKey.LIST_PARTITION_REASSIGNMENTS.key(), listPayload);
            var listResult = KafkaCodec.decodeListPartitionReassignmentsResponse(listResp);
            assertThat(listResult.errorCode()).isEqualTo(KafkaErrors.NONE.code());
            assertThat(listResult.topics()).hasSize(1);
            assertThat(listResult.topics().getFirst().partitions().getFirst().replicas()).containsExactly(0, 1, 2);
        }
    }

    @Test
    void testWirePartialPrefixReassembles() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            // Frame split INSIDE the 4-byte length prefix: each send is its own buffer, so the
            // broker reads the prefix in parts and must retry (same buffer) until it has 4 bytes.
            byte[] frame = produceFrame("reassemble-prefix", 0, "v1");
            ch.send(toBuffer(Arrays.copyOfRange(frame, 0, 2)));
            ch.send(toBuffer(Arrays.copyOfRange(frame, 2, frame.length)));

            ProduceResponse resp = readProduceResponse(ch);
            assertThat(resp.responses().getFirst().name()).isEqualTo("reassemble-prefix");
            assertThat(resp.responses().getFirst().partitionResponses().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.getPartitionLog(new TopicPartition("reassemble-prefix", 0)).highWatermark()).isEqualTo(1);
        }
    }

    @Test
    void testWireFragmentedBodyReassembles() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            // Frame with the length prefix intact but the body split in half: the broker must
            // accumulate partial body reads until the full message arrives.
            byte[] frame = produceFrame("reassemble-body", 0, "value-0123456789");
            int split = 4 + frame.length / 2; // prefix intact, body in two buffers
            ch.send(toBuffer(Arrays.copyOfRange(frame, 0, split)));
            ch.send(toBuffer(Arrays.copyOfRange(frame, split, frame.length)));

            ProduceResponse resp = readProduceResponse(ch);
            assertThat(resp.responses().getFirst().name()).isEqualTo("reassemble-body");
            assertThat(resp.responses().getFirst().partitionResponses().getFirst().errorCode())
                    .isEqualTo(KafkaErrors.NONE.code());
            assertThat(broker.getPartitionLog(new TopicPartition("reassemble-body", 0)).highWatermark()).isEqualTo(1);
        }
    }

    @Test
    void testWireCoalescedFramesBothProcessed() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            var ch = inMemoryPair(broker);

            // Two complete frames sent back-to-back: a single read may hand the broker more
            // bytes than the current frame needs (the peer's tail is re-queued). The broker
            // must process both requests in order, neither frame may be dropped, and the
            // second frame's prefix bytes must not be consumed as the first frame's body.
            byte[] f1 = produceFrame("coalesced", 0, "first");
            byte[] f2 = produceFrame("coalesced", 0, "second");
            ch.send(toBuffer(f1));
            ch.send(toBuffer(f2));

            ProduceResponse r1 = readProduceResponse(ch);
            ProduceResponse r2 = readProduceResponse(ch);
            assertThat(r1.responses().getFirst().partitionResponses().getFirst().baseOffset()).isEqualTo(0);
            assertThat(r2.responses().getFirst().partitionResponses().getFirst().baseOffset()).isEqualTo(1);
            assertThat(broker.getPartitionLog(new TopicPartition("coalesced", 0)).highWatermark()).isEqualTo(2);
        }
    }

    // ===== Wire reassembly helpers (split/coalesce a request the broker must reassemble) =====

    /** Builds the complete length-prefixed PRODUCE request frame for one non-idempotent record. */
    private byte[] produceFrame(String topic, int partition, String value) throws IOException {
        var batch = new RecordBatch()
                .baseOffset(0).baseTimestamp(0).maxTimestamp(0)
                .compression(Compression.NONE).producerId(-1L).producerEpoch((short) -1)
                .records(List.of(new Record(null, value.getBytes(StandardCharsets.UTF_8), List.of())))
                .lastOffsetDelta(0);
        byte[] payload = KafkaCodec.encodeProduceRequest(new ProduceRequest(null, (short) 1, 5000,
                List.of(new ProduceRequest.TopicData(topic,
                        List.of(new ProduceRequest.PartitionData(partition, batch.encode()))))));
        var header = new RequestHeader(ApiKey.PRODUCE.key(), (short) 0, 0, "wire-reassembly");
        var framed = KafkaCodec.encodeRequest(header, payload);
        return toBytes(framed);
    }

    /**
     * Reads one length-prefixed response with reassembly across partial reads, then decodes it
     * as a PRODUCE response. Returns null on timeout/closed (test asserts non-null).
     */
    private ProduceResponse readProduceResponse(KafkaTransport ch) throws IOException {
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        while (lenBuf.hasRemaining()) {
            int n = ch.receiveWithTimeout(lenBuf, 10, java.util.concurrent.TimeUnit.SECONDS);
            if (n < 0) return null;
        }
        lenBuf.flip();
        int len = lenBuf.getInt();
        ByteBuffer body = ByteBuffer.allocate(len);
        while (body.hasRemaining()) {
            int n = ch.receiveWithTimeout(body, 10, java.util.concurrent.TimeUnit.SECONDS);
            if (n < 0) return null;
        }
        body.flip();
        body.getInt(); // response header correlation id
        return KafkaCodec.decodeProduceResponse(body);
    }

    private static ByteBuffer toBuffer(byte[] bytes) {
        ByteBuffer b = ByteBuffer.allocate(bytes.length);
        b.put(bytes);
        b.flip();
        return b;
    }

    private static byte[] toBytes(ByteBuffer buf) {
        byte[] out = new byte[buf.remaining()];
        buf.duplicate().get(out);
        return out;
    }

    @Test
    void testCompactAllWithCompactPolicy() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("compact-topic", 1);

            // Set cleanup.policy=compact
            broker.configManager().alterConfigs(ConfigManager.RESOURCE_TYPE_TOPIC,
                    "compact-topic", Map.of("cleanup.policy", "compact"));

            // Produce duplicate keyed records directly to partition log
            PartitionLog log = broker.getPartitionLog(new TopicPartition("compact-topic", 0));
            for (int i = 0; i < 5; i++) {
                Record record = new Record(0, 0L, "same-key".getBytes(), ("val-" + i).getBytes(), List.of());
                byte[] encoded = new RecordBatch()
                        .baseOffset(0)
                        .lastOffsetDelta(0)
                        .baseTimestamp(System.currentTimeMillis())
                        .maxTimestamp(System.currentTimeMillis())
                        .records(List.of(record))
                        .encode();
                log.append(encoded);
            }
            assertThat(log.highWatermark()).isEqualTo(5);

            int removed = broker.compactAll();
            assertThat(removed).isEqualTo(4); // only latest of 5 duplicates kept
            assertThat(log.highWatermark()).isEqualTo(5); // unchanged
        }
    }

    @Test
    void testCompactAllSkipsDeletePolicy() throws IOException {
        try (var broker = new KafkaBroker("localhost", 0)) {
            broker.start();
            broker.createTopic("delete-topic", 1);

            // Default cleanup.policy is "delete" — do not set to compact

            PartitionLog log = broker.getPartitionLog(new TopicPartition("delete-topic", 0));
            for (int i = 0; i < 3; i++) {
                Record record = new Record(0, 0L, "same-key".getBytes(), ("val-" + i).getBytes(), List.of());
                byte[] encoded = new RecordBatch()
                        .baseOffset(0)
                        .lastOffsetDelta(0)
                        .baseTimestamp(System.currentTimeMillis())
                        .maxTimestamp(System.currentTimeMillis())
                        .records(List.of(record))
                        .encode();
                log.append(encoded);
            }

            int removed = broker.compactAll();
            assertThat(removed).isZero(); // delete policy — not compacted
        }
    }

    // --- Wire-level test helpers ---

    private static final AtomicInteger corrId = new AtomicInteger(0);

    /**
     * Creates an in-memory pair, feeds the server end to the broker (the same seam the
     * production service layer uses), and returns the client end so the test can send
     * requests over it.
     */
    private KafkaTransport inMemoryPair(KafkaBroker broker) {
        return InMemoryKafka.pair(broker)[1];
    }

    /**
     * Sends a length-prefixed request over the transport and reads the full response back,
     * reassembling across partial reads. Verifies the correlation id round-trips.
     */
    private ByteBuffer sendAndReceive(KafkaTransport ch, short apiKey, byte[] payload) throws IOException {
        int correlationId = corrId.getAndIncrement();
        var header = new RequestHeader(apiKey, (short) 0, correlationId, "test");
        ByteBuffer request = KafkaCodec.encodeRequest(header, payload);
        ch.send(request);

        // Read the 4-byte length prefix (reassemble across partial reads)
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        while (lenBuf.hasRemaining()) {
            int n = ch.receive(lenBuf);
            if (n < 0) throw new IOException("EOF reading length prefix");
        }
        lenBuf.flip();
        int len = lenBuf.getInt();

        // Read the response body (reassemble across partial reads)
        ByteBuffer respBuf = ByteBuffer.allocate(len);
        while (respBuf.hasRemaining()) {
            int n = ch.receive(respBuf);
            if (n < 0) throw new IOException("EOF reading response body");
        }
        respBuf.flip();

        int respCorrId = respBuf.getInt(); // skip correlation id
        assertThat(respCorrId).isEqualTo(correlationId);
        return respBuf;
    }
}
