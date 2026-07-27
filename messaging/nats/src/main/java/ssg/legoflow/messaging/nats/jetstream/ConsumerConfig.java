package ssg.legoflow.messaging.nats.jetstream;

import ssg.legoflow.messaging.nats.protocol.ServerInfo;

import java.time.Duration;

/**
 * JetStream consumer configuration.
 *
 * @param durableName   durable consumer name, or null for ephemeral
 * @param deliverPolicy when to start delivering messages
 * @param ackPolicy     acknowledgement policy
 * @param ackWait       how long to wait for ack before redelivery
 * @param maxDeliver    maximum delivery attempts (-1 for unlimited)
 * @param replayPolicy  replay speed for historical messages
 * @param filterSubject subject filter, or null for all
 * @param maxAckPending maximum number of unacknowledged messages
 * @param startSeq      starting sequence for BY_START_SEQ policy
 * @since 1.0.0
 */
public record ConsumerConfig(
        String durableName,
        DeliverPolicy deliverPolicy,
        AckPolicy ackPolicy,
        Duration ackWait,
        int maxDeliver,
        ReplayPolicy replayPolicy,
        String filterSubject,
        int maxAckPending,
        long startSeq
) {

    /**
     * When to start delivering messages to a consumer.
     */
    public enum DeliverPolicy {
        /** Deliver all available messages. */
        ALL("all"),
        /** Deliver starting from the last message. */
        LAST("last"),
        /** Deliver only new messages (received after consumer creation). */
        NEW("new"),
        /** Deliver starting from a specific sequence number. */
        BY_START_SEQ("by_start_sequence"),
        /** Deliver starting from a specific time. */
        BY_START_TIME("by_start_time");

        private final String value;
        DeliverPolicy(String value) { this.value = value; }
        public String value() { return value; }

        public static DeliverPolicy fromValue(String v) {
            for (DeliverPolicy p : values()) {
                if (p.value.equals(v)) return p;
            }
            return ALL;
        }
    }

    /**
     * How fast to replay historical messages.
     */
    public enum ReplayPolicy {
        /** Deliver as fast as possible. */
        INSTANT("instant"),
        /** Deliver at the original rate. */
        ORIGINAL("original");

        private final String value;
        ReplayPolicy(String value) { this.value = value; }
        public String value() { return value; }

        public static ReplayPolicy fromValue(String v) {
            if ("original".equals(v)) return ORIGINAL;
            return INSTANT;
        }
    }

    /**
     * Creates a consumer config with defaults.
     */
    public ConsumerConfig {
        if (deliverPolicy == null) deliverPolicy = DeliverPolicy.ALL;
        if (ackPolicy == null) ackPolicy = AckPolicy.EXPLICIT;
        if (ackWait == null) ackWait = Duration.ofSeconds(30);
        if (replayPolicy == null) replayPolicy = ReplayPolicy.INSTANT;
        if (maxDeliver <= 0 && maxDeliver != -1) maxDeliver = -1;
        if (maxAckPending <= 0) maxAckPending = 1000;
    }

    /**
     * Creates a builder for consumer configuration.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether this is a durable consumer.
     *
     * @return true if durable
     */
    public boolean isDurable() {
        return durableName != null && !durableName.isEmpty();
    }

    /**
     * Encodes this config as JSON.
     *
     * @return JSON string
     */
    public String toJson() {
        var sb = new StringBuilder(256);
        sb.append('{');
        if (durableName != null) {
            sb.append("\"durable_name\":\"").append(durableName).append("\",");
        }
        sb.append("\"deliver_policy\":\"").append(deliverPolicy.value()).append('"');
        sb.append(",\"ack_policy\":\"").append(ackPolicy.value()).append('"');
        sb.append(",\"ack_wait\":").append(ackWait.toNanos());
        sb.append(",\"max_deliver\":").append(maxDeliver);
        sb.append(",\"replay_policy\":\"").append(replayPolicy.value()).append('"');
        if (filterSubject != null) {
            sb.append(",\"filter_subject\":\"").append(filterSubject).append('"');
        }
        sb.append(",\"max_ack_pending\":").append(maxAckPending);
        if (startSeq > 0) {
            sb.append(",\"opt_start_seq\":").append(startSeq);
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses a JSON string into a ConsumerConfig.
     *
     * @param json the JSON string
     * @return parsed config
     */
    public static ConsumerConfig fromJson(String json) {
        String durableName = ServerInfo.extractString(json, "durable_name", null);
        if (durableName != null && durableName.isEmpty()) durableName = null;
        String deliverPolicy = ServerInfo.extractString(json, "deliver_policy", "all");
        String ackPolicy = ServerInfo.extractString(json, "ack_policy", "explicit");
        long ackWaitNanos = ServerInfo.extractLong(json, "ack_wait", Duration.ofSeconds(30).toNanos());
        int maxDeliver = ServerInfo.extractInt(json, "max_deliver", -1);
        String replayPolicy = ServerInfo.extractString(json, "replay_policy", "instant");
        String filterSubject = ServerInfo.extractString(json, "filter_subject", null);
        if (filterSubject != null && filterSubject.isEmpty()) filterSubject = null;
        int maxAckPending = ServerInfo.extractInt(json, "max_ack_pending", 1000);
        long startSeq = ServerInfo.extractLong(json, "opt_start_seq", 0);

        return new ConsumerConfig(durableName,
                DeliverPolicy.fromValue(deliverPolicy),
                AckPolicy.fromValue(ackPolicy),
                Duration.ofNanos(ackWaitNanos),
                maxDeliver,
                ReplayPolicy.fromValue(replayPolicy),
                filterSubject, maxAckPending, startSeq);
    }

    /**
     * Builder for ConsumerConfig.
     */
    public static final class Builder {
        private String durableName;
        private DeliverPolicy deliverPolicy = DeliverPolicy.ALL;
        private AckPolicy ackPolicy = AckPolicy.EXPLICIT;
        private Duration ackWait = Duration.ofSeconds(30);
        private int maxDeliver = -1;
        private ReplayPolicy replayPolicy = ReplayPolicy.INSTANT;
        private String filterSubject;
        private int maxAckPending = 1000;
        private long startSeq;

        public Builder durable(String name) { this.durableName = name; return this; }
        public Builder deliverPolicy(DeliverPolicy p) { this.deliverPolicy = p; return this; }
        public Builder ackPolicy(AckPolicy p) { this.ackPolicy = p; return this; }
        public Builder ackWait(Duration d) { this.ackWait = d; return this; }
        public Builder maxDeliver(int n) { this.maxDeliver = n; return this; }
        public Builder replayPolicy(ReplayPolicy p) { this.replayPolicy = p; return this; }
        public Builder filterSubject(String s) { this.filterSubject = s; return this; }
        public Builder maxAckPending(int n) { this.maxAckPending = n; return this; }
        public Builder startSeq(long seq) { this.startSeq = seq; return this; }

        public ConsumerConfig build() {
            return new ConsumerConfig(durableName, deliverPolicy, ackPolicy,
                    ackWait, maxDeliver, replayPolicy, filterSubject,
                    maxAckPending, startSeq);
        }
    }
}
