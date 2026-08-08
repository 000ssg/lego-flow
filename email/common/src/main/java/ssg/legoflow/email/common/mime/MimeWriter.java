package ssg.legoflow.email.common.mime;

import ssg.legoflow.email.common.encoding.Base64Codec;
import ssg.legoflow.email.common.encoding.QuotedPrintableCodec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serializes a {@link MimeMessage} to raw bytes.
 *
 * <p>Produces RFC 2045-compliant output with properly delimited multipart
 * boundaries, encoded content, and folded headers.
 *
 * @since 0.1.0
 */
public final class MimeWriter {

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private MimeWriter() {
    }

    /**
     * Serializes a MimeMessage to bytes.
     *
     * @param message the message to serialize
     * @return the raw message bytes
     */
    public static byte[] write(MimeMessage message) {
        var out = new ByteArrayOutputStream();
        try {
            writeHeaders(out, message.headers());
            out.write(CRLF); // blank line separator

            if (message.isMultipart()) {
                writeMultipart(out, message.multipartBody());
            } else if (message.body() != null) {
                out.write(message.body().rawContent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write MIME message", e);
        }
        return out.toByteArray();
    }

    /**
     * Serializes a MimePart to bytes.
     *
     * @param part the part to serialize
     * @return the raw part bytes
     */
    public static byte[] writePart(MimePart part) {
        var out = new ByteArrayOutputStream();
        try {
            writeHeaders(out, part.headers());
            out.write(CRLF);
            out.write(part.rawContent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write MIME part", e);
        }
        return out.toByteArray();
    }

    /**
     * Encodes content bytes according to the specified transfer encoding.
     *
     * @param content  the raw content
     * @param encoding the transfer encoding to apply
     * @return the encoded bytes
     */
    public static byte[] encodeContent(byte[] content, ContentTransferEncoding encoding) {
        return switch (encoding) {
            case BASE64 -> Base64Codec.encode(content).getBytes(StandardCharsets.US_ASCII);
            case QUOTED_PRINTABLE -> QuotedPrintableCodec.encode(content)
                    .getBytes(StandardCharsets.US_ASCII);
            case SEVEN_BIT, EIGHT_BIT, BINARY -> content;
        };
    }

    private static void writeHeaders(ByteArrayOutputStream out, MimeHeaders headers)
            throws IOException {
        String headerText = headers.toWireFormat();
        out.write(headerText.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeMultipart(ByteArrayOutputStream out, MimeMultipart multipart)
            throws IOException {
        String boundary = multipart.boundary();
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] closeDelimiter = ("--" + boundary + "--").getBytes(StandardCharsets.US_ASCII);

        // Preamble
        if (multipart.preamble() != null && !multipart.preamble().isEmpty()) {
            out.write(multipart.preamble().getBytes(StandardCharsets.UTF_8));
            out.write(CRLF);
        }

        // Parts
        for (Object part : multipart.parts()) {
            out.write(delimiter);
            out.write(CRLF);

            if (part instanceof MimePart mp) {
                writeHeaders(out, mp.headers());
                out.write(CRLF);
                out.write(mp.rawContent());
            } else if (part instanceof MimeMultipart mm) {
                // Nested multipart — write its Content-Type header then the body
                var nestedHeaders = new MimeHeaders();
                MultipartType mpt = mm.multipartType();
                String subtype = mpt != null ? mpt.subtype() : "mixed";
                nestedHeaders.set("Content-Type",
                        "multipart/" + subtype + "; boundary=\"" + mm.boundary() + "\"");
                writeHeaders(out, nestedHeaders);
                out.write(CRLF);
                writeMultipart(out, mm);
            }
            out.write(CRLF);
        }

        // Closing boundary
        out.write(closeDelimiter);
        out.write(CRLF);

        // Epilogue
        if (multipart.epilogue() != null && !multipart.epilogue().isEmpty()) {
            out.write(multipart.epilogue().getBytes(StandardCharsets.UTF_8));
            out.write(CRLF);
        }
    }
}
