package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
/**
 * TXT record RDATA: one or more character strings (RFC 1035).
 *
 * <p>Each string is encoded as a length-prefixed byte sequence (max 255 bytes).
 *
 * @param strings the text strings
 * @since 0.1.0
 */
public record TxtRecord(List<String> strings) implements RData {

    public TxtRecord {
        Objects.requireNonNull(strings, "strings must not be null");
        strings = Collections.unmodifiableList(new ArrayList<>(strings));
    }

    @Override
    public RecordType type() {
        return RecordType.TXT;
    }

    /**
     * Creates a TXT record with a single string.
     *
     * @param text the text string
     * @return the TXT record
     * @since 0.1.0
     */
    public static TxtRecord of(String text) {
        return new TxtRecord(List.of(text));
    }

    /**
     * Creates a TXT record with multiple strings.
     *
     * @param texts the text strings
     * @return the TXT record
     * @since 0.1.0
     */
    public static TxtRecord of(String... texts) {
        return new TxtRecord(List.of(texts));
    }

    /**
     * Returns the concatenated text (all strings joined).
     *
     * @return the full text
     * @since 0.1.0
     */
    public String text() {
        return String.join("", strings);
    }
}
