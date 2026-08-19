package ssg.legoflow.messaging.kafka.record;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class RecordBatchTest {

    @Test
    void testEncodeDecodeEmptyBatch() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(1000L)
                .maxTimestamp(1000L)
                .records(List.of());

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.baseOffset()).isZero();
        assertThat(decoded.records()).isEmpty();
    }

    @Test
    void testEncodeDecodeSingleRecord() {
        Record record = new Record("key".getBytes(), "value".getBytes());
        RecordBatch batch = new RecordBatch()
                .baseOffset(5)
                .baseTimestamp(System.currentTimeMillis())
                .maxTimestamp(System.currentTimeMillis())
                .records(List.of(record));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.baseOffset()).isEqualTo(5);
        assertThat(decoded.records()).hasSize(1);
        assertThat(decoded.records().getFirst().key()).isEqualTo("key".getBytes());
        assertThat(decoded.records().getFirst().value()).isEqualTo("value".getBytes());
    }

    @Test
    void testEncodeDecodeMultipleRecords() {
        List<Record> records = List.of(
                new Record(0, 0L, "k1".getBytes(), "v1".getBytes(), List.of()),
                new Record(1, 100L, "k2".getBytes(), "v2".getBytes(), List.of()),
                new Record(2, 200L, "k3".getBytes(), "v3".getBytes(), List.of()));

        RecordBatch batch = new RecordBatch()
                .baseOffset(10)
                .lastOffsetDelta(2)
                .baseTimestamp(1000L)
                .maxTimestamp(1200L)
                .records(records);

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.records()).hasSize(3);
        assertThat(decoded.records().get(0).offsetDelta()).isZero();
        assertThat(decoded.records().get(1).offsetDelta()).isEqualTo(1);
        assertThat(decoded.records().get(2).offsetDelta()).isEqualTo(2);
    }

    @Test
    void testNullKeyAndValue() {
        Record record = new Record(null, null);
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(record));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.records().getFirst().key()).isNull();
        assertThat(decoded.records().getFirst().value()).isNull();
    }

    @Test
    void testRecordHeaders() {
        Record record = new Record(0, 0L, "key".getBytes(), "value".getBytes(),
                List.of(
                        Header.of("h1", "v1"),
                        Header.of("h2", "v2"),
                        new Header("h3", null)));

        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(record));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        var headers = decoded.records().getFirst().headers();
        assertThat(headers).hasSize(3);
        assertThat(headers.get(0).key()).isEqualTo("h1");
        assertThat(new String(headers.get(0).value(), StandardCharsets.UTF_8)).isEqualTo("v1");
        assertThat(headers.get(2).value()).isNull();
    }

    @Test
    void testGzipCompression() {
        Record record = new Record("key".getBytes(), "some-longer-value-for-compression".getBytes());
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .compression(Compression.GZIP)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(record));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.compression()).isEqualTo(Compression.GZIP);
        assertThat(decoded.records()).hasSize(1);
        assertThat(decoded.records().getFirst().key()).isEqualTo("key".getBytes());
        assertThat(decoded.records().getFirst().value())
                .isEqualTo("some-longer-value-for-compression".getBytes());
    }

    @Test
    void testGzipCompressionMultipleRecords() {
        List<Record> records = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            records.add(new Record(i, 0L, ("k" + i).getBytes(), ("v" + i).getBytes(), List.of()));
        }

        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .lastOffsetDelta(99)
                .compression(Compression.GZIP)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(records);

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.records()).hasSize(100);
        assertThat(decoded.records().get(50).key()).isEqualTo("k50".getBytes());
    }

    @Test
    void testProducerMetadata() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .producerId(42L)
                .producerEpoch((short) 1)
                .baseSequence(7)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(new Record("k".getBytes(), "v".getBytes())));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.producerId()).isEqualTo(42L);
        assertThat(decoded.producerEpoch()).isEqualTo((short) 1);
        assertThat(decoded.baseSequence()).isEqualTo(7);
    }

    @Test
    void testTransactionalFlag() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .transactional(true)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of());

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.transactional()).isTrue();
        assertThat(decoded.controlBatch()).isFalse();
    }

    @Test
    void testControlBatchFlag() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .controlBatch(true)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of());

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.controlBatch()).isTrue();
    }

    @Test
    void testTimestampType() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .timestampType(true) // LogAppendTime
                .baseTimestamp(1000)
                .maxTimestamp(2000)
                .records(List.of());

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.timestampType()).isTrue();
        assertThat(decoded.baseTimestamp()).isEqualTo(1000);
        assertThat(decoded.maxTimestamp()).isEqualTo(2000);
    }

    @Test
    void testPartitionLeaderEpoch() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .partitionLeaderEpoch(42)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of());

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.partitionLeaderEpoch()).isEqualTo(42);
    }

    @Test
    void testMagicByte() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of());

        byte[] encoded = batch.encode();
        // Magic is at position 8(baseOffset) + 4(batchLength) + 4(partitionLeaderEpoch) = 16
        assertThat(encoded[16]).isEqualTo(RecordBatch.MAGIC);
    }

    @Test
    void testInvalidMagicRejected() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of());

        byte[] encoded = batch.encode();
        encoded[16] = 1; // Change magic to 1

        assertThatThrownBy(() -> RecordBatch.decode(encoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("magic");
    }

    @Test
    void testLargeValues() {
        byte[] largeValue = new byte[10000];
        java.util.Arrays.fill(largeValue, (byte) 'X');

        Record record = new Record("key".getBytes(), largeValue);
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(record));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.records().getFirst().value()).isEqualTo(largeValue);
    }

    @Test
    void testVarintRoundTrip() {
        var out = new java.io.ByteArrayOutputStream();
        RecordBatch.writeVarint(out, 0);
        RecordBatch.writeVarint(out, 1);
        RecordBatch.writeVarint(out, -1);
        RecordBatch.writeVarint(out, 300);
        RecordBatch.writeVarint(out, -300);
        RecordBatch.writeVarint(out, Integer.MAX_VALUE);
        RecordBatch.writeVarint(out, Integer.MIN_VALUE);

        ByteBuffer buf = ByteBuffer.wrap(out.toByteArray());
        assertThat(RecordBatch.readVarint(buf)).isZero();
        assertThat(RecordBatch.readVarint(buf)).isEqualTo(1);
        assertThat(RecordBatch.readVarint(buf)).isEqualTo(-1);
        assertThat(RecordBatch.readVarint(buf)).isEqualTo(300);
        assertThat(RecordBatch.readVarint(buf)).isEqualTo(-300);
        assertThat(RecordBatch.readVarint(buf)).isEqualTo(Integer.MAX_VALUE);
        assertThat(RecordBatch.readVarint(buf)).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void testVarlongRoundTrip() {
        var out = new java.io.ByteArrayOutputStream();
        RecordBatch.writeVarlong(out, 0L);
        RecordBatch.writeVarlong(out, Long.MAX_VALUE);
        RecordBatch.writeVarlong(out, Long.MIN_VALUE);
        RecordBatch.writeVarlong(out, -1L);

        ByteBuffer buf = ByteBuffer.wrap(out.toByteArray());
        assertThat(RecordBatch.readVarlong(buf)).isZero();
        assertThat(RecordBatch.readVarlong(buf)).isEqualTo(Long.MAX_VALUE);
        assertThat(RecordBatch.readVarlong(buf)).isEqualTo(Long.MIN_VALUE);
        assertThat(RecordBatch.readVarlong(buf)).isEqualTo(-1L);
    }

    @Test
    void testCompressionNone() {
        assertThat(Compression.NONE.id()).isZero();
        assertThat(Compression.forId(0)).isEqualTo(Compression.NONE);
    }

    @Test
    void testCompressionGzip() {
        assertThat(Compression.GZIP.id()).isEqualTo(1);
        assertThat(Compression.forId(1)).isEqualTo(Compression.GZIP);
    }

    @Test
    void testUnsupportedCompressionSnappy() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .compression(Compression.SNAPPY)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(new Record("k".getBytes(), "v".getBytes())));

        assertThatThrownBy(batch::encode)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SNAPPY");
    }

    @Test
    void testHeaderOfStringHelper() {
        Header h = Header.of("key", "value");
        assertThat(h.key()).isEqualTo("key");
        assertThat(new String(h.value(), StandardCharsets.UTF_8)).isEqualTo("value");
    }

    @Test
    void testHeaderOfNullValue() {
        Header h = Header.of("key", null);
        assertThat(h.key()).isEqualTo("key");
        assertThat(h.value()).isNull();
    }

    @Test
    void testRecordConvenienceConstructors() {
        Record r1 = new Record("k".getBytes(), "v".getBytes());
        assertThat(r1.offsetDelta()).isZero();
        assertThat(r1.timestampDelta()).isZero();
        assertThat(r1.headers()).isEmpty();

        Record r2 = new Record("k".getBytes(), "v".getBytes(), List.of(Header.of("h", "v")));
        assertThat(r2.headers()).hasSize(1);
    }

    @Test
    void testCompressionForIdInvalid() {
        assertThatThrownBy(() -> Compression.forId(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeFromByteBuffer() {
        RecordBatch batch = new RecordBatch()
                .baseOffset(42)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(new Record("k".getBytes(), "v".getBytes())));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(ByteBuffer.wrap(encoded));
        assertThat(decoded.baseOffset()).isEqualTo(42);
    }

    @Test
    void testEmptyKeyEmptyValue() {
        Record record = new Record(new byte[0], new byte[0]);
        RecordBatch batch = new RecordBatch()
                .baseOffset(0)
                .baseTimestamp(0)
                .maxTimestamp(0)
                .records(List.of(record));

        byte[] encoded = batch.encode();
        RecordBatch decoded = RecordBatch.decode(encoded);

        assertThat(decoded.records().getFirst().key()).isEmpty();
        assertThat(decoded.records().getFirst().value()).isEmpty();
    }
}
