package ssg.legoflow.coap.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A CoAP option as defined in RFC 7252, Section 5.4.
 *
 * <p>Each option is identified by a number and carries a byte-array value.
 * Static factory methods provide convenient construction for well-known options,
 * and decode helpers convert the raw value to typed representations.
 *
 * @param number the option number
 * @param value  the raw option value bytes
 * @since 1.0.0
 */
public record CoapOption(int number, byte[] value) {

    // ---- Well-known option numbers (RFC 7252 Section 5.10) ----

    /** If-Match option number. */
    public static final int IF_MATCH = 1;

    /** Uri-Host option number. */
    public static final int URI_HOST = 3;

    /** ETag option number. */
    public static final int ETAG = 4;

    /** If-None-Match option number. */
    public static final int IF_NONE_MATCH = 5;

    /** Observe option number (RFC 7641). */
    public static final int OBSERVE = 6;

    /** Uri-Port option number. */
    public static final int URI_PORT = 7;

    /** Location-Path option number. */
    public static final int LOCATION_PATH = 8;

    /** Uri-Path option number. */
    public static final int URI_PATH = 11;

    /** Content-Format option number. */
    public static final int CONTENT_FORMAT = 12;

    /** Max-Age option number. */
    public static final int MAX_AGE = 14;

    /** Uri-Query option number. */
    public static final int URI_QUERY = 15;

    /** Accept option number. */
    public static final int ACCEPT = 17;

    /** Location-Query option number. */
    public static final int LOCATION_QUERY = 20;

    /** Block2 option number (RFC 7959). */
    public static final int BLOCK2 = 23;

    /** Block1 option number (RFC 7959). */
    public static final int BLOCK1 = 27;

    /** Size2 option number. */
    public static final int SIZE2 = 28;

    /** Proxy-Uri option number. */
    public static final int PROXY_URI = 35;

    /** Proxy-Scheme option number. */
    public static final int PROXY_SCHEME = 39;

    /** Size1 option number. */
    public static final int SIZE1 = 60;

    /**
     * Compact constructor with defensive copy of the value array.
     *
     * @param number the option number; must be non-negative
     * @param value  the option value; must not be {@code null}
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code number} is negative
     */
    public CoapOption {
        if (number < 0) {
            throw new IllegalArgumentException("Option number must be non-negative: " + number);
        }
        Objects.requireNonNull(value, "value must not be null");
        value = value.clone();
    }

    /**
     * Returns a defensive copy of the option value.
     *
     * @return a copy of the value bytes
     * @since 1.0.0
     */
    @Override
    public byte[] value() {
        return value.clone();
    }

    // ---- Decode helpers ----

    /**
     * Decodes the option value as a UTF-8 string.
     *
     * @return the string value
     * @since 1.0.0
     */
    public String asString() {
        return new String(value, StandardCharsets.UTF_8);
    }

    /**
     * Decodes the option value as an unsigned integer (0-4 bytes, big-endian).
     *
     * @return the integer value
     * @since 1.0.0
     */
    public int asInt() {
        int result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    /**
     * Decodes the option value as an unsigned long (0-8 bytes, big-endian).
     *
     * @return the long value
     * @since 1.0.0
     */
    public long asLong() {
        long result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    // ---- Static factory methods ----

    /**
     * Creates a Uri-Path option from the given path segment.
     *
     * @param path the path segment (without leading slash)
     * @return the Uri-Path option
     * @since 1.0.0
     */
    public static CoapOption uriPath(String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new CoapOption(URI_PATH, path.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a Uri-Query option from the given query parameter.
     *
     * @param query the query parameter (e.g. "key=value")
     * @return the Uri-Query option
     * @since 1.0.0
     */
    public static CoapOption uriQuery(String query) {
        Objects.requireNonNull(query, "query must not be null");
        return new CoapOption(URI_QUERY, query.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a Uri-Host option from the given hostname.
     *
     * @param host the hostname
     * @return the Uri-Host option
     * @since 1.0.0
     */
    public static CoapOption uriHost(String host) {
        Objects.requireNonNull(host, "host must not be null");
        return new CoapOption(URI_HOST, host.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a Uri-Port option from the given port number.
     *
     * @param port the port number
     * @return the Uri-Port option
     * @since 1.0.0
     */
    public static CoapOption uriPort(int port) {
        return new CoapOption(URI_PORT, encodeUint(port));
    }

    /**
     * Creates a Content-Format option with the given format identifier.
     *
     * @param format the content format numeric value
     * @return the Content-Format option
     * @since 1.0.0
     */
    public static CoapOption contentFormat(int format) {
        return new CoapOption(CONTENT_FORMAT, encodeUint(format));
    }

    /**
     * Creates an ETag option with the given entity tag bytes.
     *
     * @param etag the entity tag bytes
     * @return the ETag option
     * @since 1.0.0
     */
    public static CoapOption etag(byte[] etag) {
        Objects.requireNonNull(etag, "etag must not be null");
        return new CoapOption(ETAG, etag);
    }

    /**
     * Creates a Max-Age option with the given max age in seconds.
     *
     * @param maxAge the max age value in seconds
     * @return the Max-Age option
     * @since 1.0.0
     */
    public static CoapOption maxAge(long maxAge) {
        return new CoapOption(MAX_AGE, encodeUint(maxAge));
    }

    /**
     * Creates an Accept option with the given content format identifier.
     *
     * @param format the accepted content format numeric value
     * @return the Accept option
     * @since 1.0.0
     */
    public static CoapOption accept(int format) {
        return new CoapOption(ACCEPT, encodeUint(format));
    }

    /**
     * Creates a Location-Path option from the given path segment.
     *
     * @param path the location path segment
     * @return the Location-Path option
     * @since 1.0.0
     */
    public static CoapOption locationPath(String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new CoapOption(LOCATION_PATH, path.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a Location-Query option from the given query parameter.
     *
     * @param query the location query parameter
     * @return the Location-Query option
     * @since 1.0.0
     */
    public static CoapOption locationQuery(String query) {
        Objects.requireNonNull(query, "query must not be null");
        return new CoapOption(LOCATION_QUERY, query.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a Proxy-Uri option from the given URI string.
     *
     * @param uri the proxy URI
     * @return the Proxy-Uri option
     * @since 1.0.0
     */
    public static CoapOption proxyUri(String uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        return new CoapOption(PROXY_URI, uri.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a Proxy-Scheme option from the given scheme string.
     *
     * @param scheme the proxy scheme
     * @return the Proxy-Scheme option
     * @since 1.0.0
     */
    public static CoapOption proxyScheme(String scheme) {
        Objects.requireNonNull(scheme, "scheme must not be null");
        return new CoapOption(PROXY_SCHEME, scheme.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates an If-Match option with the given entity tag bytes.
     *
     * @param etag the entity tag to match
     * @return the If-Match option
     * @since 1.0.0
     */
    public static CoapOption ifMatch(byte[] etag) {
        Objects.requireNonNull(etag, "etag must not be null");
        return new CoapOption(IF_MATCH, etag);
    }

    /**
     * Creates an If-None-Match option (empty value).
     *
     * @return the If-None-Match option
     * @since 1.0.0
     */
    public static CoapOption ifNoneMatch() {
        return new CoapOption(IF_NONE_MATCH, new byte[0]);
    }

    /**
     * Creates an Observe option with the given sequence number.
     *
     * @param sequenceNumber the observe sequence number (0 = register, 1 = deregister)
     * @return the Observe option
     * @since 1.0.0
     */
    public static CoapOption observe(int sequenceNumber) {
        return new CoapOption(OBSERVE, encodeUint(sequenceNumber));
    }

    /**
     * Creates a Size1 option with the given payload size.
     *
     * @param size the request payload size
     * @return the Size1 option
     * @since 1.0.0
     */
    public static CoapOption size1(long size) {
        return new CoapOption(SIZE1, encodeUint(size));
    }

    /**
     * Creates a Size2 option with the given payload size.
     *
     * @param size the response payload size
     * @return the Size2 option
     * @since 1.0.0
     */
    public static CoapOption size2(long size) {
        return new CoapOption(SIZE2, encodeUint(size));
    }

    /**
     * Encodes an unsigned integer as a variable-length byte array (big-endian,
     * with leading zero bytes stripped).
     *
     * @param value the unsigned value to encode
     * @return the encoded bytes
     */
    static byte[] encodeUint(long value) {
        if (value == 0) {
            return new byte[0];
        }
        int numBytes = 0;
        long temp = value;
        while (temp > 0) {
            numBytes++;
            temp >>= 8;
        }
        var bytes = new byte[numBytes];
        for (int i = numBytes - 1; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoapOption that)) return false;
        return number == that.number && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * number + Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "CoapOption{number=" + number + ", value=" + Arrays.toString(value) + "}";
    }
}
