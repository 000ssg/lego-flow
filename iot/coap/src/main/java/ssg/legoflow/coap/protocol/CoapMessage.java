package ssg.legoflow.coap.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a CoAP message as defined in RFC 7252, Section 3.
 *
 * <p>A CoAP message consists of a version, type, code, message ID, token,
 * a list of options, and an optional payload. Instances are created via
 * the {@link Builder}.
 *
 * @since 0.1.0
 */
public final class CoapMessage {

    private final CoapVersion version;
    private final CoapType type;
    private final CoapCode code;
    private final int messageId;
    private final byte[] token;
    private final List<CoapOption> options;
    private final byte[] payload;

    private CoapMessage(Builder builder) {
        this.version = builder.version;
        this.type = builder.type;
        this.code = builder.code;
        this.messageId = builder.messageId;
        this.token = builder.token != null ? builder.token.clone() : new byte[0];
        this.options = Collections.unmodifiableList(new ArrayList<>(builder.options));
        this.payload = builder.payload != null ? builder.payload.clone() : new byte[0];
    }

    /**
     * Returns the CoAP version.
     *
     * @return the version
     * @since 0.1.0
     */
    public CoapVersion version() {
        return version;
    }

    /**
     * Returns the message type.
     *
     * @return the type
     * @since 0.1.0
     */
    public CoapType type() {
        return type;
    }

    /**
     * Returns the request/response code.
     *
     * @return the code
     * @since 0.1.0
     */
    public CoapCode code() {
        return code;
    }

    /**
     * Returns the message ID (16-bit unsigned).
     *
     * @return the message ID
     * @since 0.1.0
     */
    public int messageId() {
        return messageId;
    }

    /**
     * Returns a copy of the token bytes.
     *
     * @return the token
     * @since 0.1.0
     */
    public byte[] token() {
        return token.clone();
    }

    /**
     * Returns the token length.
     *
     * @return the token length in bytes
     * @since 0.1.0
     */
    public int tokenLength() {
        return token.length;
    }

    /**
     * Returns an unmodifiable list of options.
     *
     * @return the options
     * @since 0.1.0
     */
    public List<CoapOption> options() {
        return options;
    }

    /**
     * Returns a copy of the payload bytes.
     *
     * @return the payload
     * @since 0.1.0
     */
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Returns whether this message has a non-empty payload.
     *
     * @return {@code true} if the payload is non-empty
     * @since 0.1.0
     */
    public boolean hasPayload() {
        return payload.length > 0;
    }

    /**
     * Returns the first option with the given number, or {@code null} if absent.
     *
     * @param number the option number
     * @return the first matching option, or {@code null}
     * @since 0.1.0
     */
    public CoapOption getOption(int number) {
        for (var option : options) {
            if (option.number() == number) {
                return option;
            }
        }
        return null;
    }

    /**
     * Returns all options with the given number.
     *
     * @param number the option number
     * @return a list of matching options (possibly empty)
     * @since 0.1.0
     */
    public List<CoapOption> getOptions(int number) {
        var result = new ArrayList<CoapOption>();
        for (var option : options) {
            if (option.number() == number) {
                result.add(option);
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ---- Convenience methods ----

    /**
     * Returns the full URI path assembled from Uri-Path options.
     *
     * @return the URI path (e.g. "/sensors/temperature"), or "/" if no Uri-Path options
     * @since 0.1.0
     */
    public String getUriPath() {
        var paths = getOptions(CoapOption.URI_PATH);
        if (paths.isEmpty()) {
            return "/";
        }
        var sb = new StringBuilder();
        for (var path : paths) {
            sb.append('/').append(path.asString());
        }
        return sb.toString();
    }

    /**
     * Returns the URI query assembled from Uri-Query options.
     *
     * @return the query string (e.g. "key=value&other=123"), or an empty string if absent
     * @since 0.1.0
     */
    public String getUriQuery() {
        var queries = getOptions(CoapOption.URI_QUERY);
        if (queries.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        for (int i = 0; i < queries.size(); i++) {
            if (i > 0) sb.append('&');
            sb.append(queries.get(i).asString());
        }
        return sb.toString();
    }

    /**
     * Returns the Content-Format option value, or -1 if absent.
     *
     * @return the content format identifier, or -1
     * @since 0.1.0
     */
    public int getContentFormat() {
        var option = getOption(CoapOption.CONTENT_FORMAT);
        return option != null ? option.asInt() : -1;
    }

    /**
     * Returns the ETag option value, or {@code null} if absent.
     *
     * @return the entity tag bytes, or {@code null}
     * @since 0.1.0
     */
    public byte[] getETag() {
        var option = getOption(CoapOption.ETAG);
        return option != null ? option.value() : null;
    }

    /**
     * Returns the Location-Path assembled from Location-Path options.
     *
     * @return the location path, or an empty string if absent
     * @since 0.1.0
     */
    public String getLocationPath() {
        var paths = getOptions(CoapOption.LOCATION_PATH);
        if (paths.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        for (var path : paths) {
            sb.append('/').append(path.asString());
        }
        return sb.toString();
    }

    /**
     * Returns the payload as a UTF-8 string.
     *
     * @return the payload string
     * @since 0.1.0
     */
    public String getPayloadString() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "CoapMessage{version=" + version + ", type=" + type + ", code=" + code
                + ", messageId=" + messageId + ", tokenLength=" + token.length
                + ", options=" + options.size() + ", payloadSize=" + payload.length + "}";
    }

    /**
     * Creates a new {@link Builder} for constructing CoAP messages.
     *
     * @return a new builder
     * @since 0.1.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link CoapMessage} instances.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private CoapVersion version = CoapVersion.V1;
        private CoapType type = CoapType.CONFIRMABLE;
        private CoapCode code = CoapCode.EMPTY;
        private int messageId;
        private byte[] token;
        private final List<CoapOption> options = new ArrayList<>();
        private byte[] payload;

        private Builder() {
        }

        /**
         * Sets the CoAP version.
         *
         * @param version the version
         * @return this builder
         * @since 0.1.0
         */
        public Builder version(CoapVersion version) {
            this.version = Objects.requireNonNull(version, "version must not be null");
            return this;
        }

        /**
         * Sets the message type.
         *
         * @param type the message type
         * @return this builder
         * @since 0.1.0
         */
        public Builder type(CoapType type) {
            this.type = Objects.requireNonNull(type, "type must not be null");
            return this;
        }

        /**
         * Sets the request/response code.
         *
         * @param code the code
         * @return this builder
         * @since 0.1.0
         */
        public Builder code(CoapCode code) {
            this.code = Objects.requireNonNull(code, "code must not be null");
            return this;
        }

        /**
         * Sets the message ID.
         *
         * @param messageId the 16-bit message ID
         * @return this builder
         * @since 0.1.0
         */
        public Builder messageId(int messageId) {
            this.messageId = messageId & 0xFFFF;
            return this;
        }

        /**
         * Sets the token bytes.
         *
         * @param token the token (0-8 bytes)
         * @return this builder
         * @since 0.1.0
         */
        public Builder token(byte[] token) {
            this.token = token;
            return this;
        }

        /**
         * Adds an option.
         *
         * @param option the option to add
         * @return this builder
         * @since 0.1.0
         */
        public Builder option(CoapOption option) {
            this.options.add(Objects.requireNonNull(option, "option must not be null"));
            return this;
        }

        /**
         * Adds a Uri-Path option for each segment in the given path.
         *
         * @param path the URI path (e.g. "/sensors/temperature")
         * @return this builder
         * @since 0.1.0
         */
        public Builder uriPath(String path) {
            Objects.requireNonNull(path, "path must not be null");
            var segments = path.split("/");
            for (var segment : segments) {
                if (!segment.isEmpty()) {
                    options.add(CoapOption.uriPath(segment));
                }
            }
            return this;
        }

        /**
         * Adds a Uri-Query option.
         *
         * @param query the query parameter
         * @return this builder
         * @since 0.1.0
         */
        public Builder uriQuery(String query) {
            options.add(CoapOption.uriQuery(query));
            return this;
        }

        /**
         * Adds a Content-Format option.
         *
         * @param format the content format value
         * @return this builder
         * @since 0.1.0
         */
        public Builder contentFormat(int format) {
            options.add(CoapOption.contentFormat(format));
            return this;
        }

        /**
         * Sets the payload.
         *
         * @param payload the payload bytes
         * @return this builder
         * @since 0.1.0
         */
        public Builder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Sets the payload from a UTF-8 string.
         *
         * @param payload the payload string
         * @return this builder
         * @since 0.1.0
         */
        public Builder payload(String payload) {
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        /**
         * Builds the {@link CoapMessage}.
         *
         * @return the constructed message
         * @since 0.1.0
         */
        public CoapMessage build() {
            return new CoapMessage(this);
        }
    }
}
