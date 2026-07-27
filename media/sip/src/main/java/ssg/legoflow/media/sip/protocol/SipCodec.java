package ssg.legoflow.media.sip.protocol;

import ssg.legoflow.media.sip.header.SipHeaders;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * SIP message codec for encoding and decoding requests and responses.
 *
 * <p>Handles the text-based SIP protocol format with HTTP-like syntax,
 * including multi-line header folding (RFC 3261 section 7.3.1) and
 * parameter parsing.
 *
 * @since 1.0.0
 */
public final class SipCodec {

    private ByteBuffer accumulator;

    /**
     * Creates a new stream-oriented SIP codec instance.
     *
     * @since 1.0.0
     */
    public SipCodec() {}

    @SuppressWarnings("unused")
    private static final SipCodec STATIC_INSTANCE = null;

    /**
     * Encodes a SIP request to bytes.
     *
     * @param request the request to encode
     * @return the encoded bytes
     * @since 1.0.0
     */
    public static byte[] encode(SipRequest request) {
        var sb = new StringBuilder();
        sb.append(request.requestLine()).append("\r\n");
        sb.append(request.headers().format());
        sb.append("\r\n");
        byte[] header = sb.toString().getBytes(StandardCharsets.UTF_8);
        if (request.hasBody()) {
            byte[] body = request.body();
            byte[] result = new byte[header.length + body.length];
            System.arraycopy(header, 0, result, 0, header.length);
            System.arraycopy(body, 0, result, header.length, body.length);
            return result;
        }
        return header;
    }

    /**
     * Encodes a SIP response to bytes.
     *
     * @param response the response to encode
     * @return the encoded bytes
     * @since 1.0.0
     */
    public static byte[] encode(SipResponse response) {
        var sb = new StringBuilder();
        sb.append(response.statusLine()).append("\r\n");
        sb.append(response.headers().format());
        sb.append("\r\n");
        byte[] header = sb.toString().getBytes(StandardCharsets.UTF_8);
        if (response.hasBody()) {
            byte[] body = response.body();
            byte[] result = new byte[header.length + body.length];
            System.arraycopy(header, 0, result, 0, header.length);
            System.arraycopy(body, 0, result, header.length, body.length);
            return result;
        }
        return header;
    }

    /**
     * Encodes any SIP message to bytes.
     *
     * @param message the message to encode
     * @return the encoded bytes
     * @since 1.0.0
     */
    public static byte[] encode(SipMessage message) {
        return switch (message) {
            case SipRequest req -> encode(req);
            case SipResponse res -> encode(res);
        };
    }

    /**
     * Decodes a SIP message from bytes, auto-detecting request vs. response.
     *
     * @param data the raw bytes
     * @return the decoded message
     * @throws IOException if parsing fails
     * @since 1.0.0
     */
    public static SipMessage decode(byte[] data) throws IOException {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("SIP/")) {
            return decodeResponse(data);
        }
        return decodeRequest(data);
    }

    /**
     * Decodes a SIP request from bytes.
     *
     * @param data the raw bytes
     * @return the decoded request
     * @throws IOException if parsing fails
     * @since 1.0.0
     */
    public static SipRequest decodeRequest(byte[] data) throws IOException {
        var reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Empty request line");
        }

        String[] parts = requestLine.split("\\s+", 3);
        if (parts.length < 3) {
            throw new IOException("Invalid request line: " + requestLine);
        }

        SipMethod method = SipMethod.fromName(parts[0]);
        String requestUri = parts[1];
        String version = parts[2];

        SipHeaders headers = readHeaders(reader);
        byte[] body = readBody(data, headers.contentLength());

        return new SipRequest(method, requestUri, version, headers, body);
    }

    /**
     * Decodes a SIP response from bytes.
     *
     * @param data the raw bytes
     * @return the decoded response
     * @throws IOException if parsing fails
     * @since 1.0.0
     */
    public static SipResponse decodeResponse(byte[] data) throws IOException {
        var reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));

        String statusLine = reader.readLine();
        if (statusLine == null || statusLine.isEmpty()) {
            throw new IOException("Empty status line");
        }

        String[] parts = statusLine.split("\\s+", 3);
        if (parts.length < 3) {
            throw new IOException("Invalid status line: " + statusLine);
        }

        String version = parts[0];
        int statusCode = Integer.parseInt(parts[1]);
        String reasonPhrase = parts[2];

        SipHeaders headers = readHeaders(reader);
        byte[] body = readBody(data, headers.contentLength());

        return new SipResponse(version, statusCode, reasonPhrase, headers, body);
    }

    // ---- Stream-oriented instance methods ----

    /**
     * Feeds request data into the accumulator and returns a parsed request
     * when a complete message has been received, or {@code null} if more data is needed.
     *
     * <p>SIP uses HTTP-like framing: headers end with {@code \r\n\r\n}, and body
     * length is determined by the Content-Length header.
     *
     * @param data the incoming data chunk
     * @return the parsed request, or null if the message is not yet complete
     * @throws IOException if parsing fails on a complete message
     * @since 1.0.0
     */
    public SipRequest feedRequestData(ByteBuffer data) throws IOException {
        var combined = combineWithAccumulator(data);
        byte[] bytes = new byte[combined.remaining()];
        combined.get(bytes);

        // Check if headers are complete
        int headerEnd = findHeaderEnd(bytes);
        if (headerEnd < 0) {
            accumulator = ByteBuffer.wrap(bytes);
            return null;
        }

        // Parse headers to get Content-Length
        int contentLength = parseContentLengthFromRaw(bytes, headerEnd);
        int totalNeeded = headerEnd + 4 + contentLength;

        if (bytes.length < totalNeeded) {
            accumulator = ByteBuffer.wrap(bytes);
            return null;
        }

        // Complete message: parse and save remainder
        byte[] messageBytes = new byte[totalNeeded];
        System.arraycopy(bytes, 0, messageBytes, 0, totalNeeded);

        if (bytes.length > totalNeeded) {
            int remaining = bytes.length - totalNeeded;
            accumulator = ByteBuffer.allocate(remaining);
            accumulator.put(bytes, totalNeeded, remaining);
            accumulator.flip();
        } else {
            accumulator = null;
        }

        return decodeRequest(messageBytes);
    }

    /**
     * Feeds response data into the accumulator and returns a parsed response
     * when a complete message has been received, or {@code null} if more data is needed.
     *
     * @param data the incoming data chunk
     * @return the parsed response, or null if the message is not yet complete
     * @throws IOException if parsing fails on a complete message
     * @since 1.0.0
     */
    public SipResponse feedResponseData(ByteBuffer data) throws IOException {
        var combined = combineWithAccumulator(data);
        byte[] bytes = new byte[combined.remaining()];
        combined.get(bytes);

        int headerEnd = findHeaderEnd(bytes);
        if (headerEnd < 0) {
            accumulator = ByteBuffer.wrap(bytes);
            return null;
        }

        int contentLength = parseContentLengthFromRaw(bytes, headerEnd);
        int totalNeeded = headerEnd + 4 + contentLength;

        if (bytes.length < totalNeeded) {
            accumulator = ByteBuffer.wrap(bytes);
            return null;
        }

        byte[] messageBytes = new byte[totalNeeded];
        System.arraycopy(bytes, 0, messageBytes, 0, totalNeeded);

        if (bytes.length > totalNeeded) {
            int remaining = bytes.length - totalNeeded;
            accumulator = ByteBuffer.allocate(remaining);
            accumulator.put(bytes, totalNeeded, remaining);
            accumulator.flip();
        } else {
            accumulator = null;
        }

        return decodeResponse(messageBytes);
    }

    /**
     * Returns true if there is buffered data awaiting more input.
     *
     * @return true if the internal accumulator has remaining bytes
     * @since 1.0.0
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    private ByteBuffer combineWithAccumulator(ByteBuffer data) {
        int totalSize = (accumulator != null ? accumulator.remaining() : 0) + data.remaining();
        var combined = ByteBuffer.allocate(totalSize);
        if (accumulator != null) {
            combined.put(accumulator.duplicate());
            accumulator = null;
        }
        combined.put(data.duplicate());
        combined.flip();
        return combined;
    }

    private static int findHeaderEnd(byte[] bytes) {
        for (int i = 0; i <= bytes.length - 4; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n'
                    && bytes[i + 2] == '\r' && bytes[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static int parseContentLengthFromRaw(byte[] bytes, int headerEnd) {
        String headerSection = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        for (String line : headerSection.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                // Support both full name and compact form 'l' per RFC 3261
                if (name.equalsIgnoreCase("Content-Length") || name.equals("l")) {
                    return Integer.parseInt(line.substring(colon + 1).trim());
                }
            }
        }
        return 0;
    }

    // ---- Static methods (backward compatible) ----

    /**
     * Reads and unfolds headers from a reader.
     *
     * <p>Handles multi-line header folding per RFC 3261 section 7.3.1:
     * lines beginning with SP or HT are folded into the previous line.
     */
    private static SipHeaders readHeaders(BufferedReader reader) throws IOException {
        var headers = new SipHeaders();
        String previousName = null;
        StringBuilder previousValue = null;

        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // Check for header folding (line starts with SP or HT)
            if ((line.charAt(0) == ' ' || line.charAt(0) == '\t') && previousName != null) {
                // Continuation line: append to previous header value
                previousValue.append(' ').append(line.strip());
                continue;
            }

            // Flush previous header
            if (previousName != null) {
                headers.add(previousName, previousValue.toString());
            }

            int colon = line.indexOf(':');
            if (colon > 0) {
                previousName = line.substring(0, colon).strip();
                previousValue = new StringBuilder(line.substring(colon + 1).strip());
            } else {
                previousName = null;
                previousValue = null;
            }
        }

        // Flush last header
        if (previousName != null) {
            headers.add(previousName, previousValue.toString());
        }

        return headers;
    }

    /**
     * Extracts the body from the raw data based on content length.
     */
    private static byte[] readBody(byte[] data, int contentLength) {
        if (contentLength <= 0) {
            return new byte[0];
        }
        String text = new String(data, StandardCharsets.UTF_8);
        int bodyStart = text.indexOf("\r\n\r\n");
        if (bodyStart < 0) {
            return new byte[0];
        }
        bodyStart += 4;
        int available = data.length - bodyStart;
        int len = Math.min(contentLength, available);
        byte[] body = new byte[len];
        System.arraycopy(data, bodyStart, body, 0, len);
        return body;
    }
}
