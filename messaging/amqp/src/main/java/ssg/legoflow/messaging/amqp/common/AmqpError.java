package ssg.legoflow.messaging.amqp.common;

/**
 * Standard AMQP 1.0 error conditions as symbolic constants.
 *
 * <p>These correspond to the error conditions defined in section 2.8.1 of the
 * AMQP 1.0 specification. Each condition is represented as a symbol string
 * following the {@code amqp:} prefix convention.
 *
 * @since 0.1.0
 */
public final class AmqpError {

    private AmqpError() {}

    // -- Connection errors --

    /** An unspecified error occurred on the connection. */
    public static final String CONNECTION_FORCED = "amqp:connection:forced";

    /** A framing error occurred. */
    public static final String FRAMING_ERROR = "amqp:connection:framing-error";

    /** The connection was redirected. */
    public static final String CONNECTION_REDIRECT = "amqp:connection:redirect";

    // -- Session errors --

    /** The peer violated the session window. */
    public static final String WINDOW_VIOLATION = "amqp:session:window-violation";

    /** An attach was received for a handle already in use. */
    public static final String ERRANT_LINK = "amqp:session:errant-link";

    /** Input was received for a handle that is not attached. */
    public static final String HANDLE_IN_USE = "amqp:session:handle-in-use";

    /** An attach was received using a handle that is unattached. */
    public static final String UNATTACHED_HANDLE = "amqp:session:unattached-handle";

    // -- Link errors --

    /** A delivery was attempted when it was not possible. */
    public static final String DETACH_FORCED = "amqp:link:detach-forced";

    /** The peer sent a transfer that exceeded the link credit. */
    public static final String TRANSFER_LIMIT_EXCEEDED = "amqp:link:transfer-limit-exceeded";

    /** The peer sent a larger message than is supported. */
    public static final String MESSAGE_SIZE_EXCEEDED = "amqp:link:message-size-exceeded";

    /** The link was redirected. */
    public static final String LINK_REDIRECT = "amqp:link:redirect";

    /** A steal was attempted but the link was not stolen. */
    public static final String STOLEN = "amqp:link:stolen";

    // -- General errors --

    /** An internal error occurred. */
    public static final String INTERNAL_ERROR = "amqp:internal-error";

    /** The operation was not allowed. */
    public static final String NOT_ALLOWED = "amqp:not-allowed";

    /** The resource was not found. */
    public static final String NOT_FOUND = "amqp:not-found";

    /** Access was refused. */
    public static final String UNAUTHORIZED_ACCESS = "amqp:unauthorized-access";

    /** A decode error occurred. */
    public static final String DECODE_ERROR = "amqp:decode-error";

    /** A resource limit was exceeded. */
    public static final String RESOURCE_LIMIT_EXCEEDED = "amqp:resource-limit-exceeded";

    /** The peer tried something not implemented. */
    public static final String NOT_IMPLEMENTED = "amqp:not-implemented";

    /** A precondition failed. */
    public static final String PRECONDITION_FAILED = "amqp:precondition-failed";

    /** A resource was deleted. */
    public static final String RESOURCE_DELETED = "amqp:resource-deleted";

    /** An illegal state transition was attempted. */
    public static final String ILLEGAL_STATE = "amqp:illegal-state";

    /** The frame body was malformed. */
    public static final String FRAME_SIZE_TOO_SMALL = "amqp:frame-size-too-small";

    /** An invalid field was received. */
    public static final String INVALID_FIELD = "amqp:invalid-field";

    // -- Transaction errors --

    /** The transactional operation timed out. */
    public static final String TRANSACTION_TIMEOUT = "amqp:transaction:timeout";

    /** A rollback was forced. */
    public static final String TRANSACTION_ROLLBACK = "amqp:transaction:rollback";
}
