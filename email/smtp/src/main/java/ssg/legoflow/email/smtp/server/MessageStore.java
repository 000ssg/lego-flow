package ssg.legoflow.email.smtp.server;

import java.util.List;

/**
 * Interface for storing delivered SMTP messages.
 *
 * <p>Implementations receive complete mail envelopes after successful DATA/BDAT
 * commands. The store is responsible for message persistence, routing, and
 * retrieval.
 *
 * @since 0.1.0
 */
public interface MessageStore {

    /**
     * Stores a delivered message.
     *
     * @param envelope the mail envelope to store
     * @return the storage result (message ID or error)
     * @throws MessageStoreException if storage fails
     */
    StoreResult store(MailEnvelope envelope) throws MessageStoreException;

    /**
     * Retrieves all stored messages.
     *
     * @return unmodifiable list of stored envelopes
     */
    List<MailEnvelope> getMessages();

    /**
     * Retrieves messages for a specific recipient.
     *
     * @param recipient the recipient email address
     * @return list of messages addressed to the recipient
     */
    List<MailEnvelope> getMessagesFor(String recipient);

    /**
     * Returns the number of stored messages.
     *
     * @return the message count
     */
    int getMessageCount();

    /**
     * Clears all stored messages.
     */
    void clear();

    /**
     * Result of a message storage operation.
     *
     * @param messageId the assigned message ID
     * @param accepted  true if the message was accepted
     * @param message   a human-readable status message
     */
    record StoreResult(String messageId, boolean accepted, String message) {

        /**
         * Creates a successful storage result.
         *
         * @param messageId the message ID
         * @return the result
         */
        public static StoreResult success(String messageId) {
            return new StoreResult(messageId, true, "Message accepted");
        }

        /**
         * Creates a rejected storage result.
         *
         * @param reason the rejection reason
         * @return the result
         */
        public static StoreResult rejected(String reason) {
            return new StoreResult(null, false, reason);
        }
    }
}
