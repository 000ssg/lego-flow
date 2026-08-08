package ssg.legoflow.messaging.amqp.message;

/**
 * AMQP 1.0 message properties section (section 3.2.4).
 *
 * <p>Immutable properties associated with a message that are defined by the
 * AMQP specification. These are distinct from application-properties.
 *
 * @param messageId        application message identifier
 * @param userId           creating user identity
 * @param to               destination address
 * @param subject          message subject
 * @param replyTo          reply-to address
 * @param correlationId    correlation identifier for request/reply
 * @param contentType      MIME content type (e.g. "application/json")
 * @param contentEncoding  MIME content encoding (e.g. "gzip")
 * @param absoluteExpiryTime absolute expiry time (millis since epoch)
 * @param creationTime     creation time (millis since epoch)
 * @param groupId          group identifier for grouping related messages
 * @param groupSequence    sequence number within a group
 * @param replyToGroupId   group identifier for reply messages
 * @since 0.1.0
 */
public record Properties(
        String messageId,
        byte[] userId,
        String to,
        String subject,
        String replyTo,
        String correlationId,
        String contentType,
        String contentEncoding,
        long absoluteExpiryTime,
        long creationTime,
        String groupId,
        long groupSequence,
        String replyToGroupId
) {

    /** Creates empty Properties with all defaults. */
    public Properties() {
        this(null, null, null, null, null, null, null, null, 0, 0, null, 0, null);
    }

    /**
     * Fluent builder for {@link Properties}.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private String messageId;
        private byte[] userId;
        private String to;
        private String subject;
        private String replyTo;
        private String correlationId;
        private String contentType;
        private String contentEncoding;
        private long absoluteExpiryTime;
        private long creationTime;
        private String groupId;
        private long groupSequence;
        private String replyToGroupId;

        /** Sets the message identifier. */
        public Builder messageId(String messageId) { this.messageId = messageId; return this; }

        /** Sets the user identity. */
        public Builder userId(byte[] userId) { this.userId = userId; return this; }

        /** Sets the destination address. */
        public Builder to(String to) { this.to = to; return this; }

        /** Sets the message subject. */
        public Builder subject(String subject) { this.subject = subject; return this; }

        /** Sets the reply-to address. */
        public Builder replyTo(String replyTo) { this.replyTo = replyTo; return this; }

        /** Sets the correlation identifier. */
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }

        /** Sets the MIME content type. */
        public Builder contentType(String contentType) { this.contentType = contentType; return this; }

        /** Sets the MIME content encoding. */
        public Builder contentEncoding(String contentEncoding) { this.contentEncoding = contentEncoding; return this; }

        /** Sets the absolute expiry time. */
        public Builder absoluteExpiryTime(long absoluteExpiryTime) { this.absoluteExpiryTime = absoluteExpiryTime; return this; }

        /** Sets the creation time. */
        public Builder creationTime(long creationTime) { this.creationTime = creationTime; return this; }

        /** Sets the group identifier. */
        public Builder groupId(String groupId) { this.groupId = groupId; return this; }

        /** Sets the group sequence number. */
        public Builder groupSequence(long groupSequence) { this.groupSequence = groupSequence; return this; }

        /** Sets the reply-to group identifier. */
        public Builder replyToGroupId(String replyToGroupId) { this.replyToGroupId = replyToGroupId; return this; }

        /** Builds the properties. */
        public Properties build() {
            return new Properties(messageId, userId, to, subject, replyTo, correlationId,
                    contentType, contentEncoding, absoluteExpiryTime, creationTime,
                    groupId, groupSequence, replyToGroupId);
        }
    }

    /**
     * Returns a new builder.
     *
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
