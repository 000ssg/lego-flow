package ssg.legoflow.media.rtp.packet;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RTP fixed header plus optional CSRC list and header extension (RFC 3550 Section 5.1).
 *
 * <p>The fixed header is 12 bytes, followed by an optional list of CSRC identifiers
 * (up to 15) and an optional header extension.
 *
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |            contributing source (CSRC) identifiers             |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * @param version        the RTP version (always 2)
 * @param padding        whether the packet contains padding
 * @param extension      whether the header extension is present
 * @param marker         the marker bit
 * @param payloadType    the payload type number (0-127)
 * @param sequenceNumber the sequence number (0-65535)
 * @param timestamp      the RTP timestamp (32-bit unsigned)
 * @param ssrc           the synchronization source identifier
 * @param csrcList       the contributing source identifiers (0-15 entries)
 * @param headerExtension the optional header extension
 * @since 0.1.0
 */
public record RtpHeader(
        int version,
        boolean padding,
        boolean extension,
        boolean marker,
        int payloadType,
        int sequenceNumber,
        long timestamp,
        long ssrc,
        List<Long> csrcList,
        Optional<HeaderExtension> headerExtension
) {

    /** RTP version 2. */
    public static final int VERSION_2 = 2;

    /** Fixed header size in bytes (without CSRC or extensions). */
    public static final int FIXED_SIZE = 12;

    /** Maximum number of CSRC entries. */
    public static final int MAX_CSRC_COUNT = 15;

    /**
     * Creates an RTP header with validation.
     */
    public RtpHeader {
        Objects.requireNonNull(csrcList, "csrcList");
        Objects.requireNonNull(headerExtension, "headerExtension");
        if (version != VERSION_2) {
            throw new IllegalArgumentException("RTP version must be 2: " + version);
        }
        if (payloadType < 0 || payloadType > 127) {
            throw new IllegalArgumentException("Payload type must be 0-127: " + payloadType);
        }
        if (sequenceNumber < 0 || sequenceNumber > 0xFFFF) {
            throw new IllegalArgumentException("Sequence number must be 0-65535: " + sequenceNumber);
        }
        if (timestamp < 0 || timestamp > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Timestamp must be 0-4294967295: " + timestamp);
        }
        if (ssrc < 0 || ssrc > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("SSRC must be 0-4294967295: " + ssrc);
        }
        if (csrcList.size() > MAX_CSRC_COUNT) {
            throw new IllegalArgumentException("CSRC count must be 0-15: " + csrcList.size());
        }
        csrcList = List.copyOf(csrcList);
        if (extension && headerExtension.isEmpty()) {
            throw new IllegalArgumentException("Extension bit set but no header extension provided");
        }
    }

    /**
     * Returns the total header size in bytes, including CSRC and extension.
     *
     * @return the total header size
     */
    public int totalSize() {
        int size = FIXED_SIZE + csrcList.size() * 4;
        if (headerExtension.isPresent()) {
            var ext = headerExtension.get();
            size += 4 + ext.data().length; // 4 bytes for profile + length fields
        }
        return size;
    }

    /**
     * Returns the CSRC count (CC field).
     *
     * @return the number of CSRC identifiers
     */
    public int csrcCount() {
        return csrcList.size();
    }
}
