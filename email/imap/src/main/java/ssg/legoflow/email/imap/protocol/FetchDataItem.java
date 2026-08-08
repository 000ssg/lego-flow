package ssg.legoflow.email.imap.protocol;

import java.util.Objects;

/**
 * Specifies what data to retrieve in a FETCH command.
 *
 * <p>Data items include FLAGS, INTERNALDATE, RFC822.SIZE, ENVELOPE,
 * BODY, BODYSTRUCTURE, and BODY[section] with optional partial ranges.
 * BODY.PEEK[...] fetches without setting the {@code \Seen} flag.
 *
 * @since 0.1.0
 */
public final class FetchDataItem {

    /** Fetch message flags. */
    public static final FetchDataItem FLAGS = new FetchDataItem("FLAGS", null, false, -1, -1);

    /** Fetch internal date. */
    public static final FetchDataItem INTERNALDATE = new FetchDataItem("INTERNALDATE", null, false, -1, -1);

    /** Fetch RFC822 message size. */
    public static final FetchDataItem RFC822_SIZE = new FetchDataItem("RFC822.SIZE", null, false, -1, -1);

    /** Fetch envelope structure. */
    public static final FetchDataItem ENVELOPE = new FetchDataItem("ENVELOPE", null, false, -1, -1);

    /** Fetch full body structure. */
    public static final FetchDataItem BODYSTRUCTURE = new FetchDataItem("BODYSTRUCTURE", null, false, -1, -1);

    /** Fetch body structure (non-extension). */
    public static final FetchDataItem BODY = new FetchDataItem("BODY", null, false, -1, -1);

    /** Fetch complete RFC822 message. */
    public static final FetchDataItem RFC822 = new FetchDataItem("RFC822", null, false, -1, -1);

    /** Fetch RFC822 header. */
    public static final FetchDataItem RFC822_HEADER = new FetchDataItem("RFC822.HEADER", null, false, -1, -1);

    /** Fetch RFC822 text. */
    public static final FetchDataItem RFC822_TEXT = new FetchDataItem("RFC822.TEXT", null, false, -1, -1);

    /** Fetch UID. */
    public static final FetchDataItem UID = new FetchDataItem("UID", null, false, -1, -1);

    private final String name;
    private final String section;
    private final boolean peek;
    private final long partialOffset;
    private final long partialLength;

    private FetchDataItem(String name, String section, boolean peek, long partialOffset, long partialLength) {
        this.name = name;
        this.section = section;
        this.peek = peek;
        this.partialOffset = partialOffset;
        this.partialLength = partialLength;
    }

    /**
     * Creates a BODY[section] fetch item.
     *
     * @param section the MIME section (e.g., "1", "1.2", "HEADER", "TEXT", "HEADER.FIELDS (Subject)")
     * @return the fetch item
     */
    public static FetchDataItem bodySection(String section) {
        return new FetchDataItem("BODY", section, false, -1, -1);
    }

    /**
     * Creates a BODY.PEEK[section] fetch item (does not set \Seen).
     *
     * @param section the MIME section
     * @return the fetch item
     */
    public static FetchDataItem bodyPeek(String section) {
        return new FetchDataItem("BODY", section, true, -1, -1);
    }

    /**
     * Creates a partial fetch item with an offset and length.
     *
     * @param section the MIME section
     * @param peek    true for BODY.PEEK
     * @param offset  the byte offset
     * @param length  the number of bytes
     * @return the fetch item
     */
    public static FetchDataItem partial(String section, boolean peek, long offset, long length) {
        return new FetchDataItem("BODY", section, peek, offset, length);
    }

    /** Returns the data item name (e.g., "FLAGS", "BODY", "ENVELOPE"). */
    public String name() { return name; }

    /** Returns the section specifier for BODY fetches, or null. */
    public String section() { return section; }

    /** Returns true if this is a BODY.PEEK fetch (does not set \Seen). */
    public boolean isPeek() { return peek; }

    /** Returns true if this is a partial fetch. */
    public boolean isPartial() { return partialOffset >= 0; }

    /** Returns the partial fetch byte offset, or -1 if not partial. */
    public long partialOffset() { return partialOffset; }

    /** Returns the partial fetch byte length, or -1 if not partial. */
    public long partialLength() { return partialLength; }

    /** Returns true if this is a BODY[section] or BODY.PEEK[section] fetch. */
    public boolean hasSection() { return section != null; }

    /**
     * Formats this data item for the IMAP wire protocol.
     *
     * @return the formatted string
     */
    public String toWire() {
        if (section == null && !peek) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        if (peek) {
            sb.append(".PEEK");
        }
        sb.append('[');
        if (section != null) {
            sb.append(section);
        }
        sb.append(']');
        if (isPartial()) {
            sb.append('<').append(partialOffset).append('.').append(partialLength).append('>');
        }
        return sb.toString();
    }

    /**
     * Parses a FETCH data item specification from protocol text.
     *
     * @param text the data item text
     * @return the parsed fetch data item
     */
    public static FetchDataItem parse(String text) {
        String upper = text.toUpperCase();

        // Simple items
        return switch (upper) {
            case "FLAGS" -> FLAGS;
            case "INTERNALDATE" -> INTERNALDATE;
            case "RFC822.SIZE" -> RFC822_SIZE;
            case "ENVELOPE" -> ENVELOPE;
            case "BODYSTRUCTURE" -> BODYSTRUCTURE;
            case "RFC822" -> RFC822;
            case "RFC822.HEADER" -> RFC822_HEADER;
            case "RFC822.TEXT" -> RFC822_TEXT;
            case "UID" -> UID;
            default -> parseBodySection(text);
        };
    }

    private static FetchDataItem parseBodySection(String text) {
        String upper = text.toUpperCase();
        boolean peek = upper.contains("BODY.PEEK");
        int bracketStart = text.indexOf('[');
        int bracketEnd = text.indexOf(']');

        if (bracketStart < 0 || bracketEnd < 0) {
            if (upper.equals("BODY")) {
                return BODY;
            }
            return new FetchDataItem(text, null, false, -1, -1);
        }

        String section = text.substring(bracketStart + 1, bracketEnd);
        if (section.isEmpty()) {
            section = null;
        }

        // Check for partial <offset.length>
        long offset = -1;
        long length = -1;
        int angleStart = text.indexOf('<', bracketEnd);
        int angleEnd = text.indexOf('>', bracketEnd);
        if (angleStart >= 0 && angleEnd > angleStart) {
            String partial = text.substring(angleStart + 1, angleEnd);
            String[] parts = partial.split("\\.");
            offset = Long.parseLong(parts[0]);
            length = Long.parseLong(parts[1]);
        }

        return new FetchDataItem("BODY", section, peek, offset, length);
    }

    @Override
    public String toString() {
        return toWire();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FetchDataItem that)) return false;
        return peek == that.peek
                && partialOffset == that.partialOffset
                && partialLength == that.partialLength
                && Objects.equals(name, that.name)
                && Objects.equals(section, that.section);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, section, peek, partialOffset, partialLength);
    }
}
