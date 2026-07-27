package ssg.legoflow.messaging.nats.jetstream;

import ssg.legoflow.messaging.nats.protocol.NatsHeaders;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory message store with retention policies for JetStream streams.
 *
 * <p>Stores messages with sequence numbers and timestamps. Supports
 * retention enforcement based on message count, byte size, and age.
 *
 * @since 1.0.0
 */
public final class StreamStore {

    /**
     * A stored message with metadata.
     *
     * @param sequence  the sequence number
     * @param subject   the message subject
     * @param headers   the message headers, or null
     * @param payload   the message payload
     * @param timestamp when the message was stored
     */
    public record StoredMessage(
            long sequence,
            String subject,
            NatsHeaders headers,
            byte[] payload,
            Instant timestamp
    ) {}

    private final StreamConfig config;
    private final CopyOnWriteArrayList<StoredMessage> messages = new CopyOnWriteArrayList<>();
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);

    /**
     * Creates a new stream store with the given configuration.
     *
     * @param config the stream configuration
     */
    public StreamStore(StreamConfig config) {
        this.config = config;
    }

    /**
     * Stores a message and returns the assigned sequence number.
     *
     * @param subject the message subject
     * @param headers the message headers, or null
     * @param payload the message payload
     * @return the assigned sequence number
     */
    public long store(String subject, NatsHeaders headers, byte[] payload) {
        // Check discard policy for new messages
        if (config.discardPolicy() == StreamConfig.DiscardPolicy.NEW) {
            if (config.maxMsgs() > 0 && messages.size() >= config.maxMsgs()) {
                return -1; // reject
            }
            if (config.maxBytes() > 0 && totalBytes.get() + payload.length > config.maxBytes()) {
                return -1; // reject
            }
        }

        long seq = sequenceCounter.incrementAndGet();
        var stored = new StoredMessage(seq, subject, headers, payload, Instant.now());
        messages.add(stored);
        totalBytes.addAndGet(payload.length);

        enforceRetention();

        return seq;
    }

    /**
     * Returns messages starting from the given sequence.
     *
     * @param fromSeq  the starting sequence (inclusive)
     * @param maxCount the maximum number of messages to return
     * @return list of stored messages
     */
    public List<StoredMessage> fetch(long fromSeq, int maxCount) {
        var result = new ArrayList<StoredMessage>();
        for (var msg : messages) {
            if (msg.sequence() >= fromSeq) {
                result.add(msg);
                if (result.size() >= maxCount) break;
            }
        }
        return result;
    }

    /**
     * Returns messages for a specific subject starting from the given sequence.
     *
     * @param subject  the subject filter
     * @param fromSeq  the starting sequence (inclusive)
     * @param maxCount the maximum number of messages to return
     * @return list of stored messages
     */
    public List<StoredMessage> fetch(String subject, long fromSeq, int maxCount) {
        var result = new ArrayList<StoredMessage>();
        for (var msg : messages) {
            if (msg.sequence() >= fromSeq && matchesSubject(msg.subject(), subject)) {
                result.add(msg);
                if (result.size() >= maxCount) break;
            }
        }
        return result;
    }

    /**
     * Returns the message at the given sequence.
     *
     * @param sequence the sequence number
     * @return the message, or null if not found
     */
    public StoredMessage get(long sequence) {
        for (var msg : messages) {
            if (msg.sequence() == sequence) return msg;
        }
        return null;
    }

    /**
     * Returns the last stored message.
     *
     * @return the last message, or null if empty
     */
    public StoredMessage lastMessage() {
        if (messages.isEmpty()) return null;
        return messages.getLast();
    }

    /**
     * Returns the first stored message.
     *
     * @return the first message, or null if empty
     */
    public StoredMessage firstMessage() {
        if (messages.isEmpty()) return null;
        return messages.getFirst();
    }

    /**
     * Returns the current message count.
     *
     * @return the count
     */
    public int messageCount() {
        return messages.size();
    }

    /**
     * Returns the current sequence number.
     *
     * @return the last assigned sequence
     */
    public long currentSequence() {
        return sequenceCounter.get();
    }

    /**
     * Returns the total bytes stored.
     *
     * @return the byte count
     */
    public long totalBytes() {
        return totalBytes.get();
    }

    /**
     * Removes a specific message by sequence (for workqueue retention).
     *
     * @param sequence the sequence to remove
     * @return true if removed
     */
    public boolean remove(long sequence) {
        return messages.removeIf(m -> {
            if (m.sequence() == sequence) {
                totalBytes.addAndGet(-m.payload().length);
                return true;
            }
            return false;
        });
    }

    /**
     * Purges all messages from this store.
     *
     * @return the number of purged messages
     */
    public int purge() {
        int count = messages.size();
        messages.clear();
        totalBytes.set(0);
        return count;
    }

    private void enforceRetention() {
        // Enforce max messages
        if (config.maxMsgs() > 0) {
            while (messages.size() > config.maxMsgs()) {
                var removed = messages.removeFirst();
                totalBytes.addAndGet(-removed.payload().length);
            }
        }

        // Enforce max bytes
        if (config.maxBytes() > 0) {
            while (totalBytes.get() > config.maxBytes() && !messages.isEmpty()) {
                var removed = messages.removeFirst();
                totalBytes.addAndGet(-removed.payload().length);
            }
        }

        // Enforce max age
        if (!config.maxAge().isZero()) {
            Instant cutoff = Instant.now().minus(config.maxAge());
            messages.removeIf(m -> {
                if (m.timestamp().isBefore(cutoff)) {
                    totalBytes.addAndGet(-m.payload().length);
                    return true;
                }
                return false;
            });
        }
    }

    private boolean matchesSubject(String messageSubject, String filterSubject) {
        if (filterSubject == null || filterSubject.equals(">")) return true;
        return ssg.legoflow.messaging.nats.subject.SubjectMatcher.matches(filterSubject, messageSubject);
    }
}
