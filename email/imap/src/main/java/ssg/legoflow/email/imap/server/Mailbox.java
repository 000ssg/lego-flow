package ssg.legoflow.email.imap.server;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An IMAP mailbox containing messages with UIDs, flags, and UIDVALIDITY.
 *
 * <p>Thread-safe for concurrent access. Maintains a message index
 * for sequence number to UID mapping, tracks permanent and session flags,
 * and supports CONDSTORE modification sequences.
 *
 * @since 0.1.0
 */
public final class Mailbox {

    /** Standard system flags. */
    public static final List<String> SYSTEM_FLAGS = List.of(
            "\\Seen", "\\Answered", "\\Flagged", "\\Deleted", "\\Draft");

    private final String name;
    private final long uidValidity;
    private final AtomicLong nextUid = new AtomicLong(1);
    private final AtomicLong modSequence = new AtomicLong(0);
    private final Map<Long, StoredMessage> messages = new ConcurrentHashMap<>();
    private final MessageIndex index = new MessageIndex();
    private final Set<String> subscribedUsers = ConcurrentHashMap.newKeySet();
    private final List<String> permanentFlags;
    private volatile boolean readOnly = false;

    /**
     * Creates a new mailbox.
     *
     * @param name        the mailbox name (e.g., "INBOX")
     * @param uidValidity the UIDVALIDITY value
     */
    public Mailbox(String name, long uidValidity) {
        this.name = Objects.requireNonNull(name);
        this.uidValidity = uidValidity;
        this.permanentFlags = new ArrayList<>(SYSTEM_FLAGS);
        this.permanentFlags.add("\\*"); // Allow custom keywords
    }

    /** Returns the mailbox name. */
    public String name() { return name; }

    /** Returns the UIDVALIDITY value. */
    public long uidValidity() { return uidValidity; }

    /** Returns the next UID value (without incrementing). */
    public long uidNext() { return nextUid.get(); }

    /** Returns the current modification sequence. */
    public long highestModSeq() { return modSequence.get(); }

    /** Returns the number of messages. */
    public int messageCount() { return messages.size(); }

    /** Returns the number of messages with \Recent flag. */
    public int recentCount() {
        return (int) messages.values().stream()
                .filter(m -> m.hasFlag("\\Recent")).count();
    }

    /** Returns the number of unseen messages. */
    public int unseenCount() {
        return (int) messages.values().stream()
                .filter(m -> !m.hasFlag("\\Seen")).count();
    }

    /** Returns the sequence number of the first unseen message, or -1. */
    public int firstUnseen() {
        List<Long> uids = index.allUids();
        for (int i = 0; i < uids.size(); i++) {
            StoredMessage msg = messages.get(uids.get(i));
            if (msg != null && !msg.hasFlag("\\Seen")) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Returns the permanent flags list. */
    public List<String> permanentFlags() { return Collections.unmodifiableList(permanentFlags); }

    /** Returns whether the mailbox is read-only. */
    public boolean isReadOnly() { return readOnly; }

    /** Sets the mailbox read-only mode. */
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    /** Returns the message index. */
    public MessageIndex index() { return index; }

    /**
     * Appends a message to the mailbox.
     *
     * @param content      the raw message content
     * @param flags        initial flags
     * @param internalDate the internal date (null for current time)
     * @return the stored message
     */
    public StoredMessage append(byte[] content, Set<String> flags, Instant internalDate) {
        long uid = nextUid.getAndIncrement();
        long modSeq = modSequence.incrementAndGet();
        Instant date = internalDate != null ? internalDate : Instant.now();
        StoredMessage msg = new StoredMessage(uid, date, content, flags);
        msg.setModSeq(modSeq);
        messages.put(uid, msg);
        index.add(uid);
        return msg;
    }

    /**
     * Returns the message with the given UID.
     *
     * @param uid the message UID
     * @return the message, or null if not found
     */
    public StoredMessage getMessage(long uid) {
        return messages.get(uid);
    }

    /**
     * Returns the message at the given sequence number.
     *
     * @param seqNum the 1-based sequence number
     * @return the message, or null if not found
     */
    public StoredMessage getMessageBySeqNum(int seqNum) {
        try {
            long uid = index.uidForSeqNum(seqNum);
            return messages.get(uid);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Returns all messages in sequence order.
     *
     * @return the list of messages
     */
    public List<StoredMessage> allMessages() {
        List<StoredMessage> result = new ArrayList<>();
        for (long uid : index.allUids()) {
            StoredMessage msg = messages.get(uid);
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * Expunges messages with the \Deleted flag.
     *
     * @return the list of expunged UIDs
     */
    public List<Long> expunge() {
        List<Long> expunged = new ArrayList<>();
        for (Long uid : index.allUids()) {
            StoredMessage msg = messages.get(uid);
            if (msg != null && msg.hasFlag("\\Deleted")) {
                messages.remove(uid);
                index.remove(uid);
                expunged.add(uid);
            }
        }
        return expunged;
    }

    /**
     * Copies a message to another mailbox.
     *
     * @param uid    the UID of the message to copy
     * @param target the target mailbox
     * @return the new UID in the target mailbox, or -1 if source not found
     */
    public long copyMessage(long uid, Mailbox target) {
        StoredMessage src = messages.get(uid);
        if (src == null) return -1;
        StoredMessage copied = target.append(src.content(), src.flags(), src.internalDate());
        return copied.uid();
    }

    /**
     * Moves a message to another mailbox (copy + delete).
     *
     * @param uid    the UID of the message to move
     * @param target the target mailbox
     * @return the new UID in the target mailbox, or -1 if source not found
     */
    public long moveMessage(long uid, Mailbox target) {
        long newUid = copyMessage(uid, target);
        if (newUid >= 0) {
            messages.remove(uid);
            index.remove(uid);
        }
        return newUid;
    }

    /**
     * Updates flags on a message and increments the mod-sequence.
     *
     * @param uid   the message UID
     * @param flags the new flags
     * @param mode  the flag operation mode
     * @return the updated message, or null if not found
     */
    public StoredMessage storeFlags(long uid, Set<String> flags, FlagOperation mode) {
        StoredMessage msg = messages.get(uid);
        if (msg == null) return null;

        switch (mode) {
            case SET -> msg.setFlags(flags);
            case ADD -> flags.forEach(msg::addFlag);
            case REMOVE -> flags.forEach(msg::removeFlag);
        }
        msg.setModSeq(modSequence.incrementAndGet());
        return msg;
    }

    /**
     * Checks if a user is subscribed to this mailbox.
     *
     * @param user the user identifier
     * @return true if subscribed
     */
    public boolean isSubscribed(String user) {
        return subscribedUsers.contains(user);
    }

    /**
     * Subscribes a user to this mailbox.
     *
     * @param user the user identifier
     */
    public void subscribe(String user) {
        subscribedUsers.add(user);
    }

    /**
     * Unsubscribes a user from this mailbox.
     *
     * @param user the user identifier
     */
    public void unsubscribe(String user) {
        subscribedUsers.remove(user);
    }

    @Override
    public String toString() {
        return "Mailbox{name='" + name + "', messages=" + messages.size()
                + ", uidNext=" + nextUid.get() + ", uidValidity=" + uidValidity + "}";
    }

    /**
     * Flag operation modes for STORE command.
     */
    public enum FlagOperation {
        /** Replace all flags. */
        SET,
        /** Add flags. */
        ADD,
        /** Remove flags. */
        REMOVE
    }
}
