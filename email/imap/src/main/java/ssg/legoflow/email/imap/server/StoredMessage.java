package ssg.legoflow.email.imap.server;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A message stored in a mailbox with UID, flags, internal date, and MIME content.
 *
 * <p>Flags are mutable and thread-safe. The raw content is the full RFC 5322 message.
 *
 * @since 0.1.0
 */
public final class StoredMessage {

    private final long uid;
    private final Instant internalDate;
    private final byte[] content;
    private final Set<String> flags;
    private volatile long modSeq;

    /**
     * Creates a stored message.
     *
     * @param uid          the unique identifier within the mailbox
     * @param internalDate the date/time the message was received
     * @param content      the raw RFC 5322 message bytes
     * @param flags        initial flags
     */
    public StoredMessage(long uid, Instant internalDate, byte[] content, Set<String> flags) {
        this.uid = uid;
        this.internalDate = Objects.requireNonNull(internalDate);
        this.content = Objects.requireNonNull(content).clone();
        this.flags = new CopyOnWriteArraySet<>(flags != null ? flags : Set.of());
        this.modSeq = 0;
    }

    /** Returns the message UID. */
    public long uid() { return uid; }

    /** Returns the internal date. */
    public Instant internalDate() { return internalDate; }

    /** Returns a copy of the raw message content. */
    public byte[] content() { return content.clone(); }

    /** Returns the raw content size in octets. */
    public int size() { return content.length; }

    /** Returns the raw content as a string (UTF-8). */
    public String contentAsString() { return new String(content, java.nio.charset.StandardCharsets.UTF_8); }

    /** Returns an unmodifiable view of the current flags. */
    public Set<String> flags() { return Collections.unmodifiableSet(flags); }

    /** Returns true if the message has the given flag. */
    public boolean hasFlag(String flag) { return flags.contains(flag); }

    /**
     * Adds a flag to this message.
     *
     * @param flag the flag to add
     * @return true if the flag was added (not already present)
     */
    public boolean addFlag(String flag) { return flags.add(flag); }

    /**
     * Removes a flag from this message.
     *
     * @param flag the flag to remove
     * @return true if the flag was removed
     */
    public boolean removeFlag(String flag) { return flags.remove(flag); }

    /**
     * Replaces all flags with the given set.
     *
     * @param newFlags the new flags
     */
    public void setFlags(Set<String> newFlags) {
        flags.clear();
        flags.addAll(newFlags);
    }

    /** Returns the modification sequence number. */
    public long modSeq() { return modSeq; }

    /**
     * Sets the modification sequence number.
     *
     * @param modSeq the new mod-sequence
     */
    public void setModSeq(long modSeq) { this.modSeq = modSeq; }

    /**
     * Extracts the value of a header field from the raw content.
     *
     * @param fieldName the header field name (case-insensitive)
     * @return the header value, or null if not found
     */
    public String getHeader(String fieldName) {
        String text = contentAsString();
        // Headers end at first blank line
        int headerEnd = text.indexOf("\r\n\r\n");
        if (headerEnd < 0) headerEnd = text.indexOf("\n\n");
        String headers = headerEnd >= 0 ? text.substring(0, headerEnd) : text;

        String search = fieldName.toLowerCase() + ":";
        String[] lines = headers.split("\r?\n");
        StringBuilder value = null;
        for (String line : lines) {
            if (value != null) {
                // Continuation line (starts with whitespace)
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    value.append(" ").append(line.trim());
                    continue;
                } else {
                    return value.toString().trim();
                }
            }
            if (line.toLowerCase().startsWith(search)) {
                value = new StringBuilder(line.substring(search.length()));
            }
        }
        return value != null ? value.toString().trim() : null;
    }

    /**
     * Returns the message body (after headers).
     *
     * @return the body text, or empty string if no body
     */
    public String getBody() {
        String text = contentAsString();
        int headerEnd = text.indexOf("\r\n\r\n");
        if (headerEnd >= 0) return text.substring(headerEnd + 4);
        headerEnd = text.indexOf("\n\n");
        if (headerEnd >= 0) return text.substring(headerEnd + 2);
        return "";
    }

    /**
     * Returns the header section of the message.
     *
     * @return the headers text
     */
    public String getHeaders() {
        String text = contentAsString();
        int headerEnd = text.indexOf("\r\n\r\n");
        if (headerEnd >= 0) return text.substring(0, headerEnd + 2);
        headerEnd = text.indexOf("\n\n");
        if (headerEnd >= 0) return text.substring(0, headerEnd + 1);
        return text;
    }

    @Override
    public String toString() {
        return "StoredMessage{uid=" + uid + ", flags=" + flags + ", size=" + content.length + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredMessage that)) return false;
        return uid == that.uid;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(uid);
    }
}
