package ssg.legoflow.email.imap.server;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maps between message sequence numbers and UIDs within a mailbox.
 *
 * <p>Sequence numbers are 1-based, dense indices into the message list.
 * UIDs are unique, monotonically increasing identifiers that persist
 * across sessions.
 *
 * @since 1.0.0
 */
public final class MessageIndex {

    private final List<Long> uidList = new CopyOnWriteArrayList<>();

    /**
     * Adds a UID to the index.
     *
     * @param uid the UID to add
     */
    public void add(long uid) {
        uidList.add(uid);
    }

    /**
     * Removes a UID from the index.
     *
     * @param uid the UID to remove
     * @return true if removed
     */
    public boolean remove(long uid) {
        return uidList.remove(Long.valueOf(uid));
    }

    /**
     * Returns the sequence number for the given UID.
     *
     * @param uid the UID
     * @return the 1-based sequence number, or -1 if not found
     */
    public int seqNumForUid(long uid) {
        int index = uidList.indexOf(uid);
        return index >= 0 ? index + 1 : -1;
    }

    /**
     * Returns the UID for the given sequence number.
     *
     * @param seqNum the 1-based sequence number
     * @return the UID
     * @throws IndexOutOfBoundsException if the sequence number is invalid
     */
    public long uidForSeqNum(int seqNum) {
        return uidList.get(seqNum - 1);
    }

    /**
     * Returns the total number of messages.
     *
     * @return the message count
     */
    public int size() {
        return uidList.size();
    }

    /**
     * Returns all UIDs in sequence order.
     *
     * @return unmodifiable list of UIDs
     */
    public List<Long> allUids() {
        return Collections.unmodifiableList(new ArrayList<>(uidList));
    }

    /**
     * Clears all entries.
     */
    public void clear() {
        uidList.clear();
    }

    /**
     * Returns true if the index contains the given UID.
     *
     * @param uid the UID to check
     * @return true if present
     */
    public boolean contains(long uid) {
        return uidList.contains(uid);
    }
}
