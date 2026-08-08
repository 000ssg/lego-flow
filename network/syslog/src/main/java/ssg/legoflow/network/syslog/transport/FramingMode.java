package ssg.legoflow.network.syslog.transport;

/**
 * TCP framing modes for syslog transport as defined in RFC 6587.
 *
 * @since 0.1.0
 */
public enum FramingMode {

    /**
     * Octet counting: each message is prefixed with its byte length.
     * Format: {@code N<SP>message} where N is the length in bytes.
     */
    OCTET_COUNTING,

    /**
     * Non-transparent framing: each message is terminated by a line feed (LF).
     * Format: {@code message<LF>}
     */
    NON_TRANSPARENT
}
