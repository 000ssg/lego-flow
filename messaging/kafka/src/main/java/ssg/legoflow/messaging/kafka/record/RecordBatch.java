package ssg.legoflow.messaging.kafka.record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Kafka v2 record batch (magic=2).
 *
 * <p>Binary layout:
 * <pre>
 * baseOffset: int64
 * batchLength: int32
 * partitionLeaderEpoch: int32
 * magic: int8 (value=2)
 * crc: uint32 (CRC32C of everything after this field)
 * attributes: int16
 *   bit 0-2: compression (0=none, 1=gzip, 2=snappy, 3=lz4, 4=zstd)
 *   bit 3: timestamp type (0=CreateTime, 1=LogAppendTime)
 *   bit 4: is transactional
 *   bit 5: is control batch
 * lastOffsetDelta: int32
 * baseTimestamp: int64
 * maxTimestamp: int64
 * producerId: int64
 * producerEpoch: int16
 * baseSequence: int32
 * records count: int32
 * records: Record[]
 * </pre>
 *
 * @since 1.0.0
 */
public final class RecordBatch {

    /** Magic byte for Kafka v2 record batch format. */
    public static final byte MAGIC = 2;

    private long baseOffset;
    private int partitionLeaderEpoch;
    private Compression compression;
    private boolean timestampType; // false=CreateTime, true=LogAppendTime
    private boolean transactional;
    private boolean controlBatch;
    private int lastOffsetDelta;
    private long baseTimestamp;
    private long maxTimestamp;
    private long producerId;
    private short producerEpoch;
    private int baseSequence;
    private List<Record> records;

    /**
     * Creates an empty record batch with defaults.
     */
    public RecordBatch() {
        this.compression = Compression.NONE;
        this.producerId = -1L;
        this.producerEpoch = -1;
        this.baseSequence = -1;
        this.records = new ArrayList<>();
    }

    // --- Getters and setters ---

    public long baseOffset() { return baseOffset; }
    public RecordBatch baseOffset(long baseOffset) { this.baseOffset = baseOffset; return this; }

    public int partitionLeaderEpoch() { return partitionLeaderEpoch; }
    public RecordBatch partitionLeaderEpoch(int epoch) { this.partitionLeaderEpoch = epoch; return this; }

    public Compression compression() { return compression; }
    public RecordBatch compression(Compression compression) { this.compression = compression; return this; }

    public boolean timestampType() { return timestampType; }
    public RecordBatch timestampType(boolean logAppendTime) { this.timestampType = logAppendTime; return this; }

    public boolean transactional() { return transactional; }
    public RecordBatch transactional(boolean transactional) { this.transactional = transactional; return this; }

    public boolean controlBatch() { return controlBatch; }
    public RecordBatch controlBatch(boolean controlBatch) { this.controlBatch = controlBatch; return this; }

    public int lastOffsetDelta() { return lastOffsetDelta; }
    public RecordBatch lastOffsetDelta(int delta) { this.lastOffsetDelta = delta; return this; }

    public long baseTimestamp() { return baseTimestamp; }
    public RecordBatch baseTimestamp(long ts) { this.baseTimestamp = ts; return this; }

    public long maxTimestamp() { return maxTimestamp; }
    public RecordBatch maxTimestamp(long ts) { this.maxTimestamp = ts; return this; }

    public long producerId() { return producerId; }
    public RecordBatch producerId(long id) { this.producerId = id; return this; }

    public short producerEpoch() { return producerEpoch; }
    public RecordBatch producerEpoch(short epoch) { this.producerEpoch = epoch; return this; }

    public int baseSequence() { return baseSequence; }
    public RecordBatch baseSequence(int seq) { this.baseSequence = seq; return this; }

    public List<Record> records() { return records; }
    public RecordBatch records(List<Record> records) { this.records = records; return this; }

    /**
     * Encodes this record batch to bytes.
     *
     * @return the encoded bytes
     */
    public byte[] encode() {
        // Encode records
        byte[] recordsBytes = encodeRecords();

        // Compress if needed
        byte[] compressedRecords = compress(recordsBytes);

        // Build attributes
        short attributes = (short) (compression.id() & 0x07);
        if (timestampType) attributes |= 0x08;
        if (transactional) attributes |= 0x10;
        if (controlBatch) attributes |= 0x20;

        // Calculate CRC over: attributes through end of records
        // CRC covers bytes from attributes onwards
        int crcPayloadSize = 2 + 4 + 8 + 8 + 8 + 2 + 4 + 4 + compressedRecords.length;
        ByteBuffer crcBuf = ByteBuffer.allocate(crcPayloadSize);
        crcBuf.putShort(attributes);
        crcBuf.putInt(lastOffsetDelta);
        crcBuf.putLong(baseTimestamp);
        crcBuf.putLong(maxTimestamp);
        crcBuf.putLong(producerId);
        crcBuf.putShort(producerEpoch);
        crcBuf.putInt(baseSequence);
        crcBuf.putInt(records.size());
        crcBuf.put(compressedRecords);
        crcBuf.flip();

        CRC32C crc32c = new CRC32C();
        crc32c.update(crcBuf.array(), 0, crcBuf.remaining());
        int crc = (int) crc32c.getValue();

        // Total batch size: partitionLeaderEpoch(4) + magic(1) + crc(4) + crcPayload
        int batchLength = 4 + 1 + 4 + crcPayloadSize;

        // Full message: baseOffset(8) + batchLength(4) + batch content
        ByteBuffer buf = ByteBuffer.allocate(8 + 4 + batchLength);
        buf.putLong(baseOffset);
        buf.putInt(batchLength);
        buf.putInt(partitionLeaderEpoch);
        buf.put(MAGIC);
        buf.putInt(crc);
        buf.putShort(attributes);
        buf.putInt(lastOffsetDelta);
        buf.putLong(baseTimestamp);
        buf.putLong(maxTimestamp);
        buf.putLong(producerId);
        buf.putShort(producerEpoch);
        buf.putInt(baseSequence);
        buf.putInt(records.size());
        buf.put(compressedRecords);
        buf.flip();

        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a record batch from bytes.
     *
     * @param data the raw bytes
     * @return the decoded record batch
     */
    public static RecordBatch decode(byte[] data) {
        return decode(ByteBuffer.wrap(data));
    }

    /**
     * Decodes a record batch from a ByteBuffer.
     *
     * @param buf the buffer
     * @return the decoded record batch
     */
    public static RecordBatch decode(ByteBuffer buf) {
        RecordBatch batch = new RecordBatch();
        batch.baseOffset = buf.getLong();
        int batchLength = buf.getInt();
        batch.partitionLeaderEpoch = buf.getInt();
        byte magic = buf.get();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Unsupported record batch magic: " + magic);
        }
        int crc = buf.getInt();
        short attributes = buf.getShort();
        batch.compression = Compression.forId(attributes & 0x07);
        batch.timestampType = (attributes & 0x08) != 0;
        batch.transactional = (attributes & 0x10) != 0;
        batch.controlBatch = (attributes & 0x20) != 0;
        batch.lastOffsetDelta = buf.getInt();
        batch.baseTimestamp = buf.getLong();
        batch.maxTimestamp = buf.getLong();
        batch.producerId = buf.getLong();
        batch.producerEpoch = buf.getShort();
        batch.baseSequence = buf.getInt();
        int recordCount = buf.getInt();

        // Read remaining bytes for records
        int recordsSize = batchLength - (4 + 1 + 4 + 2 + 4 + 8 + 8 + 8 + 2 + 4 + 4);
        byte[] recordsBytes = new byte[recordsSize];
        buf.get(recordsBytes);

        // Decompress if needed
        byte[] decompressed = decompress(recordsBytes, batch.compression);

        // Decode records
        batch.records = decodeRecords(decompressed, recordCount);

        return batch;
    }

    // --- Varint encoding/decoding ---

    /**
     * Writes a variable-length signed integer (zigzag encoded).
     */
    static void writeVarint(ByteArrayOutputStream out, int value) {
        int encoded = (value << 1) ^ (value >> 31); // zigzag encode
        writeUnsignedVarint(out, encoded);
    }

    /**
     * Writes a variable-length unsigned integer.
     */
    static void writeUnsignedVarint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    /**
     * Writes a variable-length signed long (zigzag encoded).
     */
    static void writeVarlong(ByteArrayOutputStream out, long value) {
        long encoded = (value << 1) ^ (value >> 63);
        writeUnsignedVarlong(out, encoded);
    }

    static void writeUnsignedVarlong(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }

    /**
     * Reads a variable-length signed integer (zigzag decoded).
     */
    static int readVarint(ByteBuffer buf) {
        int raw = readUnsignedVarint(buf);
        return (raw >>> 1) ^ -(raw & 1);
    }

    /**
     * Reads a variable-length unsigned integer.
     */
    static int readUnsignedVarint(ByteBuffer buf) {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = buf.get() & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0 && shift < 35);
        return value;
    }

    /**
     * Reads a variable-length signed long (zigzag decoded).
     */
    static long readVarlong(ByteBuffer buf) {
        long raw = readUnsignedVarlong(buf);
        return (raw >>> 1) ^ -(raw & 1);
    }

    static long readUnsignedVarlong(ByteBuffer buf) {
        long value = 0;
        int shift = 0;
        long b;
        do {
            b = buf.get() & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0 && shift < 70);
        return value;
    }

    // --- Record encoding ---

    private byte[] encodeRecords() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Record rec : records) {
            encodeRecord(out, rec);
        }
        return out.toByteArray();
    }

    private void encodeRecord(ByteArrayOutputStream out, Record rec) {
        // Encode record body first to get length
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0); // attributes (unused in v2)
        writeVarlong(body, rec.timestampDelta());
        writeVarint(body, rec.offsetDelta());

        // Key
        if (rec.key() == null) {
            writeVarint(body, -1);
        } else {
            writeVarint(body, rec.key().length);
            body.writeBytes(rec.key());
        }

        // Value
        if (rec.value() == null) {
            writeVarint(body, -1);
        } else {
            writeVarint(body, rec.value().length);
            body.writeBytes(rec.value());
        }

        // Headers
        writeVarint(body, rec.headers().size());
        for (Header h : rec.headers()) {
            byte[] keyBytes = h.key().getBytes(StandardCharsets.UTF_8);
            writeVarint(body, keyBytes.length);
            body.writeBytes(keyBytes);
            if (h.value() == null) {
                writeVarint(body, -1);
            } else {
                writeVarint(body, h.value().length);
                body.writeBytes(h.value());
            }
        }

        byte[] bodyBytes = body.toByteArray();
        writeVarint(out, bodyBytes.length);
        out.writeBytes(bodyBytes);
    }

    private static List<Record> decodeRecords(byte[] data, int count) {
        List<Record> records = new ArrayList<>(count);
        ByteBuffer buf = ByteBuffer.wrap(data);
        for (int i = 0; i < count; i++) {
            records.add(decodeRecord(buf));
        }
        return records;
    }

    private static Record decodeRecord(ByteBuffer buf) {
        int length = readVarint(buf);
        int startPos = buf.position();

        byte attributes = buf.get(); // unused in v2
        long timestampDelta = readVarlong(buf);
        int offsetDelta = readVarint(buf);

        // Key
        int keyLength = readVarint(buf);
        byte[] key = null;
        if (keyLength >= 0) {
            key = new byte[keyLength];
            buf.get(key);
        }

        // Value
        int valueLength = readVarint(buf);
        byte[] value = null;
        if (valueLength >= 0) {
            value = new byte[valueLength];
            buf.get(value);
        }

        // Headers
        int headerCount = readVarint(buf);
        List<Header> headers = new ArrayList<>(headerCount);
        for (int i = 0; i < headerCount; i++) {
            int hKeyLen = readVarint(buf);
            byte[] hKey = new byte[hKeyLen];
            buf.get(hKey);
            int hValLen = readVarint(buf);
            byte[] hVal = null;
            if (hValLen >= 0) {
                hVal = new byte[hValLen];
                buf.get(hVal);
            }
            headers.add(new Header(new String(hKey, StandardCharsets.UTF_8), hVal));
        }

        // Skip any remaining bytes if length doesn't match exactly
        int consumed = buf.position() - startPos;
        if (consumed < length) {
            buf.position(startPos + length);
        }

        return new Record(offsetDelta, timestampDelta, key, value, headers);
    }

    // --- Compression ---

    private byte[] compress(byte[] data) {
        if (compression == Compression.NONE) return data;
        if (compression == Compression.GZIP) return gzipCompress(data);
        throw new UnsupportedOperationException(
                compression + " compression not supported (JDK-only policy: only GZIP available)");
    }

    private static byte[] decompress(byte[] data, Compression compression) {
        if (compression == Compression.NONE) return data;
        if (compression == Compression.GZIP) return gzipDecompress(data);
        throw new UnsupportedOperationException(
                compression + " compression not supported (JDK-only policy: only GZIP available)");
    }

    private static byte[] gzipCompress(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
                gos.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("GZIP compression failed", e);
        }
    }

    private static byte[] gzipDecompress(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            try (GZIPInputStream gis = new GZIPInputStream(bais)) {
                return gis.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException("GZIP decompression failed", e);
        }
    }
}
