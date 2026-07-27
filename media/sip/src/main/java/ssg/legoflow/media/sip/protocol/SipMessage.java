package ssg.legoflow.media.sip.protocol;

import ssg.legoflow.media.sip.header.SipHeaders;

/**
 * Sealed interface representing a SIP message (request or response).
 *
 * <p>SIP messages are text-based with HTTP-like syntax, consisting of
 * a start line, headers, and an optional body. This sealed interface
 * permits only {@link SipRequest} and {@link SipResponse} as implementations.
 *
 * @since 1.0.0
 */
public sealed interface SipMessage permits SipRequest, SipResponse {

    /** SIP/2.0 version string. */
    String VERSION = "SIP/2.0";

    /**
     * Returns the message headers.
     *
     * @return the headers
     * @since 1.0.0
     */
    SipHeaders headers();

    /**
     * Returns the message body as bytes.
     *
     * @return the body, or empty array if none
     * @since 1.0.0
     */
    byte[] body();

    /**
     * Returns true if this message has a body.
     *
     * @return true if body is present
     * @since 1.0.0
     */
    default boolean hasBody() {
        return body().length > 0;
    }

    /**
     * Returns the body as a UTF-8 string.
     *
     * @return the body string
     * @since 1.0.0
     */
    default String bodyAsString() {
        return new String(body());
    }
}
