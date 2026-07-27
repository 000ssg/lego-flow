package ssg.legoflow.http.core;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class HttpMessage {

    private final HttpVersion version;
    private final HttpHeaders headers;
    private ByteBuffer body;
    private InputStream bodyStream;
    private long bodyStreamLength = -1;

    protected HttpMessage(HttpVersion version, HttpHeaders headers) {
        this.version = version;
        this.headers = headers;
    }

    public HttpVersion getVersion() {
        return version;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public ByteBuffer getBody() {
        return body;
    }

    public void setBody(ByteBuffer body) {
        this.body = body;
    }

    /**
     * Sets the response body as a stream for large content that should not
     * be buffered entirely in memory.
     *
     * @param stream the body input stream
     * @param length the content length in bytes, or -1 if unknown
     */
    public void setBodyStream(InputStream stream, long length) {
        this.bodyStream = stream;
        this.bodyStreamLength = length;
    }

    /**
     * Returns the streaming body, or null if the body is a ByteBuffer.
     *
     * @return the body input stream, or null
     */
    public InputStream getBodyStream() {
        return bodyStream;
    }

    /**
     * Returns the length of the streaming body, or -1 if unknown.
     *
     * @return the stream length in bytes
     */
    public long getBodyStreamLength() {
        return bodyStreamLength;
    }

    /**
     * Returns whether this message has a streaming body.
     *
     * @return true if the body is an InputStream
     */
    public boolean hasStreamBody() {
        return bodyStream != null;
    }

    public String getBodyAsString(Charset charset) {
        if (body == null) {
            return null;
        }
        var buf = body.duplicate();
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new String(bytes, charset);
    }

    public String getBodyAsString() {
        return getBodyAsString(StandardCharsets.UTF_8);
    }

    public int getContentLength() {
        String value = headers.get(HttpHeaders.CONTENT_LENGTH);
        if (value == null) {
            return -1;
        }
        return Integer.parseInt(value.trim());
    }
}
