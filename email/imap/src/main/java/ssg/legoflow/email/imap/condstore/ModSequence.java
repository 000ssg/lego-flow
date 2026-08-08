package ssg.legoflow.email.imap.condstore;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Modification sequence tracker for CONDSTORE (RFC 7162).
 *
 * <p>Each mailbox has a monotonically increasing modification sequence counter.
 * Every flag change or message addition increments the counter, and the new
 * value is associated with the affected message.
 *
 * @since 0.1.0
 */
public final class ModSequence {

    private final AtomicLong counter;

    /**
     * Creates a modification sequence tracker starting at the given value.
     *
     * @param initial the initial value
     */
    public ModSequence(long initial) {
        this.counter = new AtomicLong(initial);
    }

    /**
     * Creates a modification sequence tracker starting at 0.
     */
    public ModSequence() {
        this(0);
    }

    /**
     * Returns the current highest modification sequence.
     *
     * @return the current value
     */
    public long current() {
        return counter.get();
    }

    /**
     * Increments and returns the next modification sequence.
     *
     * @return the next value
     */
    public long next() {
        return counter.incrementAndGet();
    }

    /**
     * Updates the current value if the given value is higher.
     *
     * @param value the value to compare
     * @return the resulting value
     */
    public long updateIfHigher(long value) {
        return counter.updateAndGet(current -> Math.max(current, value));
    }

    @Override
    public String toString() {
        return "ModSequence{" + counter.get() + "}";
    }
}
