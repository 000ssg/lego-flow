package ssg.legoflow.media.rtsp.protocol;

import java.util.Objects;

/**
 * RTSP 2.0 response message.
 *
 * <p>Format: {@code RTSP/2.0 status reason\r\n headers\r\n\r\n [body]}
 *
 * @since 0.1.0
 */
public final class RtspResponse {

    /** RTSP protocol version. */
    public static final String VERSION = "RTSP/2.0";

    private final RtspStatus status;
    private final RtspHeaders headers;
    private final byte[] body;

    /**
     * Creates an RTSP response.
     *
     * @param status  the response status
     * @param headers the response headers
     * @param body    the response body, or empty array if none
     */
    public RtspResponse(RtspStatus status, RtspHeaders headers, byte[] body) {
        this.status = Objects.requireNonNull(status, "status");
        this.headers = Objects.requireNonNull(headers, "headers");
        this.body = body != null ? body.clone() : new byte[0];
    }

    /**
     * Creates a response without a body.
     *
     * @param status  the response status
     * @param headers the response headers
     */
    public RtspResponse(RtspStatus status, RtspHeaders headers) {
        this(status, headers, new byte[0]);
    }

    /**
     * Creates a builder for constructing responses.
     *
     * @param status the response status
     * @return a new builder
     */
    public static Builder builder(RtspStatus status) {
        return new Builder(status);
    }

    /** Returns the response status. */
    public RtspStatus status() { return status; }

    /** Returns the response headers. */
    public RtspHeaders headers() { return headers; }

    /** Returns the response body. */
    public byte[] body() { return body.clone(); }

    /** Returns true if the response has a body. */
    public boolean hasBody() { return body.length > 0; }

    /** Returns the body as a string. */
    public String bodyAsString() { return new String(body); }

    /** Returns true if this is a success response. */
    public boolean isSuccess() { return status.isSuccess(); }

    /**
     * Formats the status line.
     *
     * @return the formatted status line
     */
    public String statusLine() {
        return VERSION + " " + status.code() + " " + status.reason();
    }

    @Override
    public String toString() {
        return "RtspResponse[" + status.code() + " " + status.reason() + "]";
    }

    /**
     * Builder for RTSP responses.
     */
    public static final class Builder {
        private final RtspStatus status;
        private final RtspHeaders headers = new RtspHeaders();
        private byte[] body = new byte[0];

        Builder(RtspStatus status) {
            this.status = status;
        }

        /** Sets a header value. */
        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        /** Sets the CSeq. */
        public Builder cseq(int cseq) {
            headers.set(RtspHeaders.CSEQ, String.valueOf(cseq));
            return this;
        }

        /** Sets the Session header with timeout. */
        public Builder session(String sessionId, int timeout) {
            headers.set(RtspHeaders.SESSION, sessionId + ";timeout=" + timeout);
            return this;
        }

        /** Sets the Session header. */
        public Builder session(String sessionId) {
            headers.set(RtspHeaders.SESSION, sessionId);
            return this;
        }

        /** Sets the Transport header. */
        public Builder transport(String transport) {
            headers.set(RtspHeaders.TRANSPORT, transport);
            return this;
        }

        /** Sets the Public header with supported methods. */
        public Builder publicMethods(String methods) {
            headers.set(RtspHeaders.PUBLIC, methods);
            return this;
        }

        /** Sets the Server header. */
        public Builder server(String server) {
            headers.set(RtspHeaders.SERVER, server);
            return this;
        }

        /** Sets the body with content type. */
        public Builder body(byte[] body, String contentType) {
            this.body = body != null ? body.clone() : new byte[0];
            if (contentType != null) {
                headers.set(RtspHeaders.CONTENT_TYPE, contentType);
            }
            return this;
        }

        /** Sets the body as a string with content type. */
        public Builder body(String body, String contentType) {
            return body(body != null ? body.getBytes() : null, contentType);
        }

        /** Builds the response. */
        public RtspResponse build() {
            if (body.length > 0) {
                headers.set(RtspHeaders.CONTENT_LENGTH, String.valueOf(body.length));
            }
            return new RtspResponse(status, headers, body);
        }
    }
}
