package ssg.legoflow.email.imap.protocol;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IMAP command tag generator and holder.
 *
 * <p>Tags are alphanumeric identifiers that correlate commands with their responses.
 * Convention uses a prefix letter followed by a zero-padded number (e.g., A001, A002).
 * The special tag {@code *} denotes untagged responses.
 *
 * @since 1.0.0
 */
public final class ImapTag {

    /** Untagged response marker. */
    public static final String UNTAGGED = "*";

    /** Continuation response marker. */
    public static final String CONTINUATION = "+";

    private final AtomicInteger counter = new AtomicInteger(0);
    private final String prefix;

    /**
     * Creates a tag generator with the given prefix.
     *
     * @param prefix the tag prefix (e.g., "A")
     */
    public ImapTag(String prefix) {
        this.prefix = Objects.requireNonNull(prefix);
    }

    /**
     * Creates a tag generator with default prefix "A".
     */
    public ImapTag() {
        this("A");
    }

    /**
     * Generates the next tag.
     *
     * @return the next tag string (e.g., "A001", "A002")
     */
    public String next() {
        int num = counter.incrementAndGet();
        return prefix + String.format("%03d", num);
    }

    /**
     * Resets the tag counter to zero.
     */
    public void reset() {
        counter.set(0);
    }

    /**
     * Returns the current counter value without incrementing.
     *
     * @return the current counter value
     */
    public int current() {
        return counter.get();
    }
}
