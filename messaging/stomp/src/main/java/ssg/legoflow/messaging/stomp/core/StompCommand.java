package ssg.legoflow.messaging.stomp.core;

/**
 * All STOMP 1.2 protocol commands (client and server).
 *
 * <p>Client commands: {@link #STOMP}, {@link #CONNECT}, {@link #SEND},
 * {@link #SUBSCRIBE}, {@link #UNSUBSCRIBE}, {@link #ACK}, {@link #NACK},
 * {@link #BEGIN}, {@link #COMMIT}, {@link #ABORT}, {@link #DISCONNECT}.
 *
 * <p>Server frames: {@link #CONNECTED}, {@link #MESSAGE}, {@link #RECEIPT},
 * {@link #ERROR}.
 *
 * <p>Special: {@link #HEARTBEAT} represents the heart-beat EOL frame.
 *
 * @since 0.1.0
 */
public enum StompCommand {

    // Client commands
    /** STOMP command — alternative to CONNECT for version 1.2. */
    STOMP(true),
    /** Establish connection. */
    CONNECT(true),
    /** Send a message to a destination. */
    SEND(true),
    /** Subscribe to a destination. */
    SUBSCRIBE(true),
    /** Unsubscribe from a destination. */
    UNSUBSCRIBE(true),
    /** Acknowledge consumption of a message. */
    ACK(true),
    /** Negative acknowledge — message not consumed. */
    NACK(true),
    /** Begin a transaction. */
    BEGIN(true),
    /** Commit a transaction. */
    COMMIT(true),
    /** Abort a transaction. */
    ABORT(true),
    /** Graceful disconnect. */
    DISCONNECT(true),

    // Server frames
    /** Connection established response. */
    CONNECTED(false),
    /** Deliver a message to a subscriber. */
    MESSAGE(false),
    /** Confirm receipt of a frame. */
    RECEIPT(false),
    /** Error frame. */
    ERROR(false),

    /** Heart-beat (empty line). Not a real command per spec. */
    HEARTBEAT(false);

    private final boolean clientCommand;

    StompCommand(boolean clientCommand) {
        this.clientCommand = clientCommand;
    }

    /**
     * Returns whether this command is sent by clients (vs. server frames).
     *
     * @return {@code true} if this is a client command
     */
    public boolean isClientCommand() {
        return clientCommand;
    }

    /**
     * Parses a command string into a {@code StompCommand}.
     *
     * @param command the command string (case-insensitive)
     * @return the matching command
     * @throws IllegalArgumentException if the command is unknown
     */
    public static StompCommand fromString(String command) {
        try {
            return valueOf(command.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown STOMP command: " + command);
        }
    }
}
