package ssg.legoflow.media.sip.protocol;

/**
 * SIP response status codes as defined in RFC 3261.
 *
 * @since 0.1.0
 */
public enum SipStatus {

    // 1xx Provisional
    TRYING(100, "Trying"),
    RINGING(180, "Ringing"),
    CALL_IS_BEING_FORWARDED(181, "Call Is Being Forwarded"),
    QUEUED(182, "Queued"),
    SESSION_PROGRESS(183, "Session Progress"),

    // 2xx Success
    OK(200, "OK"),
    ACCEPTED(202, "Accepted"),

    // 3xx Redirection
    MULTIPLE_CHOICES(300, "Multiple Choices"),
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    MOVED_TEMPORARILY(302, "Moved Temporarily"),
    USE_PROXY(305, "Use Proxy"),
    ALTERNATIVE_SERVICE(380, "Alternative Service"),

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
    CONDITIONAL_REQUEST_FAILED(412, "Conditional Request Failed"),
    REQUEST_ENTITY_TOO_LARGE(413, "Request Entity Too Large"),
    REQUEST_URI_TOO_LONG(414, "Request-URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    UNSUPPORTED_URI_SCHEME(416, "Unsupported URI Scheme"),
    BAD_EXTENSION(420, "Bad Extension"),
    EXTENSION_REQUIRED(421, "Extension Required"),
    SESSION_INTERVAL_TOO_SMALL(422, "Session Interval Too Small"),
    INTERVAL_TOO_BRIEF(423, "Interval Too Brief"),
    TEMPORARILY_UNAVAILABLE(480, "Temporarily Unavailable"),
    CALL_TRANSACTION_DOES_NOT_EXIST(481, "Call/Transaction Does Not Exist"),
    LOOP_DETECTED(482, "Loop Detected"),
    TOO_MANY_HOPS(483, "Too Many Hops"),
    ADDRESS_INCOMPLETE(484, "Address Incomplete"),
    AMBIGUOUS(485, "Ambiguous"),
    BUSY_HERE(486, "Busy Here"),
    REQUEST_TERMINATED(487, "Request Terminated"),
    NOT_ACCEPTABLE_HERE(488, "Not Acceptable Here"),
    REQUEST_PENDING(491, "Request Pending"),
    UNDECIPHERABLE(493, "Undecipherable"),

    // 5xx Server Error
    SERVER_INTERNAL_ERROR(500, "Server Internal Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    SERVER_TIMEOUT(504, "Server Time-out"),
    VERSION_NOT_SUPPORTED(505, "Version Not Supported"),
    MESSAGE_TOO_LARGE(513, "Message Too Large"),

    // 6xx Global Failure
    BUSY_EVERYWHERE(600, "Busy Everywhere"),
    DECLINE(603, "Decline"),
    DOES_NOT_EXIST_ANYWHERE(604, "Does Not Exist Anywhere"),
    NOT_ACCEPTABLE_GLOBAL(606, "Not Acceptable");

    private final int code;
    private final String reason;

    SipStatus(int code, String reason) {
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
     * Returns true if this is a provisional response (1xx).
     *
     * @return true for provisional responses
     */
    public boolean isProvisional() {
        return code >= 100 && code < 200;
    }

    /**
     * Returns true if this is a success response (2xx).
     *
     * @return true for success codes
     */
    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    /**
     * Returns true if this is a final response (2xx-6xx).
     *
     * @return true for final responses
     */
    public boolean isFinal() {
        return code >= 200;
    }

    /**
     * Returns true if this is an error response (4xx-6xx).
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
    public static SipStatus fromCode(int code) {
        for (SipStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown SIP status code: " + code);
    }
}
