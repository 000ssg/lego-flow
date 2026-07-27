package ssg.legoflow.media.rtsp.protocol;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * RTSP 2.0 request message.
 *
 * <p>Format: {@code METHOD rtsp://host/path RTSP/2.0\r\n headers\r\n\r\n [body]}
 *
 * @since 1.0.0
 */
public final class RtspRequest {

    /** RTSP protocol version. */
    public static final String VERSION = "RTSP/2.0";

    private final RtspMethod method;
    private final URI uri;
    private final RtspHeaders headers;
    private final byte[] body;

    /**
     * Creates an RTSP request.
     *
     * @param method  the request method
     * @param uri     the request URI
     * @param headers the request headers
     * @param body    the request body, or empty array if none
     */
    public RtspRequest(RtspMethod method, URI uri, RtspHeaders headers, byte[] body) {
        this.method = Objects.requireNonNull(method, "method");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.headers = Objects.requireNonNull(headers, "headers");
        this.body = body != null ? body.clone() : new byte[0];
    }

    /**
     * Creates a request without a body.
     *
     * @param method  the request method
     * @param uri     the request URI
     * @param headers the request headers
     */
    public RtspRequest(RtspMethod method, URI uri, RtspHeaders headers) {
        this(method, uri, headers, new byte[0]);
    }

    /**
     * Creates a builder for constructing requests.
     *
     * @param method the request method
     * @param uri    the request URI
     * @return a new builder
     */
    public static Builder builder(RtspMethod method, URI uri) {
        return new Builder(method, uri);
    }

    /**
     * Creates a builder for constructing requests with a string URI.
     *
     * @param method the request method
     * @param uri    the request URI string
     * @return a new builder
     */
    public static Builder builder(RtspMethod method, String uri) {
        return new Builder(method, URI.create(uri));
    }

    /** Returns the request method. */
    public RtspMethod method() { return method; }

    /** Returns the request URI. */
    public URI uri() { return uri; }

    /** Returns the request headers. */
    public RtspHeaders headers() { return headers; }

    /** Returns the request body. */
    public byte[] body() { return body.clone(); }

    /** Returns true if the request has a body. */
    public boolean hasBody() { return body.length > 0; }

    /** Returns the body as a string. */
    public String bodyAsString() { return new String(body); }

    /**
     * Formats the request line.
     *
     * @return the formatted request line
     */
    public String requestLine() {
        return method.name() + " " + uri + " " + VERSION;
    }

    @Override
    public String toString() {
        return "RtspRequest[" + method + " " + uri + "]";
    }

    /**
     * Builder for RTSP requests.
     */
    public static final class Builder {
        private final RtspMethod method;
        private final URI uri;
        private final RtspHeaders headers = new RtspHeaders();
        private byte[] body = new byte[0];

        Builder(RtspMethod method, URI uri) {
            this.method = method;
            this.uri = uri;
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

        /** Sets the Session header. */
        public Builder session(String sessionId) {
            headers.set(RtspHeaders.SESSION, sessionId);
            return this;
        }

        /** Sets the Accept header. */
        public Builder accept(String mediaType) {
            headers.set(RtspHeaders.ACCEPT, mediaType);
            return this;
        }

        /** Sets the Transport header. */
        public Builder transport(String transport) {
            headers.set(RtspHeaders.TRANSPORT, transport);
            return this;
        }

        /** Sets the Range header. */
        public Builder range(String range) {
            headers.set(RtspHeaders.RANGE, range);
            return this;
        }

        /** Sets the User-Agent header. */
        public Builder userAgent(String userAgent) {
            headers.set(RtspHeaders.USER_AGENT, userAgent);
            return this;
        }

        /** Sets the body. */
        public Builder body(byte[] body) {
            this.body = body != null ? body.clone() : new byte[0];
            return this;
        }

        /** Sets the body as a string. */
        public Builder body(String body) {
            this.body = body != null ? body.getBytes() : new byte[0];
            return this;
        }

        /** Builds the request. */
        public RtspRequest build() {
            if (body.length > 0) {
                headers.set(RtspHeaders.CONTENT_LENGTH, String.valueOf(body.length));
            }
            return new RtspRequest(method, uri, headers, body);
        }
    }
}
