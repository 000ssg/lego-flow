package ssg.legoflow.email.smtp.server;

/**
 * Handler interface for SMTP message delivery decisions.
 *
 * <p>Implementations control whether senders, recipients, and messages are
 * accepted or rejected. This allows implementing relay restrictions, spam
 * filtering, address validation, and other policies.
 *
 * @since 1.0.0
 */
public interface SmtpHandler {

    /**
     * Called when a MAIL FROM command is received. Decides whether to accept
     * the sender address.
     *
     * @param sender the sender address
     * @return true to accept, false to reject with 550
     */
    default boolean acceptSender(String sender) {
        return true;
    }

    /**
     * Called when a RCPT TO command is received. Decides whether to accept
     * the recipient address.
     *
     * @param recipient the recipient address
     * @param sender    the previously accepted sender
     * @return true to accept, false to reject with 550
     */
    default boolean acceptRecipient(String recipient, String sender) {
        return true;
    }

    /**
     * Called after DATA/BDAT completes. Decides whether to accept the message
     * for delivery.
     *
     * @param envelope the complete mail envelope
     * @return true to accept, false to reject with 554
     */
    default boolean acceptMessage(MailEnvelope envelope) {
        return true;
    }

    /**
     * Called to authenticate a user. Returns true if the credentials are valid.
     *
     * @param username the username
     * @param password the password
     * @return true if authenticated
     */
    default boolean authenticate(String username, String password) {
        return false;
    }

    /**
     * Returns a handler that accepts all senders, recipients, and messages.
     *
     * @return an accept-all handler
     */
    static SmtpHandler acceptAll() {
        return new SmtpHandler() {
            @Override
            public boolean authenticate(String username, String password) {
                return true;
            }
        };
    }

    /**
     * Returns a handler that accepts messages only for specific domains.
     *
     * @param domains the accepted domains
     * @return a domain-restricted handler
     */
    static SmtpHandler forDomains(String... domains) {
        return new SmtpHandler() {
            @Override
            public boolean acceptRecipient(String recipient, String sender) {
                if (recipient == null) return false;
                String lower = recipient.toLowerCase();
                for (String domain : domains) {
                    if (lower.endsWith("@" + domain.toLowerCase())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
