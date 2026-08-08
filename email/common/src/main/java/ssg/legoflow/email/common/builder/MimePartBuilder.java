package ssg.legoflow.email.common.builder;

import ssg.legoflow.email.common.encoding.Base64Codec;
import ssg.legoflow.email.common.encoding.QuotedPrintableCodec;
import ssg.legoflow.email.common.mime.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Fluent builder for creating {@link MimePart} instances.
 *
 * @since 0.1.0
 */
public final class MimePartBuilder {

    private final MimeHeaders headers = new MimeHeaders();
    private byte[] content = new byte[0];
    private ContentTransferEncoding encoding = ContentTransferEncoding.SEVEN_BIT;
    private boolean encodeContent = false;

    private MimePartBuilder() {
    }

    /**
     * Creates a new MimePartBuilder.
     *
     * @return a new builder
     */
    public static MimePartBuilder create() {
        return new MimePartBuilder();
    }

    /**
     * Sets the Content-Type.
     *
     * @param contentType the content type string (e.g., "text/plain; charset=utf-8")
     * @return this builder
     */
    public MimePartBuilder contentType(String contentType) {
        headers.set("Content-Type", contentType);
        return this;
    }

    /**
     * Sets the Content-Type to text/plain with the given charset.
     *
     * @param charset the charset
     * @return this builder
     */
    public MimePartBuilder textPlain(Charset charset) {
        headers.set("Content-Type", "text/plain; charset=" + charset.name());
        return this;
    }

    /**
     * Sets the Content-Type to text/html with the given charset.
     *
     * @param charset the charset
     * @return this builder
     */
    public MimePartBuilder textHtml(Charset charset) {
        headers.set("Content-Type", "text/html; charset=" + charset.name());
        return this;
    }

    /**
     * Sets the Content-Disposition.
     *
     * @param disposition the disposition string
     * @return this builder
     */
    public MimePartBuilder contentDisposition(String disposition) {
        headers.set("Content-Disposition", disposition);
        return this;
    }

    /**
     * Sets the Content-Disposition to attachment with the given filename.
     *
     * @param filename the filename
     * @return this builder
     */
    public MimePartBuilder attachment(String filename) {
        headers.set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        return this;
    }

    /**
     * Sets the Content-Disposition to inline.
     *
     * @return this builder
     */
    public MimePartBuilder inline() {
        headers.set("Content-Disposition", "inline");
        return this;
    }

    /**
     * Sets the Content-ID for inline parts.
     *
     * @param contentId the content ID (without angle brackets)
     * @return this builder
     */
    public MimePartBuilder contentId(String contentId) {
        headers.set("Content-ID", "<" + contentId + ">");
        return this;
    }

    /**
     * Sets the Content-Transfer-Encoding.
     *
     * @param encoding the encoding
     * @return this builder
     */
    public MimePartBuilder transferEncoding(ContentTransferEncoding encoding) {
        this.encoding = encoding;
        headers.set("Content-Transfer-Encoding", encoding.value());
        return this;
    }

    /**
     * Adds a custom header.
     *
     * @param name  the header name
     * @param value the header value
     * @return this builder
     */
    public MimePartBuilder header(String name, String value) {
        headers.set(name, value);
        return this;
    }

    /**
     * Sets the raw content bytes (will be stored as-is).
     *
     * @param content the content bytes
     * @return this builder
     */
    public MimePartBuilder content(byte[] content) {
        this.content = content != null ? content.clone() : new byte[0];
        return this;
    }

    /**
     * Sets the content from a string using the specified charset.
     *
     * @param text    the text content
     * @param charset the charset for encoding
     * @return this builder
     */
    public MimePartBuilder content(String text, Charset charset) {
        this.content = text.getBytes(charset);
        return this;
    }

    /**
     * Sets the content from a UTF-8 string.
     *
     * @param text the text content
     * @return this builder
     */
    public MimePartBuilder content(String text) {
        return content(text, StandardCharsets.UTF_8);
    }

    /**
     * Enables automatic encoding of content when building.
     *
     * @return this builder
     */
    public MimePartBuilder encodeContent() {
        this.encodeContent = true;
        return this;
    }

    /**
     * Builds the MimePart.
     *
     * @return the constructed MimePart
     */
    public MimePart build() {
        byte[] finalContent;
        if (encodeContent) {
            finalContent = MimeWriter.encodeContent(content, encoding);
        } else {
            finalContent = content;
        }
        return new MimePart(headers, finalContent);
    }
}
