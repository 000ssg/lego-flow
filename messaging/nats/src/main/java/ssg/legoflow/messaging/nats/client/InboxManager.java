package ssg.legoflow.messaging.nats.client;

import ssg.legoflow.messaging.nats.protocol.NatsProtocol;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Generates unique inbox subjects for the NATS request/reply pattern.
 *
 * <p>Inboxes use the prefix {@code _INBOX.} followed by a unique identifier.
 * Each client instance has its own prefix to avoid collisions.
 *
 * @since 0.1.0
 */
public final class InboxManager {

    private final String prefix;
    private final AtomicLong counter = new AtomicLong(0);

    /**
     * Creates an inbox manager with a random prefix.
     */
    public InboxManager() {
        this.prefix = NatsProtocol.INBOX_PREFIX + UUID.randomUUID().toString().replace("-", "") + ".";
    }

    /**
     * Creates an inbox manager with a specified base prefix.
     *
     * @param basePrefix the base prefix (appended after _INBOX.)
     */
    public InboxManager(String basePrefix) {
        this.prefix = NatsProtocol.INBOX_PREFIX + basePrefix + ".";
    }

    /**
     * Generates a new unique inbox subject.
     *
     * @return the inbox subject
     */
    public String newInbox() {
        return prefix + counter.incrementAndGet();
    }

    /**
     * Returns the inbox prefix used by this manager.
     *
     * @return the prefix
     */
    public String prefix() {
        return prefix;
    }
}
