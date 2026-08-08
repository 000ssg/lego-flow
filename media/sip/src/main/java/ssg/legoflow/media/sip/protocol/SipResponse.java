package ssg.legoflow.media.sip.protocol;

import ssg.legoflow.media.sip.header.SipHeaders;

import java.util.Objects;

/**
 * SIP response message per RFC 3261.
 *
 * <p>Format: {@code SIP/2.0 status-code reason-phrase\r\n headers\r\n\r\n [body]}
 *
 * @param version      the SIP version (always "SIP/2.0")
 * @param statusCode   the numeric status code
 * @param reasonPhrase the reason phrase
 * @param headers      the response headers
 * @param body         the response body, or empty array
 * @since 0.1.0
 */
public record SipResponse(
        String version,
        int statusCode,
        String reasonPhrase,
        SipHeaders headers,
        byte[] body
) implements SipMessage {

    /**
     * Creates a SIP response.
     *
     * @since 0.1.0
     */
    public SipResponse {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(reasonPhrase, "reasonPhrase");
        Objects.requireNonNull(headers, "headers");
        body = body != null ? body.clone() : new byte[0];
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
     * Returns the SIP status enum for this response.
     *
     * @return the SIP status
     * @since 0.1.0
     */
    public SipStatus status() {
        return SipStatus.fromCode(statusCode);
    }

    /**
     * Returns true if this is a provisional response (1xx).
     *
     * @return true for provisional responses
     * @since 0.1.0
     */
    public boolean isProvisional() {
        return statusCode >= 100 && statusCode < 200;
    }

    /**
     * Returns true if this is a success response (2xx).
     *
     * @return true for success responses
     * @since 0.1.0
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Returns true if this is a final response (>= 200).
     *
     * @return true for final responses
     * @since 0.1.0
     */
    public boolean isFinal() {
        return statusCode >= 200;
    }

    /**
     * Formats the status line.
     *
     * @return the formatted status line
     * @since 0.1.0
     */
    public String statusLine() {
        return version + " " + statusCode + " " + reasonPhrase;
    }

    /**
     * Creates a builder for constructing responses.
     *
     * @param status the response status
     * @return a new builder
     * @since 0.1.0
     */
    public static Builder builder(SipStatus status) {
        return new Builder(status);
    }

    /**
     * Creates a builder for constructing responses with a custom status code and reason.
     *
     * @param statusCode   the status code
     * @param reasonPhrase the reason phrase
     * @return a new builder
     * @since 0.1.0
     */
    public static Builder builder(int statusCode, String reasonPhrase) {
        return new Builder(statusCode, reasonPhrase);
    }

    @Override
    public String toString() {
        return "SipResponse[" + statusCode + " " + reasonPhrase + "]";
    }

    /**
     * Builder for SIP responses.
     *
     * @since 0.1.0
     */
    public static final class Builder {
        private final int statusCode;
        private final String reasonPhrase;
        private final SipHeaders headers = new SipHeaders();
        private byte[] body = new byte[0];

        Builder(SipStatus status) {
            this.statusCode = status.code();
            this.reasonPhrase = status.reason();
        }

        Builder(int statusCode, String reasonPhrase) {
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
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
         * @param method the method
         * @return this builder
         * @since 0.1.0
         */
        public Builder cseq(long seq, SipMethod method) {
            headers.set(SipHeaders.CSEQ, seq + " " + method.name());
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
         * Sets the Server header.
         *
         * @param server the Server value
         * @return this builder
         * @since 0.1.0
         */
        public Builder server(String server) {
            headers.set(SipHeaders.SERVER, server);
            return this;
        }

        /**
         * Sets the Allow header.
         *
         * @param methods the allowed methods
         * @return this builder
         * @since 0.1.0
         */
        public Builder allow(String methods) {
            headers.set(SipHeaders.ALLOW, methods);
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
         * Copies headers from a request to form a matching response.
         *
         * @param request the original request
         * @return this builder
         * @since 0.1.0
         */
        public Builder fromRequest(SipRequest request) {
            // Copy mandatory headers from request
            request.headers().all(SipHeaders.VIA).forEach(v -> headers.add(SipHeaders.VIA, v));
            request.headers().first(SipHeaders.FROM).ifPresent(v -> headers.set(SipHeaders.FROM, v));
            request.headers().first(SipHeaders.TO).ifPresent(v -> headers.set(SipHeaders.TO, v));
            request.headers().first(SipHeaders.CALL_ID).ifPresent(v -> headers.set(SipHeaders.CALL_ID, v));
            request.headers().first(SipHeaders.CSEQ).ifPresent(v -> headers.set(SipHeaders.CSEQ, v));
            return this;
        }

        /**
         * Sets the body with content type.
         *
         * @param body        the body bytes
         * @param contentType the content type
         * @return this builder
         * @since 0.1.0
         */
        public Builder body(byte[] body, String contentType) {
            this.body = body != null ? body.clone() : new byte[0];
            if (contentType != null) {
                headers.set(SipHeaders.CONTENT_TYPE, contentType);
            }
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
            return body(body != null ? body.getBytes() : null, contentType);
        }

        /**
         * Builds the response.
         *
         * @return the built SIP response
         * @since 0.1.0
         */
        public SipResponse build() {
            if (body.length > 0) {
                headers.set(SipHeaders.CONTENT_LENGTH, String.valueOf(body.length));
            } else {
                headers.set(SipHeaders.CONTENT_LENGTH, "0");
            }
            return new SipResponse(SipMessage.VERSION, statusCode, reasonPhrase, headers, body);
        }
    }
}
