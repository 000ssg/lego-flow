package ssg.legoflow.database.mysql.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL packet framing: 4-byte header (3-byte payload length LE + 1-byte sequence ID) + payload.
 *
 * <p>Maximum payload size per packet is 16MB (2^24 - 1 = 16777215 bytes).
 * Payloads larger than this are split into multiple packets, each with an
 * incrementing sequence ID. A payload of exactly 16MB is followed by an
 * empty packet to signal completion.
 *
 * @param sequenceId the packet sequence number (0-255, wraps)
 * @param payload the packet payload data
 * @since 0.1.0
 */
public record MysqlPacket(int sequenceId, byte[] payload) {

    /** Maximum payload size per packet: 2^24 - 1 = 16,777,215 bytes. */
    public static final int MAX_PAYLOAD_SIZE = (1 << 24) - 1;

    /** Header size: 3 bytes length + 1 byte sequence ID. */
    public static final int HEADER_SIZE = 4;

    /**
     * Reads a single MySQL packet from an input stream.
     *
     * @param in the input stream
     * @return the decoded packet
     * @throws IOException if an I/O error occurs or the stream ends unexpectedly
     */
    public static MysqlPacket readFrom(InputStream in) throws IOException {
        var header = in.readNBytes(HEADER_SIZE);
        if (header.length < HEADER_SIZE) {
            throw new IOException("Unexpected end of stream reading packet header (got "
                    + header.length + " bytes)");
        }
        int length = (header[0] & 0xFF)
                | ((header[1] & 0xFF) << 8)
                | ((header[2] & 0xFF) << 16);
        int seqId = header[3] & 0xFF;

        var payload = in.readNBytes(length);
        if (payload.length < length) {
            throw new IOException("Unexpected end of stream reading packet payload (expected "
                    + length + ", got " + payload.length + ")");
        }
        return new MysqlPacket(seqId, payload);
    }

    /**
     * Reads a possibly multi-packet payload from an input stream.
     *
     * <p>If a packet has the maximum payload size, the next packet is a
     * continuation. Continues reading until a packet with less than max
     * payload size is received.
     *
     * @param in the input stream
     * @return the complete reassembled packet with the first sequence ID
     * @throws IOException if an I/O error occurs
     */
    public static MysqlPacket readFullFrom(InputStream in) throws IOException {
        var first = readFrom(in);
        if (first.payload.length < MAX_PAYLOAD_SIZE) {
            return first;
        }

        var chunks = new ArrayList<byte[]>();
        chunks.add(first.payload);
        int totalLength = first.payload.length;

        MysqlPacket packet = first;
        while (packet.payload.length == MAX_PAYLOAD_SIZE) {
            packet = readFrom(in);
            chunks.add(packet.payload);
            totalLength += packet.payload.length;
        }

        var combined = new byte[totalLength];
        int offset = 0;
        for (var chunk : chunks) {
            System.arraycopy(chunk, 0, combined, offset, chunk.length);
            offset += chunk.length;
        }
        return new MysqlPacket(first.sequenceId, combined);
    }

    /**
     * Writes this packet to an output stream.
     *
     * <p>If the payload exceeds the maximum size, it is automatically split
     * into multiple packets with incrementing sequence IDs.
     *
     * @param out the output stream
     * @throws IOException if an I/O error occurs
     */
    public void writeTo(OutputStream out) throws IOException {
        if (payload.length <= MAX_PAYLOAD_SIZE) {
            writeRawPacket(out, sequenceId, payload, 0, payload.length);
            return;
        }

        int offset = 0;
        int seqId = sequenceId;
        while (offset < payload.length) {
            int chunkSize = Math.min(MAX_PAYLOAD_SIZE, payload.length - offset);
            writeRawPacket(out, seqId, payload, offset, chunkSize);
            offset += chunkSize;
            seqId = (seqId + 1) & 0xFF;
        }

        // If last chunk was exactly MAX_PAYLOAD_SIZE, send empty terminator
        if (payload.length % MAX_PAYLOAD_SIZE == 0) {
            writeRawPacket(out, seqId, payload, 0, 0);
        }
    }

    /**
     * Encodes this packet as a byte array including the 4-byte header.
     *
     * <p>For payloads within the max size, returns a single packet.
     * For larger payloads, returns all split packets concatenated.
     *
     * @return the encoded packet bytes
     */
    public byte[] encode() {
        if (payload.length <= MAX_PAYLOAD_SIZE) {
            var buf = new byte[HEADER_SIZE + payload.length];
            buf[0] = (byte) (payload.length & 0xFF);
            buf[1] = (byte) ((payload.length >> 8) & 0xFF);
            buf[2] = (byte) ((payload.length >> 16) & 0xFF);
            buf[3] = (byte) (sequenceId & 0xFF);
            System.arraycopy(payload, 0, buf, HEADER_SIZE, payload.length);
            return buf;
        }

        var packets = split();
        int totalSize = 0;
        var encoded = new ArrayList<byte[]>();
        for (var pkt : packets) {
            var enc = pkt.encode();
            encoded.add(enc);
            totalSize += enc.length;
        }
        var result = new byte[totalSize];
        int offset = 0;
        for (var enc : encoded) {
            System.arraycopy(enc, 0, result, offset, enc.length);
            offset += enc.length;
        }
        return result;
    }

    /**
     * Decodes a MySQL packet from raw bytes including the header.
     *
     * @param data the raw bytes (header + payload)
     * @return the decoded packet
     * @throws IllegalArgumentException if data is too short
     */
    public static MysqlPacket decode(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Data too short for MySQL packet header: " + data.length);
        }
        int length = (data[0] & 0xFF)
                | ((data[1] & 0xFF) << 8)
                | ((data[2] & 0xFF) << 16);
        int seqId = data[3] & 0xFF;
        var payload = new byte[length];
        System.arraycopy(data, HEADER_SIZE, payload, 0, length);
        return new MysqlPacket(seqId, payload);
    }

    /**
     * Returns a ByteBuffer wrapping the payload for reading.
     *
     * @return a read-only byte buffer over the payload
     */
    public ByteBuffer payloadBuffer() {
        return ByteBuffer.wrap(payload);
    }

    /**
     * Splits this packet into multiple packets if payload exceeds max size.
     *
     * @return list of packets, each within the max payload size
     */
    public List<MysqlPacket> split() {
        if (payload.length < MAX_PAYLOAD_SIZE) {
            return List.of(this);
        }

        var packets = new ArrayList<MysqlPacket>();
        int offset = 0;
        int seqId = sequenceId;
        while (offset < payload.length) {
            int chunkSize = Math.min(MAX_PAYLOAD_SIZE, payload.length - offset);
            var chunk = new byte[chunkSize];
            System.arraycopy(payload, offset, chunk, 0, chunkSize);
            packets.add(new MysqlPacket(seqId, chunk));
            offset += chunkSize;
            seqId = (seqId + 1) & 0xFF;
        }

        if (payload.length % MAX_PAYLOAD_SIZE == 0) {
            packets.add(new MysqlPacket(seqId, new byte[0]));
        }

        return packets;
    }

    private static void writeRawPacket(OutputStream out, int seqId, byte[] data,
                                        int offset, int length) throws IOException {
        var header = new byte[HEADER_SIZE];
        header[0] = (byte) (length & 0xFF);
        header[1] = (byte) ((length >> 8) & 0xFF);
        header[2] = (byte) ((length >> 16) & 0xFF);
        header[3] = (byte) (seqId & 0xFF);
        out.write(header);
        if (length > 0) {
            out.write(data, offset, length);
        }
    }
}
