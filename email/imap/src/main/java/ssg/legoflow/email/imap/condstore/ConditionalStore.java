package ssg.legoflow.email.imap.condstore;

import ssg.legoflow.email.imap.server.Mailbox;
import ssg.legoflow.email.imap.server.StoredMessage;
import java.util.*;
/**
 * CONDSTORE extension support (RFC 7162).
 *
 * <p>Provides conditional flag updates using UNCHANGEDSINCE modification
 * sequence values, and MODSEQ-based search and fetch filtering.
 *
 * @since 0.1.0
 */
public final class ConditionalStore {

    private ConditionalStore() {
    }

    /**
     * Conditionally stores flags on a message only if its mod-sequence
     * has not changed since the given value.
     *
     * @param mailbox        the mailbox
     * @param uid            the message UID
     * @param flags          the flags to set
     * @param operation      the flag operation
     * @param unchangedSince the UNCHANGEDSINCE value
     * @return the updated message, or null if the precondition failed
     */
    public static StoredMessage conditionalStore(Mailbox mailbox, long uid,
                                                  Set<String> flags,
                                                  Mailbox.FlagOperation operation,
                                                  long unchangedSince) {
        StoredMessage msg = mailbox.getMessage(uid);
        if (msg == null) return null;

        // Check precondition
        if (msg.modSeq() > unchangedSince) {
            return null; // MODIFIED response
        }

        return mailbox.storeFlags(uid, flags, operation);
    }

    /**
     * Finds messages with modification sequence greater than the given value.
     *
     * @param mailbox the mailbox
     * @param modSeq  the minimum modification sequence
     * @return the list of matching messages
     */
    public static List<StoredMessage> findModified(Mailbox mailbox, long modSeq) {
        List<StoredMessage> result = new ArrayList<>();
        for (StoredMessage msg : mailbox.allMessages()) {
            if (msg.modSeq() > modSeq) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * Returns UIDs of messages modified since the given mod-sequence.
     *
     * @param mailbox the mailbox
     * @param modSeq  the modification sequence threshold
     * @return the list of modified UIDs
     */
    public static List<Long> modifiedUids(Mailbox mailbox, long modSeq) {
        return findModified(mailbox, modSeq).stream()
                .map(StoredMessage::uid)
                .toList();
    }

    /**
     * Fetches messages with MODSEQ metadata included.
     *
     * @param msg the message
     * @return the MODSEQ response fragment
     */
    public static String formatModSeq(StoredMessage msg) {
        return "MODSEQ (" + msg.modSeq() + ")";
    }
}
