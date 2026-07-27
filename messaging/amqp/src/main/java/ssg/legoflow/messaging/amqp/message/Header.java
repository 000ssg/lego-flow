package ssg.legoflow.messaging.amqp.message;

/**
 * AMQP 1.0 message header section (section 3.2.1).
 *
 * <p>Transport-related properties of a message. Not all fields are required;
 * defaults follow the specification.
 *
 * @param durable        whether the message is durable
 * @param priority       message priority (0-9, default 4)
 * @param ttl            time to live in milliseconds (0 = no expiry)
 * @param firstAcquirer  whether this is the first acquirer of the message
 * @param deliveryCount  number of unsuccessful delivery attempts
 * @since 1.0.0
 */
public record Header(
        boolean durable,
        short priority,
        long ttl,
        boolean firstAcquirer,
        long deliveryCount
) {

    /** Creates a Header with default values. */
    public Header() {
        this(false, (short) 4, 0, false, 0);
    }

    /** Creates a durable header with default priority. */
    public static Header ofDurable() {
        return new Header(true, (short) 4, 0, false, 0);
    }
}
