package ssg.legoflow.messaging.stomp.core;

import java.nio.charset.StandardCharsets;

/**
 * Immutable STOMP frame consisting of a command, headers, and optional body.
 *
 * <p>STOMP frame wire format:
 * <pre>
 * COMMAND\n
 * header1:value1\n
 * header2:value2\n
 * \n
 * body\0
 * </pre>
 *
 * @param command the STOMP command
 * @param headers the frame headers
 * @param body    the frame body (may be empty, never null)
 * @since 1.0.0
 */
public record StompFrame(StompCommand command, StompHeaders headers, byte[] body) {

    /**
     * Creates a frame with validated non-null fields.
     *
     * @param command the STOMP command
     * @param headers the frame headers
     * @param body    the frame body
     */
    public StompFrame {
        if (command == null) throw new IllegalArgumentException("Command must not be null");
        if (headers == null) headers = new StompHeaders();
        if (body == null) body = new byte[0];
    }

    /**
     * Creates a frame with no body.
     *
     * @param command the STOMP command
     * @param headers the frame headers
     */
    public StompFrame(StompCommand command, StompHeaders headers) {
        this(command, headers, new byte[0]);
    }

    /**
     * Creates a frame with no headers and no body.
     *
     * @param command the STOMP command
     */
    public StompFrame(StompCommand command) {
        this(command, new StompHeaders(), new byte[0]);
    }

    /**
     * Creates a frame with a text body (UTF-8 encoded).
     *
     * @param command the STOMP command
     * @param headers the frame headers
     * @param text    the body text
     * @return a new frame
     */
    public static StompFrame withText(StompCommand command, StompHeaders headers, String text) {
        return new StompFrame(command, headers, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the body as a UTF-8 string.
     *
     * @return the body text
     */
    public String bodyAsText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Returns whether this frame has a non-empty body.
     *
     * @return {@code true} if body length is greater than zero
     */
    public boolean hasBody() {
        return body.length > 0;
    }

    /**
     * Returns the value of the given header, or {@code null}.
     *
     * @param name the header name
     * @return the header value
     */
    public String header(String name) {
        return headers.get(name);
    }

    /**
     * Creates a heart-beat frame (empty line).
     *
     * @return a heart-beat frame
     */
    public static StompFrame heartbeat() {
        return new StompFrame(StompCommand.HEARTBEAT);
    }

    /**
     * Returns whether this frame is a heart-beat.
     *
     * @return {@code true} if this is a heart-beat frame
     */
    public boolean isHeartbeat() {
        return command == StompCommand.HEARTBEAT;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("StompFrame[").append(command);
        if (!headers.isEmpty()) {
            sb.append(", headers=").append(headers);
        }
        if (body.length > 0) {
            sb.append(", body=").append(body.length).append(" bytes");
        }
        sb.append(']');
        return sb.toString();
    }
}
