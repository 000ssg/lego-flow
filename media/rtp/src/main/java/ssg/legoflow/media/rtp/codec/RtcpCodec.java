package ssg.legoflow.media.rtp.codec;

import ssg.legoflow.media.rtp.rtcp.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * Codec for encoding and decoding RTCP packets to/from {@link ByteBuffer} (RFC 3550 Section 6).
 *
 * <p>Each RTCP packet shares a common 4-byte header:
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|  count  |      PT       |           length              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * @since 0.1.0
 */
public final class RtcpCodec {

    /** RTCP version, always 2. */
    private static final int VERSION = 2;

    /** RTCP common header size in bytes. */
    private static final int HEADER_SIZE = 4;

    private RtcpCodec() {}

    // ---- Encoding ----

    /**
     * Encodes a compound RTCP packet into a newly allocated {@link ByteBuffer}.
     *
     * @param compound the compound packet to encode
     * @return a ByteBuffer containing the encoded compound packet
     */
    public static ByteBuffer encodeCompound(CompoundPacket compound) {
        List<ByteBuffer> parts = new ArrayList<>(compound.size());
        int totalSize = 0;
        for (RtcpPacket pkt : compound.packets()) {
            ByteBuffer encoded = encode(pkt);
            parts.add(encoded);
            totalSize += encoded.remaining();
        }

        ByteBuffer result = ByteBuffer.allocate(totalSize);
        for (ByteBuffer part : parts) {
            result.put(part);
        }
        result.flip();
        return result;
    }

    /**
     * Encodes a single RTCP packet into a newly allocated {@link ByteBuffer}.
     *
     * @param packet the RTCP packet to encode
     * @return a ByteBuffer containing the encoded packet
     */
    public static ByteBuffer encode(RtcpPacket packet) {
        return switch (packet) {
            case SenderReport sr -> encodeSenderReport(sr);
            case ReceiverReport rr -> encodeReceiverReport(rr);
            case SourceDescription sdes -> encodeSourceDescription(sdes);
            case Goodbye bye -> encodeGoodbye(bye);
            case ApplicationDefined app -> encodeApplicationDefined(app);
        };
    }

    private static ByteBuffer encodeSenderReport(SenderReport sr) {
        // Header(4) + SSRC(4) + SenderInfo(20) + Reports(24 each)
        int length = 4 + 20 + sr.reportCount() * ReceptionReport.SIZE;
        int totalSize = HEADER_SIZE + length;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        writeHeader(buf, sr.reportCount(), RtcpPacket.PT_SR, length / 4);
        buf.putInt((int) sr.ssrc());

        // Sender info: NTP timestamp (64-bit), RTP timestamp, packet count, octet count
        buf.putLong(sr.ntpTimestamp());
        buf.putInt((int) sr.rtpTimestamp());
        buf.putInt((int) sr.senderPacketCount());
        buf.putInt((int) sr.senderOctetCount());

        for (ReceptionReport rr : sr.reports()) {
            encodeReceptionReport(buf, rr);
        }

        buf.flip();
        return buf;
    }

    private static ByteBuffer encodeReceiverReport(ReceiverReport rr) {
        int length = 4 + rr.reportCount() * ReceptionReport.SIZE;
        int totalSize = HEADER_SIZE + length;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        writeHeader(buf, rr.reportCount(), RtcpPacket.PT_RR, length / 4);
        buf.putInt((int) rr.ssrc());

        for (ReceptionReport report : rr.reports()) {
            encodeReceptionReport(buf, report);
        }

        buf.flip();
        return buf;
    }

    private static void encodeReceptionReport(ByteBuffer buf, ReceptionReport rr) {
        buf.putInt((int) rr.ssrc());
        // Fraction lost (8 bits) + cumulative lost (24 bits)
        int lostWord = ((rr.fractionLost() & 0xFF) << 24)
                | (rr.cumulativeLost() & 0x00FFFFFF);
        buf.putInt(lostWord);
        buf.putInt((int) rr.highestSeqReceived());
        buf.putInt((int) rr.jitter());
        buf.putInt((int) rr.lastSR());
        buf.putInt((int) rr.delaySinceLastSR());
    }

    private static ByteBuffer encodeSourceDescription(SourceDescription sdes) {
        // Calculate size: for each chunk, SSRC(4) + items + END + padding
        int bodySize = 0;
        for (SdesChunk chunk : sdes.chunks()) {
            bodySize += 4; // SSRC
            for (SdesItem item : chunk.items()) {
                bodySize += 2 + item.value().getBytes(StandardCharsets.UTF_8).length; // type + length + value
            }
            bodySize += 1; // END item (type=0)
            // Pad to 4-byte boundary
            int pad = (4 - (bodySize % 4)) % 4;
            bodySize += pad;
        }

        int totalSize = HEADER_SIZE + bodySize;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        writeHeader(buf, sdes.sourceCount(), RtcpPacket.PT_SDES, bodySize / 4);

        for (SdesChunk chunk : sdes.chunks()) {
            buf.putInt((int) chunk.ssrc());
            for (SdesItem item : chunk.items()) {
                byte[] valueBytes = item.value().getBytes(StandardCharsets.UTF_8);
                buf.put((byte) item.type().code());
                buf.put((byte) valueBytes.length);
                buf.put(valueBytes);
            }
            buf.put((byte) 0); // END
            // Pad to 4-byte boundary from start of chunk
            int pos = buf.position();
            int pad = (4 - (pos % 4)) % 4;
            for (int i = 0; i < pad; i++) {
                buf.put((byte) 0);
            }
        }

        buf.flip();
        return buf;
    }

    private static ByteBuffer encodeGoodbye(Goodbye bye) {
        int bodySize = bye.sourceCount() * 4;
        if (bye.reason().isPresent()) {
            byte[] reasonBytes = bye.reason().get().getBytes(StandardCharsets.UTF_8);
            bodySize += 1 + reasonBytes.length; // length byte + reason
            // Pad to 4-byte boundary
            int pad = (4 - ((1 + reasonBytes.length) % 4)) % 4;
            bodySize += pad;
        }

        int totalSize = HEADER_SIZE + bodySize;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        writeHeader(buf, bye.sourceCount(), RtcpPacket.PT_BYE, bodySize / 4);

        for (long ssrc : bye.ssrcList()) {
            buf.putInt((int) ssrc);
        }

        bye.reason().ifPresent(reason -> {
            byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
            buf.put((byte) reasonBytes.length);
            buf.put(reasonBytes);
            int pad = (4 - ((1 + reasonBytes.length) % 4)) % 4;
            for (int i = 0; i < pad; i++) {
                buf.put((byte) 0);
            }
        });

        buf.flip();
        return buf;
    }

    private static ByteBuffer encodeApplicationDefined(ApplicationDefined app) {
        byte[] nameBytes = app.name().getBytes(StandardCharsets.US_ASCII);
        byte[] data = app.data();
        int bodySize = 4 + 4 + data.length; // SSRC + name + data
        int totalSize = HEADER_SIZE + bodySize;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        writeHeader(buf, app.subtype(), RtcpPacket.PT_APP, bodySize / 4);
        buf.putInt((int) app.ssrc());
        buf.put(nameBytes);
        buf.put(data);

        buf.flip();
        return buf;
    }

    private static void writeHeader(ByteBuffer buf, int count, int packetType, int lengthInWords) {
        int firstByte = (VERSION << 6) | (count & 0x1F);
        buf.put((byte) firstByte);
        buf.put((byte) packetType);
        buf.putShort((short) lengthInWords);
    }

    // ---- Decoding ----

    /**
     * Decodes a compound RTCP packet from a {@link ByteBuffer}.
     *
     * <p>Reads all RTCP packets from the current position to the limit.
     *
     * @param buf the source buffer
     * @return the decoded compound packet
     */
    public static CompoundPacket decodeCompound(ByteBuffer buf) {
        List<RtcpPacket> packets = new ArrayList<>();
        while (buf.remaining() >= HEADER_SIZE) {
            packets.add(decode(buf));
        }
        return new CompoundPacket(packets);
    }

    /**
     * Decodes a single RTCP packet from a {@link ByteBuffer}.
     *
     * @param buf the source buffer
     * @return the decoded RTCP packet
     * @throws IllegalArgumentException if the data is invalid
     */
    public static RtcpPacket decode(ByteBuffer buf) {
        if (buf.remaining() < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Buffer too small for RTCP header: " + buf.remaining() + " bytes");
        }

        int firstByte = buf.get() & 0xFF;
        int version = (firstByte >> 6) & 0x03;
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported RTCP version: " + version);
        }
        int count = firstByte & 0x1F;
        int packetType = buf.get() & 0xFF;
        int lengthInWords = buf.getShort() & 0xFFFF;
        int lengthInBytes = lengthInWords * 4;

        // Slice the buffer to this packet's payload
        if (buf.remaining() < lengthInBytes) {
            throw new IllegalArgumentException(
                    "Buffer too small for RTCP payload: need " + lengthInBytes
                            + ", have " + buf.remaining());
        }
        int endPos = buf.position() + lengthInBytes;

        RtcpPacket result = switch (packetType) {
            case RtcpPacket.PT_SR -> decodeSenderReport(buf, count);
            case RtcpPacket.PT_RR -> decodeReceiverReport(buf, count);
            case RtcpPacket.PT_SDES -> decodeSourceDescription(buf, count, endPos);
            case RtcpPacket.PT_BYE -> decodeGoodbye(buf, count, endPos);
            case RtcpPacket.PT_APP -> decodeApplicationDefined(buf, count, endPos);
            default -> throw new IllegalArgumentException("Unknown RTCP packet type: " + packetType);
        };

        // Ensure buffer position is at the end of this packet
        buf.position(endPos);
        return result;
    }

    private static SenderReport decodeSenderReport(ByteBuffer buf, int reportCount) {
        long ssrc = buf.getInt() & 0xFFFFFFFFL;
        long ntpTimestamp = buf.getLong();
        long rtpTimestamp = buf.getInt() & 0xFFFFFFFFL;
        long senderPacketCount = buf.getInt() & 0xFFFFFFFFL;
        long senderOctetCount = buf.getInt() & 0xFFFFFFFFL;

        List<ReceptionReport> reports = decodeReceptionReports(buf, reportCount);
        return new SenderReport(ssrc, ntpTimestamp, rtpTimestamp,
                senderPacketCount, senderOctetCount, reports);
    }

    private static ReceiverReport decodeReceiverReport(ByteBuffer buf, int reportCount) {
        long ssrc = buf.getInt() & 0xFFFFFFFFL;
        List<ReceptionReport> reports = decodeReceptionReports(buf, reportCount);
        return new ReceiverReport(ssrc, reports);
    }

    private static List<ReceptionReport> decodeReceptionReports(ByteBuffer buf, int count) {
        List<ReceptionReport> reports = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long ssrc = buf.getInt() & 0xFFFFFFFFL;
            int lostWord = buf.getInt();
            int fractionLost = (lostWord >> 24) & 0xFF;
            int cumulativeLost = lostWord & 0x00FFFFFF;
            // Sign-extend 24-bit value
            if ((cumulativeLost & 0x800000) != 0) {
                cumulativeLost |= 0xFF000000;
            }
            long highestSeq = buf.getInt() & 0xFFFFFFFFL;
            long jitter = buf.getInt() & 0xFFFFFFFFL;
            long lastSR = buf.getInt() & 0xFFFFFFFFL;
            long dlsr = buf.getInt() & 0xFFFFFFFFL;
            reports.add(new ReceptionReport(ssrc, fractionLost, cumulativeLost,
                    highestSeq, jitter, lastSR, dlsr));
        }
        return reports;
    }

    private static SourceDescription decodeSourceDescription(ByteBuffer buf, int sourceCount, int endPos) {
        List<SdesChunk> chunks = new ArrayList<>(sourceCount);
        for (int i = 0; i < sourceCount; i++) {
            long ssrc = buf.getInt() & 0xFFFFFFFFL;
            List<SdesItem> items = new ArrayList<>();
            while (buf.position() < endPos) {
                int type = buf.get() & 0xFF;
                if (type == 0) {
                    // END item -- skip padding to 4-byte boundary
                    int pos = buf.position();
                    int pad = (4 - (pos % 4)) % 4;
                    buf.position(pos + pad);
                    break;
                }
                int length = buf.get() & 0xFF;
                byte[] valueBytes = new byte[length];
                buf.get(valueBytes);
                items.add(new SdesItem(SdesItem.Type.fromCode(type),
                        new String(valueBytes, StandardCharsets.UTF_8)));
            }
            chunks.add(new SdesChunk(ssrc, items));
        }
        return new SourceDescription(chunks);
    }

    private static Goodbye decodeGoodbye(ByteBuffer buf, int sourceCount, int endPos) {
        List<Long> ssrcList = new ArrayList<>(sourceCount);
        for (int i = 0; i < sourceCount; i++) {
            ssrcList.add(buf.getInt() & 0xFFFFFFFFL);
        }
        Optional<String> reason = Optional.empty();
        if (buf.position() < endPos) {
            int reasonLen = buf.get() & 0xFF;
            if (reasonLen > 0) {
                byte[] reasonBytes = new byte[reasonLen];
                buf.get(reasonBytes);
                reason = Optional.of(new String(reasonBytes, StandardCharsets.UTF_8));
            }
        }
        return new Goodbye(ssrcList, reason);
    }

    private static ApplicationDefined decodeApplicationDefined(ByteBuffer buf, int subtype, int endPos) {
        long ssrc = buf.getInt() & 0xFFFFFFFFL;
        byte[] nameBytes = new byte[4];
        buf.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.US_ASCII);

        int dataLen = endPos - buf.position();
        byte[] data = new byte[dataLen];
        if (dataLen > 0) {
            buf.get(data);
        }
        return new ApplicationDefined(ssrc, subtype, name, data);
    }
}
