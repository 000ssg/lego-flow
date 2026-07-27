package ssg.legoflow.media.rtp.rtcp;

import java.util.List;
import java.util.Objects;

/**
 * RTCP Source Description (SDES) packet (RFC 3550 Section 6.5).
 *
 * <p>Contains one or more chunks, each describing a single SSRC/CSRC
 * with one or more SDES items (CNAME, NAME, EMAIL, etc.).
 *
 * @param chunks the list of SDES chunks
 * @since 1.0.0
 */
public record SourceDescription(List<SdesChunk> chunks) implements RtcpPacket {

    /**
     * Creates a source description with validation.
     */
    public SourceDescription {
        Objects.requireNonNull(chunks, "chunks");
        chunks = List.copyOf(chunks);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("SDES must contain at least one chunk");
        }
        if (chunks.size() > 31) {
            throw new IllegalArgumentException("Maximum 31 SDES chunks: " + chunks.size());
        }
    }

    @Override
    public int packetType() {
        return PT_SDES;
    }

    @Override
    public long ssrc() {
        return chunks.getFirst().ssrc();
    }

    /**
     * Returns the source count (SC field).
     *
     * @return the number of chunks
     */
    public int sourceCount() {
        return chunks.size();
    }
}
