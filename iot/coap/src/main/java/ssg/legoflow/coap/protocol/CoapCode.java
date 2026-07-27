package ssg.legoflow.coap.protocol;

import java.util.Objects;

/**
 * CoAP request/response codes as defined in RFC 7252, Section 12.1.
 *
 * <p>A CoAP code is encoded as {@code (class << 5) | detail} in a single byte.
 * The class (3 bits) and detail (5 bits) together form codes like 0.01 (GET),
 * 2.05 (Content), 4.04 (Not Found), etc.
 *
 * @since 1.0.0
 */
public final class CoapCode {

    private final int codeClass;
    private final int codeDetail;

    /**
     * Creates a new CoAP code from class and detail components.
     *
     * @param codeClass  the code class (0-7)
     * @param codeDetail the code detail (0-31)
     * @throws IllegalArgumentException if class or detail is out of range
     * @since 1.0.0
     */
    public CoapCode(int codeClass, int codeDetail) {
        if (codeClass < 0 || codeClass > 7) {
            throw new IllegalArgumentException("Code class must be 0-7: " + codeClass);
        }
        if (codeDetail < 0 || codeDetail > 31) {
            throw new IllegalArgumentException("Code detail must be 0-31: " + codeDetail);
        }
        this.codeClass = codeClass;
        this.codeDetail = codeDetail;
    }

    /**
     * Returns the code class (0-7).
     *
     * @return the code class
     * @since 1.0.0
     */
    public int codeClass() {
        return codeClass;
    }

    /**
     * Returns the code detail (0-31).
     *
     * @return the code detail
     * @since 1.0.0
     */
    public int codeDetail() {
        return codeDetail;
    }

    /**
     * Encodes this code as a single byte: {@code (class << 5) | detail}.
     *
     * @return the encoded byte value
     * @since 1.0.0
     */
    public int encode() {
        return (codeClass << 5) | codeDetail;
    }

    /**
     * Decodes a CoAP code from the encoded byte value.
     *
     * @param encoded the encoded code byte
     * @return the decoded {@code CoapCode}
     * @since 1.0.0
     */
    public static CoapCode decode(int encoded) {
        int cls = (encoded >> 5) & 0x07;
        int detail = encoded & 0x1F;
        return new CoapCode(cls, detail);
    }

    /**
     * Returns whether this code is a request method (class 0, detail 1-31).
     *
     * @return {@code true} if this is a method code
     * @since 1.0.0
     */
    public boolean isMethod() {
        return codeClass == 0 && codeDetail > 0;
    }

    /**
     * Returns whether this code is a success response (class 2).
     *
     * @return {@code true} if this is a success code
     * @since 1.0.0
     */
    public boolean isSuccess() {
        return codeClass == 2;
    }

    /**
     * Returns whether this code is a client error response (class 4).
     *
     * @return {@code true} if this is a client error code
     * @since 1.0.0
     */
    public boolean isClientError() {
        return codeClass == 4;
    }

    /**
     * Returns whether this code is a server error response (class 5).
     *
     * @return {@code true} if this is a server error code
     * @since 1.0.0
     */
    public boolean isServerError() {
        return codeClass == 5;
    }

    /**
     * Returns whether this is the empty code (0.00).
     *
     * @return {@code true} if class and detail are both zero
     * @since 1.0.0
     */
    public boolean isEmpty() {
        return codeClass == 0 && codeDetail == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoapCode that)) return false;
        return codeClass == that.codeClass && codeDetail == that.codeDetail;
    }

    @Override
    public int hashCode() {
        return Objects.hash(codeClass, codeDetail);
    }

    @Override
    public String toString() {
        return codeClass + "." + String.format("%02d", codeDetail);
    }

    // ---- Method codes (class 0) ----

    /** GET method (0.01). */
    public static final CoapCode GET = new CoapCode(0, 1);

    /** POST method (0.02). */
    public static final CoapCode POST = new CoapCode(0, 2);

    /** PUT method (0.03). */
    public static final CoapCode PUT = new CoapCode(0, 3);

    /** DELETE method (0.04). */
    public static final CoapCode DELETE = new CoapCode(0, 4);

    /** FETCH method (0.05). */
    public static final CoapCode FETCH = new CoapCode(0, 5);

    /** PATCH method (0.06). */
    public static final CoapCode PATCH = new CoapCode(0, 6);

    /** iPATCH method (0.07). */
    public static final CoapCode iPATCH = new CoapCode(0, 7);

    // ---- Success response codes (class 2) ----

    /** Created (2.01). */
    public static final CoapCode CREATED = new CoapCode(2, 1);

    /** Deleted (2.02). */
    public static final CoapCode DELETED = new CoapCode(2, 2);

    /** Valid (2.03). */
    public static final CoapCode VALID = new CoapCode(2, 3);

    /** Changed (2.04). */
    public static final CoapCode CHANGED = new CoapCode(2, 4);

    /** Content (2.05). */
    public static final CoapCode CONTENT = new CoapCode(2, 5);

    /** Continue (2.31). */
    public static final CoapCode CONTINUE = new CoapCode(2, 31);

    // ---- Client error response codes (class 4) ----

    /** Bad Request (4.00). */
    public static final CoapCode BAD_REQUEST = new CoapCode(4, 0);

    /** Unauthorized (4.01). */
    public static final CoapCode UNAUTHORIZED = new CoapCode(4, 1);

    /** Bad Option (4.02). */
    public static final CoapCode BAD_OPTION = new CoapCode(4, 2);

    /** Forbidden (4.03). */
    public static final CoapCode FORBIDDEN = new CoapCode(4, 3);

    /** Not Found (4.04). */
    public static final CoapCode NOT_FOUND = new CoapCode(4, 4);

    /** Method Not Allowed (4.05). */
    public static final CoapCode METHOD_NOT_ALLOWED = new CoapCode(4, 5);

    /** Not Acceptable (4.06). */
    public static final CoapCode NOT_ACCEPTABLE = new CoapCode(4, 6);

    /** Request Entity Incomplete (4.08). */
    public static final CoapCode REQUEST_ENTITY_INCOMPLETE = new CoapCode(4, 8);

    /** Conflict (4.09). */
    public static final CoapCode CONFLICT = new CoapCode(4, 9);

    /** Precondition Failed (4.12). */
    public static final CoapCode PRECONDITION_FAILED = new CoapCode(4, 12);

    /** Request Entity Too Large (4.13). */
    public static final CoapCode REQUEST_ENTITY_TOO_LARGE = new CoapCode(4, 13);

    /** Unsupported Content-Format (4.15). */
    public static final CoapCode UNSUPPORTED_CONTENT_FORMAT = new CoapCode(4, 15);

    // ---- Server error response codes (class 5) ----

    /** Internal Server Error (5.00). */
    public static final CoapCode INTERNAL_SERVER_ERROR = new CoapCode(5, 0);

    /** Not Implemented (5.01). */
    public static final CoapCode NOT_IMPLEMENTED = new CoapCode(5, 1);

    /** Bad Gateway (5.02). */
    public static final CoapCode BAD_GATEWAY = new CoapCode(5, 2);

    /** Service Unavailable (5.03). */
    public static final CoapCode SERVICE_UNAVAILABLE = new CoapCode(5, 3);

    /** Gateway Timeout (5.04). */
    public static final CoapCode GATEWAY_TIMEOUT = new CoapCode(5, 4);

    /** Proxying Not Supported (5.05). */
    public static final CoapCode PROXYING_NOT_SUPPORTED = new CoapCode(5, 5);

    /** Empty code (0.00), used in empty messages like ACK/RST. */
    public static final CoapCode EMPTY = new CoapCode(0, 0);
}
