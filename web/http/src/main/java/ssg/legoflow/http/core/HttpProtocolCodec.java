package ssg.legoflow.http.core;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class HttpProtocolCodec {

    private ByteBuffer accumulator;

    public ByteBuffer serializeRequest(HttpRequest request) {
        var sb = new StringBuilder();
        sb.append(request.getMethod().name())
          .append(HttpConstants.SP)
          .append(request.getUri())
          .append(HttpConstants.SP)
          .append(request.getVersion().value())
          .append(HttpConstants.CRLF);
        appendHeaders(sb, request.getHeaders());
        sb.append(HttpConstants.CRLF);
        return appendBody(sb, request.getBody());
    }

    public ByteBuffer serializeResponse(HttpResponse response) {
        var sb = new StringBuilder();
        sb.append(response.getVersion().value())
          .append(HttpConstants.SP)
          .append(response.getStatus().code())
          .append(HttpConstants.SP)
          .append(response.getStatus().reason())
          .append(HttpConstants.CRLF);
        appendHeaders(sb, response.getHeaders());
        sb.append(HttpConstants.CRLF);
        return appendBody(sb, response.getBody());
    }

    /**
     * Serializes only the response status line and headers (no body).
     *
     * <p>Used when the body will be streamed separately to the output stream.
     *
     * @param response the HTTP response
     * @return the serialized headers as bytes
     */
    public byte[] serializeResponseHeaders(HttpResponse response) {
        var sb = new StringBuilder();
        sb.append(response.getVersion().value())
          .append(HttpConstants.SP)
          .append(response.getStatus().code())
          .append(HttpConstants.SP)
          .append(response.getStatus().reason())
          .append(HttpConstants.CRLF);
        appendHeaders(sb, response.getHeaders());
        sb.append(HttpConstants.CRLF);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public HttpRequest parseRequest(ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        int headerEnd = findHeaderEnd(bytes);
        if (headerEnd < 0) {
            headerEnd = bytes.length; // backward compat: treat entire input as header section
        }
        String headerSection = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        String[] lines = headerSection.split(HttpConstants.CRLF, 2);

        String[] requestLine = lines[0].split(HttpConstants.SP, 3);
        HttpMethod method = HttpMethod.valueOf(requestLine[0]);
        String uri = requestLine[1];
        HttpVersion version = HttpVersion.parse(requestLine[2]);

        HttpHeaders headers = (lines.length > 1) ? parseHeaders(lines[1]) : new HttpHeaders();

        HttpRequest request = new HttpRequest(method, uri, version, headers);

        int bodyStart = headerEnd + 4; // skip CRLFCRLF
        if (bodyStart < bytes.length) {
            request.setBody(ByteBuffer.wrap(Arrays.copyOfRange(bytes, bodyStart, bytes.length)));
        }

        return request;
    }

    public HttpResponse parseResponse(ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        int headerEnd = findHeaderEnd(bytes);
        if (headerEnd < 0) {
            headerEnd = bytes.length; // backward compat: treat entire input as header section
        }
        String headerSection = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        String[] lines = headerSection.split(HttpConstants.CRLF, 2);

        String[] statusLine = lines[0].split(HttpConstants.SP, 3);
        HttpVersion version = HttpVersion.parse(statusLine[0]);
        int code = Integer.parseInt(statusLine[1]);
        HttpStatus status = HttpStatus.fromCode(code);

        HttpHeaders headers = (lines.length > 1) ? parseHeaders(lines[1]) : new HttpHeaders();

        HttpResponse response = new HttpResponse(status, version, headers);

        int bodyStart = headerEnd + 4; // skip CRLFCRLF
        if (bodyStart < bytes.length) {
            response.setBody(ByteBuffer.wrap(Arrays.copyOfRange(bytes, bodyStart, bytes.length)));
        }

        return response;
    }

    /**
     * Stream-oriented request parser. Accumulates data across calls and returns a
     * complete HttpRequest only when headers and body (per Content-Length) are fully received.
     *
     * @param data the next chunk of incoming bytes
     * @return the parsed request, or null if more data is needed
     * @since 0.1.0
     */
    public HttpRequest parseRequestStreaming(ByteBuffer data) {
        var combined = combineWithAccumulator(data);
        byte[] bytes = new byte[combined.remaining()];
        combined.duplicate().get(bytes);

        int headerEnd = findHeaderEnd(bytes);
        if (headerEnd < 0) {
            // Headers not yet complete — save everything to accumulator
            accumulator = ByteBuffer.allocate(bytes.length);
            accumulator.put(bytes);
            accumulator.flip();
            return null;
        }

        String headerSection = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        String[] lines = headerSection.split(HttpConstants.CRLF, 2);

        String[] requestLine = lines[0].split(HttpConstants.SP, 3);
        HttpMethod method = HttpMethod.valueOf(requestLine[0]);
        String uri = requestLine[1];
        HttpVersion version = HttpVersion.parse(requestLine[2]);

        HttpHeaders headers = (lines.length > 1) ? parseHeaders(lines[1]) : new HttpHeaders();

        int bodyStart = headerEnd + 4; // skip CRLFCRLF
        int contentLength = getContentLength(headers);

        if (contentLength > 0) {
            int availableBody = bytes.length - bodyStart;
            if (availableBody < contentLength) {
                // Body not yet complete — save everything to accumulator
                accumulator = ByteBuffer.allocate(bytes.length);
                accumulator.put(bytes);
                accumulator.flip();
                return null;
            }
        }

        HttpRequest request = new HttpRequest(method, uri, version, headers);

        if (contentLength > 0) {
            request.setBody(ByteBuffer.wrap(Arrays.copyOfRange(bytes, bodyStart, bodyStart + contentLength)));
            // Save any remainder beyond this request
            int consumed = bodyStart + contentLength;
            if (consumed < bytes.length) {
                accumulator = ByteBuffer.allocate(bytes.length - consumed);
                accumulator.put(bytes, consumed, bytes.length - consumed);
                accumulator.flip();
            } else {
                accumulator = null;
            }
        } else if (bodyStart < bytes.length) {
            // No Content-Length but there is data after headers — for methods like POST without CL
            // In streaming mode without Content-Length, return what we have
            request.setBody(ByteBuffer.wrap(Arrays.copyOfRange(bytes, bodyStart, bytes.length)));
            accumulator = null;
        } else {
            accumulator = null;
        }

        return request;
    }

    /**
     * Stream-oriented response parser. Accumulates data across calls and returns a
     * complete HttpResponse only when headers and body (per Content-Length) are fully received.
     *
     * @param data the next chunk of incoming bytes
     * @return the parsed response, or null if more data is needed
     * @since 0.1.0
     */
    public HttpResponse parseResponseStreaming(ByteBuffer data) {
        var combined = combineWithAccumulator(data);
        byte[] bytes = new byte[combined.remaining()];
        combined.duplicate().get(bytes);

        int headerEnd = findHeaderEnd(bytes);
        if (headerEnd < 0) {
            // Headers not yet complete — save everything to accumulator
            accumulator = ByteBuffer.allocate(bytes.length);
            accumulator.put(bytes);
            accumulator.flip();
            return null;
        }

        String headerSection = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        String[] lines = headerSection.split(HttpConstants.CRLF, 2);

        String[] statusLine = lines[0].split(HttpConstants.SP, 3);
        HttpVersion version = HttpVersion.parse(statusLine[0]);
        int code = Integer.parseInt(statusLine[1]);
        HttpStatus status = HttpStatus.fromCode(code);

        HttpHeaders headers = (lines.length > 1) ? parseHeaders(lines[1]) : new HttpHeaders();

        int bodyStart = headerEnd + 4; // skip CRLFCRLF
        int contentLength = getContentLength(headers);

        if (contentLength > 0) {
            int availableBody = bytes.length - bodyStart;
            if (availableBody < contentLength) {
                // Body not yet complete — save everything to accumulator
                accumulator = ByteBuffer.allocate(bytes.length);
                accumulator.put(bytes);
                accumulator.flip();
                return null;
            }
        }

        HttpResponse response = new HttpResponse(status, version, headers);

        if (contentLength > 0) {
            response.setBody(ByteBuffer.wrap(Arrays.copyOfRange(bytes, bodyStart, bodyStart + contentLength)));
            // Save any remainder beyond this response
            int consumed = bodyStart + contentLength;
            if (consumed < bytes.length) {
                accumulator = ByteBuffer.allocate(bytes.length - consumed);
                accumulator.put(bytes, consumed, bytes.length - consumed);
                accumulator.flip();
            } else {
                accumulator = null;
            }
        } else if (bodyStart < bytes.length) {
            response.setBody(ByteBuffer.wrap(Arrays.copyOfRange(bytes, bodyStart, bytes.length)));
            accumulator = null;
        } else {
            accumulator = null;
        }

        return response;
    }

    public HttpHeaders parseHeaders(String rawHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (rawHeaders == null || rawHeaders.isEmpty()) {
            return headers;
        }
        for (String line : rawHeaders.split(HttpConstants.CRLF)) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                headers.add(name, value);
            }
        }
        return headers;
    }

    /**
     * Returns whether this codec has buffered partial data from a previous streaming parse call.
     *
     * @return true if there is buffered data awaiting more input
     * @since 0.1.0
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    private void appendHeaders(StringBuilder sb, HttpHeaders headers) {
        for (String name : headers.names()) {
            for (String value : headers.getAll(name)) {
                sb.append(name).append(": ").append(value).append(HttpConstants.CRLF);
            }
        }
    }

    private ByteBuffer appendBody(StringBuilder sb, ByteBuffer body) {
        byte[] headerBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        if (body == null || body.remaining() == 0) {
            return ByteBuffer.wrap(headerBytes);
        }
        var bodyDup = body.duplicate();
        var bodyBytes = new byte[bodyDup.remaining()];
        bodyDup.get(bodyBytes);
        var out = new ByteArrayOutputStream(headerBytes.length + bodyBytes.length);
        out.writeBytes(headerBytes);
        out.writeBytes(bodyBytes);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Finds the end of the HTTP header section (the position of the first \r in \r\n\r\n).
     *
     * @param data the raw HTTP message bytes
     * @return the index of the header end, or -1 if \r\n\r\n is not found
     */
    int findHeaderEnd(byte[] data) {
        byte cr = '\r';
        byte lf = '\n';
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == cr && data[i + 1] == lf && data[i + 2] == cr && data[i + 3] == lf) {
                return i;
            }
        }
        return -1;
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

    private static int getContentLength(HttpHeaders headers) {
        String cl = headers.get("Content-Length");
        if (cl == null) cl = headers.get("content-length");
        if (cl != null) {
            try {
                return Integer.parseInt(cl.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return 0;
    }
}
