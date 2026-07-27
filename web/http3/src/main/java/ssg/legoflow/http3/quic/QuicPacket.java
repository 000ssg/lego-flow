package ssg.legoflow.http3.quic;

import java.util.List;

/**
 * Represents a QUIC packet containing one or more frames.
 *
 * <p>A packet carries a type (determining the header format), connection
 * identifiers, a monotonically increasing packet number, the frames,
 * and the QUIC version (relevant for long-header packets).</p>
 *
 * @param type         the packet type
 * @param connectionId the destination connection ID
 * @param packetNumber the packet number
 * @param frames       the list of frames in this packet
 * @param version      the QUIC version (1 for RFC 9000)
 * @since 1.0.0
 */
public record QuicPacket(
        QuicPacketType type,
        long connectionId,
        long packetNumber,
        List<QuicFrame> frames,
        int version
) {

    /**
     * Creates a QuicPacket with a defensive copy of the frames list.
     *
     * @param type         the packet type
     * @param connectionId the destination connection ID
     * @param packetNumber the packet number
     * @param frames       the list of frames in this packet
     * @param version      the QUIC version
     */
    public QuicPacket {
        frames = List.copyOf(frames);
    }
}
