package ssg.legoflow.email.common.mime;

import ssg.legoflow.email.common.address.AddressParser;
import ssg.legoflow.email.common.address.Mailbox;
import ssg.legoflow.email.common.header.DateTimeParser;
import ssg.legoflow.email.common.header.MessageId;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A complete MIME message consisting of headers and body.
 *
 * <p>The body may be a simple text/binary content ({@link MimePart}) or
 * a multipart structure ({@link MimeMultipart}).
 *
 * @since 0.1.0
 */
public final class MimeMessage {

    private final MimeHeaders headers;
    private final MimePart body;
    private final MimeMultipart multipartBody;

    /**
     * Creates a MIME message with a simple body.
     *
     * @param headers the message headers
     * @param body    the message body as a single part
     */
    public MimeMessage(MimeHeaders headers, MimePart body) {
        this.headers = Objects.requireNonNull(headers);
        this.body = body;
        this.multipartBody = null;
    }

    /**
     * Creates a MIME message with a multipart body.
     *
     * @param headers       the message headers
     * @param multipartBody the multipart body
     */
    public MimeMessage(MimeHeaders headers, MimeMultipart multipartBody) {
        this.headers = Objects.requireNonNull(headers);
        this.body = null;
        this.multipartBody = multipartBody;
    }

    /**
     * Returns the message headers.
     *
     * @return the headers
     */
    public MimeHeaders headers() {
        return headers;
    }

    /**
     * Returns the simple body, or null if the message is multipart.
     *
     * @return the body part
     */
    public MimePart body() {
        return body;
    }

    /**
     * Returns the multipart body, or null if the message has a simple body.
     *
     * @return the multipart body
     */
    public MimeMultipart multipartBody() {
        return multipartBody;
    }

    /**
     * Checks whether this message has a multipart body.
     *
     * @return true if multipart
     */
    public boolean isMultipart() {
        return multipartBody != null;
    }

    /**
     * Returns all leaf parts of this message.
     *
     * <p>For simple messages, returns a list with just the body.
     * For multipart messages, recursively collects all parts.
     *
     * @return list of all MimePart instances
     */
    public List<MimePart> allParts() {
        if (multipartBody != null) {
            return multipartBody.allParts();
        }
        if (body != null) {
            return List.of(body);
        }
        return List.of();
    }

    // --- Convenience header accessors ---

    /**
     * Returns the Subject header value (decoded).
     *
     * @return the subject, or null
     */
    public String subject() {
        return headers.getDecoded("Subject");
    }

    /**
     * Returns the From mailboxes.
     *
     * @return the list of sender mailboxes
     */
    public List<Mailbox> from() {
        String value = headers.get("From");
        return value != null ? AddressParser.parseMailboxList(value) : List.of();
    }

    /**
     * Returns the To mailboxes.
     *
     * @return the list of recipient mailboxes
     */
    public List<Mailbox> to() {
        String value = headers.get("To");
        return value != null ? AddressParser.parseMailboxList(value) : List.of();
    }

    /**
     * Returns the Cc mailboxes.
     *
     * @return the list of CC mailboxes
     */
    public List<Mailbox> cc() {
        String value = headers.get("Cc");
        return value != null ? AddressParser.parseMailboxList(value) : List.of();
    }

    /**
     * Returns the Bcc mailboxes.
     *
     * @return the list of BCC mailboxes
     */
    public List<Mailbox> bcc() {
        String value = headers.get("Bcc");
        return value != null ? AddressParser.parseMailboxList(value) : List.of();
    }

    /**
     * Returns the Date header as an OffsetDateTime.
     *
     * @return the date, or null if not present or unparseable
     */
    public OffsetDateTime date() {
        String value = headers.get("Date");
        if (value != null) {
            try {
                return DateTimeParser.parse(value);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Returns the Message-ID.
     *
     * @return the message ID, or null
     */
    public MessageId messageId() {
        String value = headers.get("Message-ID");
        return value != null ? MessageId.parse(value) : null;
    }

    /**
     * Returns the In-Reply-To message IDs.
     *
     * @return the list of message IDs
     */
    public List<MessageId> inReplyTo() {
        String value = headers.get("In-Reply-To");
        return value != null ? MessageId.parseList(value) : List.of();
    }

    /**
     * Returns the References message IDs.
     *
     * @return the list of message IDs
     */
    public List<MessageId> references() {
        String value = headers.get("References");
        return value != null ? MessageId.parseList(value) : List.of();
    }

    /**
     * Returns the Content-Type of this message.
     *
     * @return the content type
     */
    public ContentType contentType() {
        return headers.contentType();
    }

    @Override
    public String toString() {
        String subject = subject();
        return "MimeMessage{subject=" + (subject != null ? subject : "(none)")
                + ", multipart=" + isMultipart()
                + ", parts=" + allParts().size() + "}";
    }
}
