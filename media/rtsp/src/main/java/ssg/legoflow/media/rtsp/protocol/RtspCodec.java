package ssg.legoflow.media.rtsp.protocol;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.nio.charset.StandardCharsets;

/**
 * RTSP 2.0 message codec for encoding and decoding requests and responses.
 *
 * <p>Handles the text-based RTSP protocol format with HTTP-like syntax.
 * Also detects interleaved binary frames ($ prefix) in the TCP stream.
 *
 * @since 0.1.0
 */
public final class RtspCodec {

    private ByteBuffer accumulator;

    /**
     * Creates a new stream-oriented RTSP codec instance.
     */
    public RtspCodec() {}

    /**
     * Creates a stateless codec (for backward compatibility with static methods).
     */
    @SuppressWarnings("unused")
    private static final RtspCodec STATIC_INSTANCE = null;

    /**
     * Encodes an RTSP request to bytes.
     *
     * @param request the request to encode
     * @return the encoded bytes
     */
    public static byte[] encodeRequest(RtspRequest request) {
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
     * Encodes an RTSP response to bytes.
     *
     * @param response the response to encode
     * @return the encoded bytes
     */
    public static byte[] encodeResponse(RtspResponse response) {
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
     * Decodes an RTSP request from bytes.
     *
     * @param data the raw bytes
     * @return the decoded request
     * @throws IOException if parsing fails
     */
    public static RtspRequest decodeRequest(byte[] data) throws IOException {
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

        RtspMethod method = RtspMethod.fromName(parts[0]);
        URI uri = URI.create(parts[1]);
        // parts[2] is RTSP/2.0

        RtspHeaders headers = readHeaders(reader);
        byte[] body = readBody(data, headers.contentLength());

        return new RtspRequest(method, uri, headers, body);
    }

    /**
     * Decodes an RTSP response from bytes.
     *
     * @param data the raw bytes
     * @return the decoded response
     * @throws IOException if parsing fails
     */
    public static RtspResponse decodeResponse(byte[] data) throws IOException {
        var reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));

        String statusLine = reader.readLine();
        if (statusLine == null || statusLine.isEmpty()) {
            throw new IOException("Empty status line");
        }

        String[] parts = statusLine.split("\\s+", 3);
        if (parts.length < 2) {
            throw new IOException("Invalid status line: " + statusLine);
        }

        // parts[0] is RTSP/2.0
        int statusCode = Integer.parseInt(parts[1]);
        RtspStatus status = RtspStatus.fromCode(statusCode);

        RtspHeaders headers = readHeaders(reader);
        byte[] body = readBody(data, headers.contentLength());

        return new RtspResponse(status, headers, body);
    }

    // ---- Stream-oriented instance methods ----

    /**
     * Feeds request data into the accumulator and returns a parsed request
     * when a complete message has been received, or {@code null} if more data is needed.
     *
     * <p>RTSP uses HTTP-like framing: headers end with {@code \r\n\r\n}, and body
     * length is determined by the Content-Length header.
     *
     * @param data the incoming data chunk
     * @return the parsed request, or null if the message is not yet complete
     * @throws IOException if parsing fails on a complete message
     */
    public RtspRequest feedRequestData(ByteBuffer data) throws IOException {
        var combined = combineWithAccumulator(data);
        byte[] bytes = new byte[combined.remaining()];
        combined.get(bytes);

        // Check if headers are complete
        int headerEnd = findHeaderEnd(bytes);
        if (headerEnd < 0) {
            // Headers not yet complete — accumulate
            accumulator = ByteBuffer.wrap(bytes);
            return null;
        }

        // Parse headers to get Content-Length
        int contentLength = parseContentLength(bytes, headerEnd);
        int totalNeeded = headerEnd + 4 + contentLength; // headerEnd + \r\n\r\n + body

        if (bytes.length < totalNeeded) {
            // Body not yet complete — accumulate
            accumulator = ByteBuffer.wrap(bytes);
            return null;
        }

        // Complete message: parse and save remainder
        byte[] messageBytes = new byte[totalNeeded];
        System.arraycopy(bytes, 0, messageBytes, 0, totalNeeded);

        if (bytes.length > totalNeeded) {
            int remaining = bytes.length - totalNeeded;
            accumulator = BufferPool.getBuffer(remaining);
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
     */
    public RtspResponse feedResponseData(ByteBuffer data) throws IOException {
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
        int contentLength = parseContentLength(bytes, headerEnd);
        int totalNeeded = headerEnd + 4 + contentLength;

        if (bytes.length < totalNeeded) {
            accumulator = ByteBuffer.wrap(bytes);
            return null;
        }

        // Complete message
        byte[] messageBytes = new byte[totalNeeded];
        System.arraycopy(bytes, 0, messageBytes, 0, totalNeeded);

        if (bytes.length > totalNeeded) {
            int remaining = bytes.length - totalNeeded;
            accumulator = BufferPool.getBuffer(remaining);
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
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    private ByteBuffer combineWithAccumulator(ByteBuffer data) {
        int totalSize = (accumulator != null ? accumulator.remaining() : 0) + data.remaining();
        var combined = BufferPool.getBuffer(totalSize);
        if (accumulator != null) {
            combined.put(accumulator.duplicate());
            accumulator = null;
        }
        combined.put(data.duplicate());
        combined.flip();
        return combined;
    }

    /**
     * Finds the position of the \r\n\r\n header terminator.
     *
     * @return the index of the first \r of \r\n\r\n, or -1 if not found
     */
    private static int findHeaderEnd(byte[] bytes) {
        for (int i = 0; i <= bytes.length - 4; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n'
                    && bytes[i + 2] == '\r' && bytes[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parses Content-Length from raw header bytes.
     */
    private static int parseContentLength(byte[] bytes, int headerEnd) {
        String headerSection = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        for (String line : headerSection.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                if (name.equalsIgnoreCase("Content-Length")) {
                    return Integer.parseInt(line.substring(colon + 1).trim());
                }
            }
        }
        return 0;
    }

    // ---- Static methods (backward compatible) ----

    /**
     * Returns true if the byte starts an interleaved binary frame ('$').
     *
     * @param b the byte to check
     * @return true if this is the start of an interleaved frame
     */
    public static boolean isInterleavedFrame(byte b) {
        return b == '$';
    }

    private static RtspHeaders readHeaders(BufferedReader reader) throws IOException {
        var headers = new RtspHeaders();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                headers.add(name, value);
            }
        }
        return headers;
    }

    private static byte[] readBody(byte[] data, int contentLength) {
        if (contentLength <= 0) {
            return new byte[0];
        }
        // Find the empty line (\r\n\r\n) that separates headers from body
        String text = new String(data, StandardCharsets.UTF_8);
        int bodyStart = text.indexOf("\r\n\r\n");
        if (bodyStart < 0) {
            return new byte[0];
        }
        bodyStart += 4; // skip \r\n\r\n
        int available = data.length - bodyStart;
        int len = Math.min(contentLength, available);
        byte[] body = new byte[len];
        System.arraycopy(data, bodyStart, body, 0, len);
        return body;
    }
}
