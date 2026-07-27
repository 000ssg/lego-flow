package ssg.legoflow.messaging.nats.jetstream;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JetStream consumer tracking delivery position and acknowledgements.
 *
 * @since 1.0.0
 */
public final class Consumer {

    private final String name;
    private final ConsumerConfig config;
    private final AtomicLong deliveredSequence;
    private final AtomicLong ackedSequence = new AtomicLong(0);
    private final Set<Long> pendingAcks = new java.util.concurrent.ConcurrentSkipListSet<>();
    private final AtomicLong deliveredCount = new AtomicLong(0);

    /**
     * Creates a new consumer.
     *
     * @param name   the consumer name (durable name or generated)
     * @param config the consumer configuration
     * @param startSeq the starting sequence number
     */
    public Consumer(String name, ConsumerConfig config, long startSeq) {
        this.name = Objects.requireNonNull(name);
        this.config = Objects.requireNonNull(config);
        this.deliveredSequence = new AtomicLong(startSeq > 0 ? startSeq - 1 : 0);
    }

    /**
     * Returns the consumer name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the consumer configuration.
     *
     * @return the config
     */
    public ConsumerConfig config() {
        return config;
    }

    /**
     * Returns the last delivered sequence number.
     *
     * @return the sequence
     */
    public long deliveredSequence() {
        return deliveredSequence.get();
    }

    /**
     * Returns the last acknowledged sequence number.
     *
     * @return the sequence
     */
    public long ackedSequence() {
        return ackedSequence.get();
    }

    /**
     * Returns the number of pending (unacknowledged) messages.
     *
     * @return the count
     */
    public int pendingCount() {
        return pendingAcks.size();
    }

    /**
     * Returns the total number of delivered messages.
     *
     * @return the count
     */
    public long deliveredCount() {
        return deliveredCount.get();
    }

    /**
     * Advances the delivered sequence and records pending ack if needed.
     *
     * @param sequence the delivered sequence
     */
    public void markDelivered(long sequence) {
        deliveredSequence.set(sequence);
        deliveredCount.incrementAndGet();
        if (config.ackPolicy() != AckPolicy.NONE) {
            pendingAcks.add(sequence);
        }
    }

    /**
     * Acknowledges a message sequence.
     *
     * @param sequence the acknowledged sequence
     */
    public void acknowledge(long sequence) {
        switch (config.ackPolicy()) {
            case EXPLICIT -> pendingAcks.remove(sequence);
            case ALL -> {
                pendingAcks.removeIf(s -> s <= sequence);
                ackedSequence.set(Math.max(ackedSequence.get(), sequence));
            }
            case NONE -> { /* nothing to do */ }
        }
        if (config.ackPolicy() != AckPolicy.NONE) {
            ackedSequence.set(Math.max(ackedSequence.get(), sequence));
        }
    }

    /**
     * Returns whether this consumer can accept more unacknowledged messages.
     *
     * @return true if under the max_ack_pending limit
     */
    public boolean canDeliver() {
        if (config.ackPolicy() == AckPolicy.NONE) return true;
        return pendingAcks.size() < config.maxAckPending();
    }

    /**
     * Returns the next sequence to fetch from.
     *
     * @return the next sequence
     */
    public long nextFetchSequence() {
        return deliveredSequence.get() + 1;
    }

    /**
     * Returns consumer info as JSON.
     *
     * @return JSON string
     */
    public String toInfoJson() {
        var sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"name\":\"").append(name).append('"');
        sb.append(",\"config\":").append(config.toJson());
        sb.append(",\"delivered\":{\"stream_seq\":").append(deliveredSequence.get())
                .append(",\"consumer_seq\":").append(deliveredCount.get()).append('}');
        sb.append(",\"ack_floor\":{\"stream_seq\":").append(ackedSequence.get()).append('}');
        sb.append(",\"num_pending\":").append(pendingAcks.size());
        sb.append(",\"num_ack_pending\":").append(pendingAcks.size());
        sb.append('}');
        return sb.toString();
    }
}
