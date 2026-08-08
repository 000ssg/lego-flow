package ssg.legoflow.media.sip.protocol;

import ssg.legoflow.media.sip.header.SipHeaders;

import java.util.Objects;

/**
 * SIP request message per RFC 3261.
 *
 * <p>Format: {@code METHOD request-uri SIP/2.0\r\n headers\r\n\r\n [body]}
 *
 * @param method     the request method
 * @param requestUri the request URI string
 * @param version    the SIP version (always "SIP/2.0")
 * @param headers    the request headers
 * @param body       the request body, or empty array
 * @since 0.1.0
 */
public record SipRequest(
        SipMethod method,
        String requestUri,
        String version,
        SipHeaders headers,
        byte[] body
) implements SipMessage {

    /**
     * Creates a SIP request.
     *
     * @since 0.1.0
     */
    public SipRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(requestUri, "requestUri");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(headers, "headers");
        body = body != null ? body.clone() : new byte[0];
    }

    /**
     * Creates a SIP request with default version and no body.
     *
     * @param method     the request method
     * @param requestUri the request URI
     * @param headers    the request headers
     * @since 0.1.0
     */
    public SipRequest(SipMethod method, String requestUri, SipHeaders headers) {
        this(method, requestUri, SipMessage.VERSION, headers, new byte[0]);
    }

    /**
     * Returns a defensive copy of the body.
     *
     * @return the body bytes
     * @since 0.1.0
     */
    @Override
    public byte[] body() {
        return body.clone();
    }

    /**
     * Formats the request line.
     *
     * @return the formatted request line
     * @since 0.1.0
     */
    public String requestLine() {
        return method.name() + " " + requestUri + " " + version;
    }

    /**
     * Creates a builder for constructing requests.
     *
     * @param method     the request method
     * @param requestUri the request URI
     * @return a new builder
     * @since 0.1.0
     */
    public static Builder builder(SipMethod method, String requestUri) {
        return new Builder(method, requestUri);
    }

    @Override
    public String toString() {
        return "SipRequest[" + method + " " + requestUri + "]";
    }

    /**
     * Builder for SIP requests.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private final SipMethod method;
        private final String requestUri;
        private final SipHeaders headers = new SipHeaders();
        private byte[] body = new byte[0];

        Builder(SipMethod method, String requestUri) {
            this.method = method;
            this.requestUri = requestUri;
        }

        /**
         * Sets a header value.
         *
         * @param name  the header name
         * @param value the header value
         * @return this builder
         * @since 0.1.0
         */
        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        /**
         * Adds a header value (supports multiple values).
         *
         * @param name  the header name
         * @param value the header value
         * @return this builder
         * @since 0.1.0
         */
        public Builder addHeader(String name, String value) {
            headers.add(name, value);
            return this;
        }

        /**
         * Sets the Via header.
         *
         * @param via the Via header value
         * @return this builder
         * @since 0.1.0
         */
        public Builder via(String via) {
            headers.add(SipHeaders.VIA, via);
            return this;
        }

        /**
         * Sets the From header.
         *
         * @param from the From header value
         * @return this builder
         * @since 0.1.0
         */
        public Builder from(String from) {
            headers.set(SipHeaders.FROM, from);
            return this;
        }

        /**
         * Sets the To header.
         *
         * @param to the To header value
         * @return this builder
         * @since 0.1.0
         */
        public Builder to(String to) {
            headers.set(SipHeaders.TO, to);
            return this;
        }

        /**
         * Sets the Call-ID header.
         *
         * @param callId the Call-ID value
         * @return this builder
         * @since 0.1.0
         */
        public Builder callId(String callId) {
            headers.set(SipHeaders.CALL_ID, callId);
            return this;
        }

        /**
         * Sets the CSeq header.
         *
         * @param seq    the sequence number
         * @param method the method name
         * @return this builder
         * @since 0.1.0
         */
        public Builder cseq(long seq, SipMethod method) {
            headers.set(SipHeaders.CSEQ, seq + " " + method.name());
            return this;
        }

        /**
         * Sets the Max-Forwards header.
         *
         * @param maxForwards the max forwards value
         * @return this builder
         * @since 0.1.0
         */
        public Builder maxForwards(int maxForwards) {
            headers.set(SipHeaders.MAX_FORWARDS, String.valueOf(maxForwards));
            return this;
        }

        /**
         * Sets the Contact header.
         *
         * @param contact the Contact value
         * @return this builder
         * @since 0.1.0
         */
        public Builder contact(String contact) {
            headers.set(SipHeaders.CONTACT, contact);
            return this;
        }

        /**
         * Sets the User-Agent header.
         *
         * @param userAgent the User-Agent value
         * @return this builder
         * @since 0.1.0
         */
        public Builder userAgent(String userAgent) {
            headers.set(SipHeaders.USER_AGENT, userAgent);
            return this;
        }

        /**
         * Sets the Expires header.
         *
         * @param seconds the expiration in seconds
         * @return this builder
         * @since 0.1.0
         */
        public Builder expires(int seconds) {
            headers.set(SipHeaders.EXPIRES, String.valueOf(seconds));
            return this;
        }

        /**
         * Sets the body.
         *
         * @param body the body bytes
         * @return this builder
         * @since 0.1.0
         */
        public Builder body(byte[] body) {
            this.body = body != null ? body.clone() : new byte[0];
            return this;
        }

        /**
         * Sets the body as a string with content type.
         *
         * @param body        the body string
         * @param contentType the content type
         * @return this builder
         * @since 0.1.0
         */
        public Builder body(String body, String contentType) {
            this.body = body != null ? body.getBytes() : new byte[0];
            if (contentType != null) {
                headers.set(SipHeaders.CONTENT_TYPE, contentType);
            }
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the built SIP request
         * @since 0.1.0
         */
        public SipRequest build() {
            if (body.length > 0) {
                headers.set(SipHeaders.CONTENT_LENGTH, String.valueOf(body.length));
            } else {
                headers.set(SipHeaders.CONTENT_LENGTH, "0");
            }
            return new SipRequest(method, requestUri, SipMessage.VERSION, headers, body);
        }
    }
}
