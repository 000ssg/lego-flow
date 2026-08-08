package ssg.legoflow.wamp.core.role;

/**
 * WAMP client roles as defined in the protocol specification.
 *
 * @since 0.1.0
 */
public enum WampRole {
    /** Can call remote procedures. */
    CALLER,
    /** Can register and serve remote procedures. */
    CALLEE,
    /** Can publish events to topics. */
    PUBLISHER,
    /** Can subscribe to topics and receive events. */
    SUBSCRIBER
}
