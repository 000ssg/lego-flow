package ssg.legoflow.media.sip.header;

import ssg.legoflow.media.sip.protocol.SipMethod;
import java.util.Objects;
/**
 * Parsed SIP CSeq header per RFC 3261 section 20.16.
 *
 * <p>Format: {@code sequence-number method}
 *
 * @param sequence the sequence number
 * @param method   the request method
 * @since 0.1.0
 */
public record CSeqHeader(long sequence, SipMethod method) {

    /**
     * Creates a CSeq header.
     *
     * @since 0.1.0
     */
    public CSeqHeader {
        Objects.requireNonNull(method, "method");
        if (sequence < 0) {
            throw new IllegalArgumentException("CSeq sequence must be non-negative: " + sequence);
        }
    }

    /**
     * Parses a CSeq header value string.
     *
     * @param value the CSeq value (e.g., "1 INVITE")
     * @return the parsed CSeq header
     * @throws IllegalArgumentException if the format is invalid
     * @since 0.1.0
     */
    public static CSeqHeader parse(String value) {
        Objects.requireNonNull(value, "value");
        String[] parts = value.strip().split("\\s+", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CSeq header: " + value);
        }
        long seq = Long.parseLong(parts[0]);
        SipMethod method = SipMethod.fromName(parts[1]);
        return new CSeqHeader(seq, method);
    }

    /**
     * Formats this CSeq header as a string value.
     *
     * @return the formatted value
     * @since 0.1.0
     */
    public String format() {
        return sequence + " " + method.name();
    }

    @Override
    public String toString() {
        return format();
    }
}
