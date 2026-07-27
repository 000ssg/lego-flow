package ssg.legoflow.email.smtp.protocol;

/**
 * Enumeration of SMTP commands as defined in RFC 5321 and extensions.
 *
 * <p>Includes commands from:
 * <ul>
 *   <li>RFC 5321 -- Simple Mail Transfer Protocol (core)</li>
 *   <li>RFC 3207 -- SMTP Service Extension for Secure SMTP over TLS (STARTTLS)</li>
 *   <li>RFC 4954 -- SMTP Service Extension for Authentication (AUTH)</li>
 *   <li>RFC 3030 -- SMTP Service Extensions for Transmission of Large and Binary MIME Messages (BDAT)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public enum SmtpCommand {

    // --- RFC 5321 Core Commands ---

    /** Extended HELLO -- identifies client and requests extensions. */
    EHLO,
    /** HELLO -- identifies client (legacy, no extensions). */
    HELO,
    /** Initiate mail transaction with sender address. */
    MAIL,
    /** Specify a recipient address. */
    RCPT,
    /** Begin message data transfer (dot-stuffed). */
    DATA,
    /** Reset the current transaction. */
    RSET,
    /** Close the connection. */
    QUIT,
    /** No operation (keep-alive). */
    NOOP,
    /** Verify a user or mailbox name. */
    VRFY,
    /** Expand a mailing list. */
    EXPN,
    /** Request help information. */
    HELP,

    // --- RFC 3207 STARTTLS ---

    /** Upgrade connection to TLS. */
    STARTTLS,

    // --- RFC 4954 AUTH ---

    /** Authenticate the client using SASL mechanism. */
    AUTH,

    // --- RFC 3030 Chunking ---

    /** Binary data chunk transfer. */
    BDAT;

    /**
     * Parses a command string into an {@link SmtpCommand}.
     *
     * @param text the command string (case-insensitive)
     * @return the matching command
     * @throws IllegalArgumentException if the command is not recognized
     */
    public static SmtpCommand parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Command text must not be null or blank");
        }
        return valueOf(text.trim().toUpperCase());
    }

    /**
     * Returns the command as it appears on the wire (uppercase name).
     *
     * @return the command string
     */
    public String wireForm() {
        return name();
    }
}
