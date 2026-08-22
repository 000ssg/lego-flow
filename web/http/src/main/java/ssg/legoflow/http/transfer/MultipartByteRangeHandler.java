package ssg.legoflow.http.transfer;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
/**
 * Handles multipart/byteranges responses per RFC 7233 §4.3.
 *
 * <p>When multiple ranges are requested, the server responds with a
 * multipart/byteranges content type containing each range as a separate part.
 *
 * @since 0.1.0
 */
public class MultipartByteRangeHandler {

    private static final String CRLF = "\r\n";

    /**
     * Generates a unique boundary string for multipart responses.
     *
     * @return a boundary string
     */
    public static String generateBoundary() {
        return "ByteRangeBoundary_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Builds a multipart/byteranges response for multiple ranges.
     *
     * @param content     the full content as a ByteBuffer
     * @param ranges      the list of byte ranges to include
     * @param totalSize   the total size of the original content
     * @param contentType the content type of the original resource
     * @return the HTTP response with multipart/byteranges body
     */
    public static HttpResponse buildMultipartResponse(ByteBuffer content,
                                                       List<ByteRangeHandler.ByteRange> ranges,
                                                       long totalSize,
                                                       String contentType) {
        String boundary = generateBoundary();
        byte[] body = buildMultipartBody(content, ranges, totalSize, contentType, boundary);

        HttpResponse response = HttpResponse.of(HttpStatus.PARTIAL_CONTENT);
        response.setBody(ByteBuffer.wrap(body));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE,
                "multipart/byteranges; boundary=" + boundary);
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        return response;
    }

    /**
     * Builds the multipart body bytes with all range parts.
     *
     * @param content     the full content
     * @param ranges      the ranges to include
     * @param totalSize   the total content size
     * @param contentType the content type of each part
     * @param boundary    the multipart boundary string
     * @return the encoded multipart body
     */
    public static byte[] buildMultipartBody(ByteBuffer content,
                                             List<ByteRangeHandler.ByteRange> ranges,
                                             long totalSize,
                                             String contentType,
                                             String boundary) {
        var out = new ByteArrayOutputStream();
        for (var range : ranges) {
            // Boundary delimiter
            writeString(out, "--" + boundary + CRLF);
            // Part headers
            if (contentType != null) {
                writeString(out, "Content-Type: " + contentType + CRLF);
            }
            writeString(out, "Content-Range: " +
                    ByteRangeHandler.formatContentRange(range, totalSize) + CRLF);
            writeString(out, CRLF);
            // Part body
            ByteBuffer rangeData = ByteRangeHandler.extractRange(content, range);
            byte[] rangeBytes = new byte[rangeData.remaining()];
            rangeData.get(rangeBytes);
            out.writeBytes(rangeBytes);
            writeString(out, CRLF);
        }
        // Final boundary
        writeString(out, "--" + boundary + "--" + CRLF);
        return out.toByteArray();
    }

    /**
     * Parses a multipart/byteranges response body into individual parts.
     *
     * @param body     the multipart body bytes
     * @param boundary the boundary string
     * @return list of parts, each containing the raw bytes of that range
     */
    public static List<byte[]> parseMultipartBody(byte[] body, String boundary) {
        var parts = new java.util.ArrayList<byte[]>();
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        String delimiter = "--" + boundary;
        String endDelimiter = delimiter + "--";

        String[] sections = bodyStr.split(java.util.regex.Pattern.quote(delimiter));
        for (String section : sections) {
            if (section.isEmpty() || section.startsWith("--")) {
                continue;
            }
            // Find the blank line separating headers from body
            int blankLine = section.indexOf(CRLF + CRLF);
            if (blankLine >= 0) {
                String partBody = section.substring(blankLine + 4);
                // Remove trailing CRLF
                if (partBody.endsWith(CRLF)) {
                    partBody = partBody.substring(0, partBody.length() - 2);
                }
                parts.add(partBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        return parts;
    }

    /**
     * Extracts the boundary from a multipart/byteranges content-type header.
     *
     * @param contentTypeHeader the Content-Type header value
     * @return the boundary string, or null if not found
     */
    public static String extractBoundary(String contentTypeHeader) {
        if (contentTypeHeader == null) {
            return null;
        }
        for (String part : contentTypeHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return trimmed.substring(9).trim();
            }
        }
        return null;
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
