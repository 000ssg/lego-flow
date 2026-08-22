package ssg.legoflow.email.common.mime;

import ssg.legoflow.email.common.encoding.Base64Codec;
import ssg.legoflow.email.common.encoding.QuotedPrintableCodec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
/**
 * A single MIME part consisting of headers and content.
 *
 * <p>Content is stored in its raw (encoded) form. Methods are provided
 * to decode the content based on the Content-Transfer-Encoding.
 *
 * @since 0.1.0
 */
public final class MimePart {

    private final MimeHeaders headers;
    private final byte[] rawContent;

    /**
     * Creates a MIME part with headers and raw content.
     *
     * @param headers    the part headers
     * @param rawContent the raw (possibly encoded) content bytes
     */
    public MimePart(MimeHeaders headers, byte[] rawContent) {
        this.headers = Objects.requireNonNull(headers, "Headers must not be null");
        this.rawContent = rawContent != null ? rawContent.clone() : new byte[0];
    }

    /**
     * Returns the headers for this part.
     *
     * @return the MIME headers
     */
    public MimeHeaders headers() {
        return headers;
    }

    /**
     * Returns the raw (encoded) content bytes.
     *
     * @return a copy of the raw content
     */
    public byte[] rawContent() {
        return rawContent.clone();
    }

    /**
     * Returns the decoded content bytes based on Content-Transfer-Encoding.
     *
     * @return the decoded content
     */
    public byte[] decodedContent() {
        ContentTransferEncoding encoding = headers.contentTransferEncoding();
        return switch (encoding) {
            case BASE64 -> Base64Codec.decode(new String(rawContent, StandardCharsets.US_ASCII));
            case QUOTED_PRINTABLE -> QuotedPrintableCodec.decode(
                    new String(rawContent, StandardCharsets.US_ASCII));
            case SEVEN_BIT, EIGHT_BIT, BINARY -> rawContent.clone();
        };
    }

    /**
     * Returns the decoded content as a string using the charset from Content-Type.
     *
     * @return the decoded text content
     */
    public String decodedContentAsString() {
        byte[] decoded = decodedContent();
        Charset charset = headers.contentType().charset();
        return new String(decoded, charset);
    }

    /**
     * Returns the Content-Type of this part.
     *
     * @return the content type
     */
    public ContentType contentType() {
        return headers.contentType();
    }

    /**
     * Returns the Content-Disposition of this part.
     *
     * @return the content disposition, or null
     */
    public ContentDisposition contentDisposition() {
        return headers.contentDisposition();
    }

    /**
     * Returns the Content-Transfer-Encoding of this part.
     *
     * @return the encoding
     */
    public ContentTransferEncoding contentTransferEncoding() {
        return headers.contentTransferEncoding();
    }

    /**
     * Returns the filename from Content-Disposition or Content-Type name parameter.
     *
     * @return the filename, or null if not specified
     */
    public String filename() {
        ContentDisposition disp = contentDisposition();
        if (disp != null && disp.filename() != null) {
            return disp.filename();
        }
        return contentType().name();
    }

    /**
     * Checks whether this part is a text part.
     *
     * @return true if Content-Type starts with "text/"
     */
    public boolean isText() {
        return contentType().isText();
    }

    /**
     * Checks whether this part is an attachment.
     *
     * @return true if Content-Disposition is "attachment"
     */
    public boolean isAttachment() {
        ContentDisposition disp = contentDisposition();
        return disp != null && disp.isAttachment();
    }

    /**
     * Checks whether this part is inline.
     *
     * @return true if Content-Disposition is "inline" or not specified
     */
    public boolean isInline() {
        ContentDisposition disp = contentDisposition();
        return disp == null || disp.isInline();
    }

    @Override
    public String toString() {
        return "MimePart{" + contentType().mediaType()
                + ", encoding=" + contentTransferEncoding()
                + ", size=" + rawContent.length + "}";
    }
}
