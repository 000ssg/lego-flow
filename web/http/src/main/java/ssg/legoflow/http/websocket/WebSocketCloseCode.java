package ssg.legoflow.http.websocket;

/**
 * WebSocket close status codes per RFC 6455 §7.4.1.
 *
 * <p>Defines standard close codes (1000-1015) and recognizes the
 * registered ranges for libraries (3000-3999) and private use (4000-4999).
 *
 * @since 0.1.0
 */
public enum WebSocketCloseCode {

    /** Normal closure; the connection was fulfilled. */
    NORMAL_CLOSURE(1000, "Normal Closure"),

    /** The endpoint is going away (server shutdown or browser navigating away). */
    GOING_AWAY(1001, "Going Away"),

    /** The endpoint terminated due to a protocol error. */
    PROTOCOL_ERROR(1002, "Protocol Error"),

    /** The endpoint received data it cannot accept (e.g., text-only got binary). */
    UNSUPPORTED_DATA(1003, "Unsupported Data"),

    /** Reserved. No status code was present. */
    NO_STATUS_RECEIVED(1005, "No Status Received"),

    /** Reserved. Connection was closed abnormally (no close frame). */
    ABNORMAL_CLOSURE(1006, "Abnormal Closure"),

    /** The endpoint received data inconsistent with the message type (e.g., invalid UTF-8 in text). */
    INVALID_FRAME_PAYLOAD_DATA(1007, "Invalid Frame Payload Data"),

    /** The endpoint received a message that violates its policy. */
    POLICY_VIOLATION(1008, "Policy Violation"),

    /** The message is too big to process. */
    MESSAGE_TOO_BIG(1009, "Message Too Big"),

    /** The client expected server to negotiate extensions. */
    MANDATORY_EXTENSION(1010, "Mandatory Extension"),

    /** The server encountered an unexpected condition. */
    INTERNAL_ERROR(1011, "Internal Error"),

    /** The connection was closed due to a TLS handshake failure. */
    TLS_HANDSHAKE(1015, "TLS Handshake");

    private final int code;
    private final String reason;

    WebSocketCloseCode(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    /**
     * Returns the numeric close code.
     *
     * @return the close code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the human-readable reason phrase.
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the close code for the given numeric value.
     *
     * @param code the numeric close code
     * @return the matching WebSocketCloseCode
     * @throws IllegalArgumentException if the code is not a known standard code
     */
    public static WebSocketCloseCode fromCode(int code) {
        for (var cc : values()) {
            if (cc.code == code) {
                return cc;
            }
        }
        throw new IllegalArgumentException("Unknown WebSocket close code: " + code);
    }

    /**
     * Validates that a close code is within valid ranges per RFC 6455 §7.4.
     *
     * <p>Valid ranges:
     * <ul>
     *   <li>1000-1011: Standard codes</li>
     *   <li>3000-3999: Registered for libraries/frameworks</li>
     *   <li>4000-4999: Private use</li>
     * </ul>
     *
     * @param code the close code to validate
     * @return true if the code is valid
     */
    public static boolean isValidCode(int code) {
        return (code >= 1000 && code <= 1011 && code != 1004)
                || code == 1015
                || (code >= 3000 && code <= 4999);
    }
}
