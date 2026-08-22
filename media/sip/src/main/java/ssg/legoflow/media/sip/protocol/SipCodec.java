package ssg.legoflow.media.sip.protocol;

import ssg.legoflow.media.sip.header.SipHeaders;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
/**
 * SIP message codec for encoding and decoding requests and responses.
 *
 * <p>Handles the text-based SIP protocol format with HTTP-like syntax,
 * including multi-line header folding (RFC 3261 section 7.3.1) and
 * parameter parsing.
 *
 * @since 0.1.0
 */
public final class SipCodec {

    private static final Queue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_POOL_SIZE = 100;
    private ByteBuffer accumulator;

    /**
     * Creates a new stream-oriented SIP codec instance.
     *
     * @since 0.1.0
     */
    public SipCodec() {}

    /**
     * Encodes a SIP request to bytes.
     *
     * @param request the request to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(SipRequest request) {
        // Try to get a buffer from the pool
        ByteBuffer buf = BUFFER_POOL.poll();
        if (buf == null) {
            buf = ByteBuffer.allocate(1024); // Allocate a reasonable default size
        } else {
            buf.clear(); // Reset buffer position and limit
        }

        try {
            // Use the buffer for encoding
            buf.mark(); // Mark the position for reset if needed
            
            // Encode request line
            String requestLine = request.requestLine();
            byte[] requestLineBytes = requestLine.getBytes(StandardCharsets.UTF_8);
            buf.put(requestLineBytes);
            buf.put("\r\n".getBytes(StandardCharsets.UTF_8));
            
            // Encode headers
            String headers = request.headers().format();
            byte[] headersBytes = headers.getBytes(StandardCharsets.UTF_8);
            buf.put(headersBytes);
            buf.put("\r\n".getBytes(StandardCharsets.UTF_8));
            
            // Handle payload efficiently
            byte[] result;
            if (request.hasBody()) {
                int headerLength = buf.position();
                byte[] body = request.body();
                // Verify we have enough capacity
                if (buf.capacity() < headerLength + body.length) {
                    // Reallocate a larger buffer - increase by 50% to reduce reallocations
                    ByteBuffer newBuf = ByteBuffer.allocate(Math.max(headerLength + body.length, buf.capacity() * 3 / 2));
                    buf.reset(); // Reset to mark
                    newBuf.put(buf.array(), 0, headerLength); // Copy existing data
                    buf = newBuf;
                }
                buf.put(body);
                result = new byte[buf.position()];
                buf.reset(); // Reset position to beginning
                buf.get(result); // Copy to the result array
            } else {
                result = new byte[buf.position()];
                buf.reset(); // Reset position to beginning
                buf.get(result); // Copy to the result array
            }
            
            return result;
        } finally {
            // Return buffer to pool if space available
            if (BUFFER_POOL.size() < MAX_BUFFER_POOL_SIZE) {
                BUFFER_POOL.offer(buf);
            }
        }
    }

    /**
     * Encodes a SIP response to bytes.
     *
     * @param response the response to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(SipResponse response) {
        // Try to get a buffer from the pool
        ByteBuffer buf = BUFFER_POOL.poll();
        if (buf == null) {
            buf = ByteBuffer.allocate(1024); // Allocate a reasonable default size
        } else {
            buf.clear(); // Reset buffer position and limit
        }

        try {
            // Use the buffer for encoding
            buf.mark(); // Mark the position for reset if needed
            
            // Encode status line
            String statusLine = response.statusLine();
            byte[] statusLineBytes = statusLine.getBytes(StandardCharsets.UTF_8);
            buf.put(statusLineBytes);
            buf.put("\r\n".getBytes(StandardCharsets.UTF_8));
            
            // Encode headers
            String headers = response.headers().format();
            byte[] headersBytes = headers.getBytes(StandardCharsets.UTF_8);
            buf.put(headersBytes);
            buf.put("\r\n".getBytes(StandardCharsets.UTF_8));
            
            // Handle payload efficiently
            byte[] result;
            if (response.hasBody()) {
                int headerLength = buf.position();
                byte[] body = response.body();
                // Verify we have enough capacity
                if (buf.capacity() < headerLength + body.length) {
                    // Reallocate a larger buffer - increase by 50% to reduce reallocations
                    ByteBuffer newBuf = ByteBuffer.allocate(Math.max(headerLength + body.length, buf.capacity() * 3 / 2));
                    buf.reset(); // Reset to mark
                    newBuf.put(buf.array(), 0, headerLength); // Copy existing data
                    buf = newBuf;
                }
                buf.put(body);
                result = new byte[buf.position()];
                buf.reset(); // Reset position to beginning
                buf.get(result); // Copy to the result array
            } else {
                result = new byte[buf.position()];
                buf.reset(); // Reset position to beginning
                buf.get(result); // Copy to the result array
            }
            
            return result;
        } finally {
            // Return buffer to pool if space available
            if (BUFFER_POOL.size() < MAX_BUFFER_POOL_SIZE) {
                BUFFER_POOL.offer(buf);
            }
        }
    }

    /**
     * Decodes a SIP request from bytes.
     *
     * @param data the raw bytes
     * @return the parsed request or null if incomplete
     * @since 0.1.0
     */
    SipRequest decodeRequestStream(byte[] data) {
        var combined = combineWithAccumulator(ByteBuffer.wrap(data));
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

        // Parse the complete message
        return parseRequestData(messageBytes);
    }

    /**
     * Parses a complete SIP request from assembled bytes.
     */
    private SipRequest parseRequestData(byte[] data) {
        int headerEnd = findHeaderEnd(data);
        if (headerEnd < 0) return null;
        int contentLength = parseContentLengthFromRaw(data, headerEnd);
        int bodyStart = headerEnd + 4;
        if (data.length < bodyStart + contentLength) return null;
        byte[] body = readBody(data, contentLength);
        String raw = new String(data, 0, headerEnd, StandardCharsets.UTF_8);
        int firstCrlf = raw.indexOf("\r\n");
        String requestLine = (firstCrlf > 0) ? raw.substring(0, firstCrlf).trim() : raw.trim();
        String headersRaw = (firstCrlf > 0) ? raw.substring(firstCrlf + 2) : "";
        SipHeaders headers = new SipHeaders();
        // Handle multi-line header folding (RFC 3261 section 7.3.1)
        String[] lines = headersRaw.split("\r\n", -1);
        String prevName = null;
        StringBuilder prevValue = null;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            char c0 = line.charAt(0);
            if ((c0 == ' ' || c0 == '\t') && prevName != null) {
                prevValue.append(' ').append(line.strip());
                continue;
            }
            if (prevName != null) {
                headers.add(prevName, prevValue.toString());
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                prevName = line.substring(0, colon).strip();
                prevValue = new StringBuilder(line.substring(colon + 1).strip());
            } else {
                prevName = null;
                prevValue = null;
            }
        }
        if (prevName != null) {
            headers.add(prevName, prevValue.toString());
        }
        int s1 = requestLine.indexOf(' ');
        int s2 = requestLine.indexOf(' ', s1 + 1);
        if (s1 < 0 || s2 < 0) return null;
        SipMethod method;
        try { method = SipMethod.valueOf(requestLine.substring(0, s1)); } catch (IllegalArgumentException e) { return null; }
        return new SipRequest(method, requestLine.substring(s1 + 1, s2).trim(), SipMessage.VERSION, headers, body);
    }

    /**
     * Decodes a SIP response from bytes.
     *
     * @param data the raw bytes
     * @return the parsed response or null if incomplete
     * @since 0.1.0
     */
    SipResponse decodeResponseStream(byte[] data) {
        var combined = combineWithAccumulator(ByteBuffer.wrap(data));
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

        return parseResponseData(messageBytes);
    }

    /**
     * Returns true if there is buffered data awaiting more input.
     *
     * @return true if the internal accumulator has remaining bytes
     * @since 0.1.0
     */

    /**
     * Parses a complete SIP response from assembled bytes.
     */
    private SipResponse parseResponseData(byte[] data) {
        int headerEnd = findHeaderEnd(data);
        if (headerEnd < 0) return null;
        int contentLength = parseContentLengthFromRaw(data, headerEnd);
        int bodyStart = headerEnd + 4;
        if (data.length < bodyStart + contentLength) return null;
        byte[] body = readBody(data, contentLength);
        String raw = new String(data, 0, headerEnd, StandardCharsets.UTF_8);
        int firstCrlf = raw.indexOf("\r\n");
        String statusLine = (firstCrlf > 0) ? raw.substring(0, firstCrlf).trim() : raw.trim();
        String headersRaw = (firstCrlf > 0) ? raw.substring(firstCrlf + 2) : "";
        SipHeaders headers = new SipHeaders();
        // Handle multi-line header folding (RFC 3261 section 7.3.1)
        String[] lines = headersRaw.split("\r\n", -1);
        String prevName = null;
        StringBuilder prevValue = null;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            char c0 = line.charAt(0);
            if ((c0 == ' ' || c0 == '\t') && prevName != null) {
                prevValue.append(' ').append(line.strip());
                continue;
            }
            if (prevName != null) {
                headers.add(prevName, prevValue.toString());
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                prevName = line.substring(0, colon).strip();
                prevValue = new StringBuilder(line.substring(colon + 1).strip());
            } else {
                prevName = null;
                prevValue = null;
            }
        }
        if (prevName != null) {
            headers.add(prevName, prevValue.toString());
        }
        int s1 = statusLine.indexOf(' ');
        int s2 = statusLine.indexOf(' ', s1 + 1);
        if (s1 < 0 || s2 < 0) return null;
        int statusCode;
        try { statusCode = Integer.parseInt(statusLine.substring(s1 + 1, s2).trim()); } catch (NumberFormatException e) { return null; }
        String reason = s2 + 1 < statusLine.length() ? statusLine.substring(s2 + 1).trim() : "";
        return new SipResponse(SipMessage.VERSION, statusCode, reason, headers, body);
    }

    /**
     * Encodes any SIP message to bytes, dispatching to the correct encoder.
     */
    public static byte[] encode(SipMessage message) {
        if (message instanceof SipRequest request) return encode(request);
        if (message instanceof SipResponse response) return encode(response);
        throw new IllegalArgumentException("Unknown SipMessage type: " + message.getClass());
    }

    /**
     * Decodes raw bytes into a SIP message, auto-detecting Request vs Response.
     */
    public static SipMessage decode(byte[] data) {
        if (data.length >= 7 && data[0] == 'S' && data[1] == 'I' && data[2] == 'P') {
            return new SipCodec().decodeResponseStream(data);
        }
        return new SipCodec().decodeRequestStream(data);
    }

    /**
     * Decodes raw bytes into a SIP request.
     *
     * @param data the raw bytes
     * @return the parsed request, or null if incomplete
     */
    public static SipRequest decodeRequest(byte[] data) {
        return new SipCodec().decodeRequestStream(data);
    }

    /**
     * Decodes raw bytes into a SIP response.
     *
     * @param data the raw bytes
     * @return the parsed response, or null if incomplete
     */
    public static SipResponse decodeResponse(byte[] data) {
        return new SipCodec().decodeResponseStream(data);
    }

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
    
    // Performance enhancement: Add optimized buffer pool management
    private static ByteBuffer getBufferFromPool(int requiredSize) {
        ByteBuffer buffer = BUFFER_POOL.poll();
        if (buffer == null || buffer.capacity() < requiredSize) {
            return ByteBuffer.allocate(requiredSize);
        }
        buffer.clear();
        return buffer;
    }
    
    private static void returnBufferToPool(ByteBuffer buffer) {
        if (buffer != null && BUFFER_POOL.size() < MAX_BUFFER_POOL_SIZE) {
            BUFFER_POOL.offer(buffer);
        }
    }
}
