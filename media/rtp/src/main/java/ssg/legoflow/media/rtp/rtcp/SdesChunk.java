package ssg.legoflow.media.rtp.rtcp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SDES chunk containing items for a single SSRC/CSRC (RFC 3550 Section 6.5).
 *
 * @param ssrc  the SSRC/CSRC identifier
 * @param items the list of SDES items
 * @since 1.0.0
 */
public record SdesChunk(long ssrc, List<SdesItem> items) {

    /**
     * Creates an SDES chunk with validation.
     */
    public SdesChunk {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    /**
     * Returns the CNAME item value, if present.
     *
     * @return the CNAME value
     */
    public Optional<String> cname() {
        return items.stream()
                .filter(item -> item.type() == SdesItem.Type.CNAME)
                .map(SdesItem::value)
                .findFirst();
    }
}
