package ssg.legoflow.email.common.mime;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Parses raw bytes into a {@link MimeMessage}.
 *
 * <p>Handles header parsing, body extraction, multipart boundary detection,
 * and recursive nesting of multipart containers.
 *
 * @since 0.1.0
 */
public final class MimeParser {

    private MimeParser() {
    }

    /**
     * Parses a complete MIME message from raw bytes.
     *
     * @param data the raw message bytes
     * @return the parsed MimeMessage
     * @throws IllegalArgumentException if the data is malformed
     */
    public static MimeMessage parse(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Message data must not be empty");
        }
        return parse(new String(data, StandardCharsets.UTF_8));
    }

    /**
     * Parses a complete MIME message from a string.
     *
     * @param message the raw message string
     * @return the parsed MimeMessage
     */
    public static MimeMessage parse(String message) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Message string must not be empty");
        }

        // Split headers from body at the blank line
        int headerEnd = findHeaderEnd(message);
        String headerBlock;
        String bodyBlock;

        if (headerEnd >= 0) {
            headerBlock = message.substring(0, headerEnd);
            // Skip the blank line separator
            int bodyStart = headerEnd;
            if (bodyStart < message.length() && message.charAt(bodyStart) == '\r') bodyStart++;
            if (bodyStart < message.length() && message.charAt(bodyStart) == '\n') bodyStart++;
            if (bodyStart < message.length() && message.charAt(bodyStart) == '\r') bodyStart++;
            if (bodyStart < message.length() && message.charAt(bodyStart) == '\n') bodyStart++;
            bodyBlock = bodyStart < message.length() ? message.substring(bodyStart) : "";
        } else {
            // No body — all headers
            headerBlock = message;
            bodyBlock = "";
        }

        MimeHeaders headers = MimeHeaders.parse(headerBlock);
        ContentType contentType = headers.contentType();

        if (contentType.isMultipart()) {
            String boundary = contentType.boundary();
            if (boundary == null) {
                throw new IllegalArgumentException("Multipart message missing boundary parameter");
            }
            MimeMultipart multipart = parseMultipart(bodyBlock, boundary, contentType.subtype());
            return new MimeMessage(headers, multipart);
        } else {
            byte[] bodyBytes = bodyBlock.getBytes(StandardCharsets.UTF_8);
            MimePart body = new MimePart(new MimeHeaders(), bodyBytes);
            return new MimeMessage(headers, body);
        }
    }

    /**
     * Parses a multipart body from a string.
     *
     * @param body     the body text
     * @param boundary the boundary string
     * @param subtype  the multipart subtype
     * @return the parsed MimeMultipart
     */
    static MimeMultipart parseMultipart(String body, String boundary, String subtype) {
        String delimiter = "--" + boundary;
        String closeDelimiter = delimiter + "--";

        var parts = new ArrayList<Object>();
        String preamble = null;
        String epilogue = null;

        // Find first boundary
        int firstBoundary = body.indexOf(delimiter);
        if (firstBoundary < 0) {
            // No boundaries found — treat as empty multipart
            MultipartType mpt = MultipartType.tryParse(subtype);
            return new MimeMultipart(boundary, mpt, parts, body.trim(), null);
        }

        // Preamble is text before first boundary
        if (firstBoundary > 0) {
            String pre = body.substring(0, firstBoundary).trim();
            if (!pre.isEmpty()) {
                preamble = pre;
            }
        }

        // Split on boundaries
        int pos = firstBoundary;
        while (pos < body.length()) {
            // Skip the boundary line itself
            int lineEnd = body.indexOf('\n', pos);
            if (lineEnd < 0) break;
            int partStart = lineEnd + 1;

            // Check if this is the closing boundary
            String boundaryLine = body.substring(pos, lineEnd).trim();
            if (boundaryLine.equals(closeDelimiter) || boundaryLine.startsWith(closeDelimiter)) {
                // Epilogue after closing boundary
                if (partStart < body.length()) {
                    String epi = body.substring(partStart).trim();
                    if (!epi.isEmpty()) {
                        epilogue = epi;
                    }
                }
                break;
            }

            // Find next boundary
            int nextBoundary = body.indexOf(delimiter, partStart);
            if (nextBoundary < 0) {
                // Last part — no closing boundary (lenient)
                String partText = body.substring(partStart);
                parts.add(parsePart(partText));
                break;
            }

            // Extract part text (remove trailing CRLF before boundary)
            String partText = body.substring(partStart, nextBoundary);
            if (partText.endsWith("\r\n")) {
                partText = partText.substring(0, partText.length() - 2);
            } else if (partText.endsWith("\n")) {
                partText = partText.substring(0, partText.length() - 1);
            }

            parts.add(parsePart(partText));
            pos = nextBoundary;
        }

        MultipartType mpt = MultipartType.tryParse(subtype);
        return new MimeMultipart(boundary, mpt, parts, preamble, epilogue);
    }

    /**
     * Parses a single MIME part (headers + body) which may itself be multipart.
     */
    private static Object parsePart(String partText) {
        int headerEnd = findHeaderEnd(partText);
        String headerBlock;
        String bodyBlock;

        if (headerEnd >= 0) {
            headerBlock = partText.substring(0, headerEnd);
            int bodyStart = headerEnd;
            if (bodyStart < partText.length() && partText.charAt(bodyStart) == '\r') bodyStart++;
            if (bodyStart < partText.length() && partText.charAt(bodyStart) == '\n') bodyStart++;
            if (bodyStart < partText.length() && partText.charAt(bodyStart) == '\r') bodyStart++;
            if (bodyStart < partText.length() && partText.charAt(bodyStart) == '\n') bodyStart++;
            bodyBlock = bodyStart < partText.length() ? partText.substring(bodyStart) : "";
        } else {
            // No headers — entire text is body
            headerBlock = "";
            bodyBlock = partText;
        }

        MimeHeaders headers = MimeHeaders.parse(headerBlock);
        ContentType contentType = headers.contentType();

        if (contentType.isMultipart()) {
            String boundary = contentType.boundary();
            if (boundary != null) {
                return parseMultipart(bodyBlock, boundary, contentType.subtype());
            }
        }

        byte[] bodyBytes = bodyBlock.getBytes(StandardCharsets.UTF_8);
        return new MimePart(headers, bodyBytes);
    }

    /**
     * Finds the position of the blank line separating headers from body.
     *
     * @return the position of the first blank line, or -1 if not found
     */
    private static int findHeaderEnd(String text) {
        // Look for \r\n\r\n or \n\n
        int crlfcrlf = text.indexOf("\r\n\r\n");
        int lflf = text.indexOf("\n\n");

        if (crlfcrlf >= 0 && (lflf < 0 || crlfcrlf < lflf)) {
            return crlfcrlf;
        }
        return lflf;
    }
}
