package ssg.legoflow.email.imap.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Push notification system for IMAP IDLE (RFC 2177).
 *
 * <p>Sessions register for notifications on a mailbox and receive
 * untagged responses when changes occur (new messages, flag changes,
 * expunges).
 *
 * @since 1.0.0
 */
public final class IdleNotifier {

    private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();

    /**
     * Registers a listener for mailbox notifications.
     *
     * @param mailboxName the mailbox name
     * @param listener    the notification callback
     */
    public void register(String mailboxName, Consumer<String> listener) {
        listeners.computeIfAbsent(mailboxName.toUpperCase(), k -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    /**
     * Unregisters a listener.
     *
     * @param mailboxName the mailbox name
     * @param listener    the listener to remove
     */
    public void unregister(String mailboxName, Consumer<String> listener) {
        List<Consumer<String>> list = listeners.get(mailboxName.toUpperCase());
        if (list != null) {
            list.remove(listener);
        }
    }

    /**
     * Notifies all listeners of a mailbox change.
     *
     * @param mailboxName the mailbox name
     * @param notification the notification text (e.g., "5 EXISTS", "2 EXPUNGE")
     */
    public void notify(String mailboxName, String notification) {
        List<Consumer<String>> list = listeners.get(mailboxName.toUpperCase());
        if (list != null) {
            for (Consumer<String> listener : list) {
                try {
                    listener.accept("* " + notification);
                } catch (Exception e) {
                    // Ignore listener errors
                }
            }
        }
    }

    /**
     * Notifies all listeners that a new message exists.
     *
     * @param mailboxName  the mailbox name
     * @param messageCount the new total message count
     */
    public void notifyExists(String mailboxName, int messageCount) {
        notify(mailboxName, messageCount + " EXISTS");
    }

    /**
     * Notifies all listeners of a message expunge.
     *
     * @param mailboxName the mailbox name
     * @param seqNum      the expunged sequence number
     */
    public void notifyExpunge(String mailboxName, int seqNum) {
        notify(mailboxName, seqNum + " EXPUNGE");
    }

    /**
     * Notifies all listeners of a flag change.
     *
     * @param mailboxName the mailbox name
     * @param seqNum      the sequence number
     * @param flags       the new flags
     */
    public void notifyFlagChange(String mailboxName, int seqNum, Set<String> flags) {
        String flagStr = "(" + String.join(" ", new TreeSet<>(flags)) + ")";
        notify(mailboxName, seqNum + " FETCH (FLAGS " + flagStr + ")");
    }

    /**
     * Returns the number of listeners for a mailbox.
     *
     * @param mailboxName the mailbox name
     * @return the listener count
     */
    public int listenerCount(String mailboxName) {
        List<Consumer<String>> list = listeners.get(mailboxName.toUpperCase());
        return list != null ? list.size() : 0;
    }

    /**
     * Removes all listeners for a mailbox.
     *
     * @param mailboxName the mailbox name
     */
    public void clearListeners(String mailboxName) {
        listeners.remove(mailboxName.toUpperCase());
    }
}
