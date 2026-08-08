package ssg.legoflow.media.rtsp.protocol;

/**
 * RTSP 2.0 response status codes as defined in RFC 7826 section 17.
 *
 * @since 0.1.0
 */
public enum RtspStatus {

    // 1xx Informational
    CONTINUE(100, "Continue"),

    // 2xx Success
    OK(200, "OK"),

    // 3xx Redirection
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    MOVED_TEMPORARILY(302, "Moved Temporarily"),
    SEE_OTHER(303, "See Other"),
    USE_PROXY(305, "Use Proxy"),

    // 4xx Client Error
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    PAYMENT_REQUIRED(402, "Payment Required"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    GONE(410, "Gone"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    REQUEST_ENTITY_TOO_LARGE(413, "Request Message Body Too Large"),
    REQUEST_URI_TOO_LONG(414, "Request-URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    PARAMETER_NOT_UNDERSTOOD(451, "Parameter Not Understood"),
    NOT_ENOUGH_BANDWIDTH(453, "Not Enough Bandwidth"),
    SESSION_NOT_FOUND(454, "Session Not Found"),
    METHOD_NOT_VALID_IN_THIS_STATE(455, "Method Not Valid in This State"),
    HEADER_FIELD_NOT_VALID(456, "Header Field Not Valid for Resource"),
    INVALID_RANGE(457, "Invalid Range"),
    PARAMETER_IS_READ_ONLY(458, "Parameter Is Read-Only"),
    AGGREGATE_OPERATION_NOT_ALLOWED(459, "Aggregate Operation Not Allowed"),
    ONLY_AGGREGATE_OPERATION_ALLOWED(460, "Only Aggregate Operation Allowed"),
    UNSUPPORTED_TRANSPORT(461, "Unsupported Transport"),
    DESTINATION_UNREACHABLE(462, "Destination Unreachable"),

    // 5xx Server Error
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),
    RTSP_VERSION_NOT_SUPPORTED(505, "RTSP Version Not Supported"),
    OPTION_NOT_SUPPORTED(551, "Option Not Supported");

    private final int code;
    private final String reason;

    RtspStatus(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    /**
     * Returns the numeric status code.
     *
     * @return the status code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the reason phrase.
     *
     * @return the reason phrase
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns true if this is a success status (2xx).
     *
     * @return true for success codes
     */
    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    /**
     * Returns true if this is an error status (4xx or 5xx).
     *
     * @return true for error codes
     */
    public boolean isError() {
        return code >= 400;
    }

    /**
     * Finds a status by numeric code.
     *
     * @param code the status code
     * @return the matching status
     * @throws IllegalArgumentException if the code is unknown
     */
    public static RtspStatus fromCode(int code) {
        for (RtspStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown RTSP status code: " + code);
    }
}
