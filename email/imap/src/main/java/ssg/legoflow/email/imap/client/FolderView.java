package ssg.legoflow.email.imap.client;

import java.util.List;
import java.util.Objects;

/**
 * Represents the state of a selected IMAP mailbox from the client perspective.
 *
 * <p>Contains mailbox metadata received from the server's SELECT/EXAMINE response:
 * message count, recent count, flags, UID validity, UID next, and access mode.
 *
 * @since 0.1.0
 */
public final class FolderView {

    private final String name;
    private volatile int messageCount;
    private volatile int recentCount;
    private volatile long uidValidity;
    private volatile long uidNext;
    private volatile long highestModSeq;
    private volatile boolean readOnly;
    private volatile List<String> flags;
    private volatile List<String> permanentFlags;
    private volatile int firstUnseen;

    /**
     * Creates a folder view.
     *
     * @param name the mailbox name
     */
    public FolderView(String name) {
        this.name = Objects.requireNonNull(name);
        this.flags = List.of();
        this.permanentFlags = List.of();
    }

    /** Returns the mailbox name. */
    public String name() { return name; }

    /** Returns the total message count. */
    public int messageCount() { return messageCount; }

    /** Sets the message count. */
    public void setMessageCount(int count) { this.messageCount = count; }

    /** Returns the recent message count. */
    public int recentCount() { return recentCount; }

    /** Sets the recent count. */
    public void setRecentCount(int count) { this.recentCount = count; }

    /** Returns the UIDVALIDITY value. */
    public long uidValidity() { return uidValidity; }

    /** Sets the UID validity. */
    public void setUidValidity(long value) { this.uidValidity = value; }

    /** Returns the UIDNEXT value. */
    public long uidNext() { return uidNext; }

    /** Sets the UID next. */
    public void setUidNext(long value) { this.uidNext = value; }

    /** Returns the highest modification sequence. */
    public long highestModSeq() { return highestModSeq; }

    /** Sets the highest mod-seq. */
    public void setHighestModSeq(long value) { this.highestModSeq = value; }

    /** Returns true if the mailbox is opened read-only (EXAMINE). */
    public boolean isReadOnly() { return readOnly; }

    /** Sets the read-only mode. */
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    /** Returns the mailbox flags. */
    public List<String> flags() { return flags; }

    /** Sets the mailbox flags. */
    public void setFlags(List<String> flags) { this.flags = List.copyOf(flags); }

    /** Returns the permanent flags. */
    public List<String> permanentFlags() { return permanentFlags; }

    /** Sets the permanent flags. */
    public void setPermanentFlags(List<String> flags) { this.permanentFlags = List.copyOf(flags); }

    /** Returns the sequence number of the first unseen message. */
    public int firstUnseen() { return firstUnseen; }

    /** Sets the first unseen. */
    public void setFirstUnseen(int value) { this.firstUnseen = value; }

    @Override
    public String toString() {
        return "FolderView{name='" + name + "', messages=" + messageCount
                + ", recent=" + recentCount + ", uidNext=" + uidNext
                + ", uidValidity=" + uidValidity + ", readOnly=" + readOnly + "}";
    }
}
