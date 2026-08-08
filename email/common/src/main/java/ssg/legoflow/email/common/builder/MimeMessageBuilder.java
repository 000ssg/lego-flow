package ssg.legoflow.email.common.builder;

import ssg.legoflow.email.common.address.EmailAddress;
import ssg.legoflow.email.common.address.Mailbox;
import ssg.legoflow.email.common.encoding.EncodedWordCodec;
import ssg.legoflow.email.common.header.DateTimeParser;
import ssg.legoflow.email.common.header.MessageId;
import ssg.legoflow.email.common.mime.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating {@link MimeMessage} instances.
 *
 * <p>Supports building simple text messages, messages with attachments,
 * and complex multipart structures.
 *
 * @since 0.1.0
 */
public final class MimeMessageBuilder {

    private final MimeHeaders headers = new MimeHeaders();
    private final List<Mailbox> from = new ArrayList<>();
    private final List<Mailbox> to = new ArrayList<>();
    private final List<Mailbox> cc = new ArrayList<>();
    private final List<Mailbox> bcc = new ArrayList<>();
    private String subject;
    private OffsetDateTime date;
    private MessageId messageId;

    // Body options
    private String textBody;
    private String htmlBody;
    private Charset bodyCharset = StandardCharsets.UTF_8;
    private final List<MimePart> attachments = new ArrayList<>();
    private final List<MimePart> inlineParts = new ArrayList<>();
    private MimeMultipart explicitMultipart;

    private MimeMessageBuilder() {
    }

    /**
     * Creates a new MimeMessageBuilder.
     *
     * @return a new builder
     */
    public static MimeMessageBuilder create() {
        return new MimeMessageBuilder();
    }

    /**
     * Sets the From address.
     *
     * @param displayName the display name (may be null)
     * @param email       the email address
     * @return this builder
     */
    public MimeMessageBuilder from(String displayName, String email) {
        from.add(new Mailbox(displayName, EmailAddress.parse(email)));
        return this;
    }

    /**
     * Sets the From address without a display name.
     *
     * @param email the email address
     * @return this builder
     */
    public MimeMessageBuilder from(String email) {
        from.add(new Mailbox(EmailAddress.parse(email)));
        return this;
    }

    /**
     * Adds a To recipient.
     *
     * @param displayName the display name (may be null)
     * @param email       the email address
     * @return this builder
     */
    public MimeMessageBuilder to(String displayName, String email) {
        to.add(new Mailbox(displayName, EmailAddress.parse(email)));
        return this;
    }

    /**
     * Adds a To recipient without a display name.
     *
     * @param email the email address
     * @return this builder
     */
    public MimeMessageBuilder to(String email) {
        to.add(new Mailbox(EmailAddress.parse(email)));
        return this;
    }

    /**
     * Adds a Cc recipient.
     *
     * @param displayName the display name (may be null)
     * @param email       the email address
     * @return this builder
     */
    public MimeMessageBuilder cc(String displayName, String email) {
        cc.add(new Mailbox(displayName, EmailAddress.parse(email)));
        return this;
    }

    /**
     * Adds a Cc recipient without a display name.
     *
     * @param email the email address
     * @return this builder
     */
    public MimeMessageBuilder cc(String email) {
        cc.add(new Mailbox(EmailAddress.parse(email)));
        return this;
    }

    /**
     * Adds a Bcc recipient.
     *
     * @param email the email address
     * @return this builder
     */
    public MimeMessageBuilder bcc(String email) {
        bcc.add(new Mailbox(EmailAddress.parse(email)));
        return this;
    }

    /**
     * Sets the Subject header.
     *
     * @param subject the subject text
     * @return this builder
     */
    public MimeMessageBuilder subject(String subject) {
        this.subject = subject;
        return this;
    }

    /**
     * Sets the Date header.
     *
     * @param date the date
     * @return this builder
     */
    public MimeMessageBuilder date(OffsetDateTime date) {
        this.date = date;
        return this;
    }

    /**
     * Sets the Message-ID.
     *
     * @param messageId the message ID
     * @return this builder
     */
    public MimeMessageBuilder messageId(MessageId messageId) {
        this.messageId = messageId;
        return this;
    }

    /**
     * Sets a custom header.
     *
     * @param name  the header name
     * @param value the header value
     * @return this builder
     */
    public MimeMessageBuilder header(String name, String value) {
        headers.set(name, value);
        return this;
    }

    /**
     * Sets the body charset.
     *
     * @param charset the charset for text bodies
     * @return this builder
     */
    public MimeMessageBuilder charset(Charset charset) {
        this.bodyCharset = charset;
        return this;
    }

    /**
     * Sets the plain text body.
     *
     * @param text the text content
     * @return this builder
     */
    public MimeMessageBuilder textBody(String text) {
        this.textBody = text;
        return this;
    }

    /**
     * Sets the HTML body.
     *
     * @param html the HTML content
     * @return this builder
     */
    public MimeMessageBuilder htmlBody(String html) {
        this.htmlBody = html;
        return this;
    }

    /**
     * Adds an attachment.
     *
     * @param filename    the filename
     * @param contentType the MIME type
     * @param data        the attachment data
     * @return this builder
     */
    public MimeMessageBuilder attachment(String filename, String contentType, byte[] data) {
        MimePart part = MimePartBuilder.create()
                .contentType(contentType)
                .attachment(filename)
                .transferEncoding(ContentTransferEncoding.BASE64)
                .content(data)
                .encodeContent()
                .build();
        attachments.add(part);
        return this;
    }

    /**
     * Adds an inline part (e.g., embedded image).
     *
     * @param contentId   the content ID for referencing in HTML
     * @param contentType the MIME type
     * @param data        the inline data
     * @return this builder
     */
    public MimeMessageBuilder inlinePart(String contentId, String contentType, byte[] data) {
        MimePart part = MimePartBuilder.create()
                .contentType(contentType)
                .inline()
                .contentId(contentId)
                .transferEncoding(ContentTransferEncoding.BASE64)
                .content(data)
                .encodeContent()
                .build();
        inlineParts.add(part);
        return this;
    }

    /**
     * Sets an explicit multipart body, overriding text/html/attachment building.
     *
     * @param multipart the multipart body
     * @return this builder
     */
    public MimeMessageBuilder multipartBody(MimeMultipart multipart) {
        this.explicitMultipart = multipart;
        return this;
    }

    /**
     * Builds the MimeMessage.
     *
     * @return the constructed MimeMessage
     */
    public MimeMessage build() {
        // Set envelope headers
        if (!from.isEmpty()) {
            headers.set("From", formatMailboxList(from));
        }
        if (!to.isEmpty()) {
            headers.set("To", formatMailboxList(to));
        }
        if (!cc.isEmpty()) {
            headers.set("Cc", formatMailboxList(cc));
        }
        if (!bcc.isEmpty()) {
            headers.set("Bcc", formatMailboxList(bcc));
        }
        if (subject != null) {
            if (EncodedWordCodec.needsEncoding(subject)) {
                headers.set("Subject", EncodedWordCodec.encode(subject));
            } else {
                headers.set("Subject", subject);
            }
        }
        if (date != null) {
            headers.set("Date", DateTimeParser.format(date));
        }
        if (messageId != null) {
            headers.set("Message-ID", messageId.toWireFormat());
        }
        headers.set("MIME-Version", "1.0");

        // Build body
        if (explicitMultipart != null) {
            MultipartType mpt = explicitMultipart.multipartType();
            String subtype = mpt != null ? mpt.subtype() : "mixed";
            headers.set("Content-Type",
                    "multipart/" + subtype + "; boundary=\"" + explicitMultipart.boundary() + "\"");
            return new MimeMessage(headers, explicitMultipart);
        }

        boolean hasText = textBody != null;
        boolean hasHtml = htmlBody != null;
        boolean hasAttachments = !attachments.isEmpty();
        boolean hasInline = !inlineParts.isEmpty();

        if (!hasText && !hasHtml && !hasAttachments) {
            // Empty message
            headers.set("Content-Type", "text/plain; charset=" + bodyCharset.name());
            MimePart body = new MimePart(new MimeHeaders(), new byte[0]);
            return new MimeMessage(headers, body);
        }

        if (hasText && !hasHtml && !hasAttachments && !hasInline) {
            // Simple text message
            headers.set("Content-Type", "text/plain; charset=" + bodyCharset.name());
            headers.set("Content-Transfer-Encoding", "quoted-printable");
            byte[] encoded = QuotedPrintableCodec.encode(textBody.getBytes(bodyCharset))
                    .getBytes(StandardCharsets.US_ASCII);
            MimePart body = new MimePart(new MimeHeaders(), encoded);
            return new MimeMessage(headers, body);
        }

        // Multipart message needed
        var outerBuilder = MultipartBuilder.mixed();

        // Build text/HTML alternatives
        MimePart textPart = null;
        MimePart htmlPart = null;

        if (hasText) {
            textPart = MimePartBuilder.create()
                    .textPlain(bodyCharset)
                    .transferEncoding(ContentTransferEncoding.QUOTED_PRINTABLE)
                    .content(textBody, bodyCharset)
                    .encodeContent()
                    .build();
        }
        if (hasHtml) {
            htmlPart = MimePartBuilder.create()
                    .textHtml(bodyCharset)
                    .transferEncoding(ContentTransferEncoding.QUOTED_PRINTABLE)
                    .content(htmlBody, bodyCharset)
                    .encodeContent()
                    .build();
        }

        Object contentPart;
        if (hasText && hasHtml) {
            var altBuilder = MultipartBuilder.alternative();
            altBuilder.addPart(textPart);
            if (hasInline) {
                var relatedBuilder = MultipartBuilder.related();
                relatedBuilder.addPart(htmlPart);
                for (MimePart ip : inlineParts) {
                    relatedBuilder.addPart(ip);
                }
                altBuilder.addPart(relatedBuilder);
            } else {
                altBuilder.addPart(htmlPart);
            }
            contentPart = altBuilder.build();
        } else if (hasHtml && hasInline) {
            var relatedBuilder = MultipartBuilder.related();
            relatedBuilder.addPart(htmlPart);
            for (MimePart ip : inlineParts) {
                relatedBuilder.addPart(ip);
            }
            contentPart = relatedBuilder.build();
        } else {
            contentPart = hasText ? textPart : htmlPart;
        }

        if (hasAttachments) {
            if (contentPart instanceof MimeMultipart mm) {
                outerBuilder.addPart(mm);
            } else if (contentPart instanceof MimePart mp) {
                outerBuilder.addPart(mp);
            }
            for (MimePart att : attachments) {
                outerBuilder.addPart(att);
            }
            MimeMultipart multipart = outerBuilder.build();
            headers.set("Content-Type", outerBuilder.contentTypeValue());
            return new MimeMessage(headers, multipart);
        } else if (contentPart instanceof MimeMultipart mm) {
            headers.set("Content-Type",
                    "multipart/" + (mm.multipartType() != null
                            ? mm.multipartType().subtype() : "mixed")
                            + "; boundary=\"" + mm.boundary() + "\"");
            return new MimeMessage(headers, mm);
        } else {
            // Single part without attachments
            MimePart mp = (MimePart) contentPart;
            // Copy headers from part to message
            for (var field : mp.headers().fields()) {
                headers.set(field.name(), field.rawValue());
            }
            return new MimeMessage(headers, mp);
        }
    }

    private String formatMailboxList(List<Mailbox> mailboxes) {
        var sb = new StringBuilder();
        for (int i = 0; i < mailboxes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(mailboxes.get(i).toWireFormat());
        }
        return sb.toString();
    }

    // Import for QP encoding
    private static final class QuotedPrintableCodec {
        static String encode(byte[] data) {
            return ssg.legoflow.email.common.encoding.QuotedPrintableCodec.encode(data);
        }
    }
}
