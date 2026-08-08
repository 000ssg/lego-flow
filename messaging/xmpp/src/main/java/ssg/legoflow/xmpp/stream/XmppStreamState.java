package ssg.legoflow.xmpp.stream;

/**
 * States of an XMPP XML stream lifecycle.
 *
 * @since 0.1.0
 */
public enum XmppStreamState {

    /** Stream has not been initiated. */
    INITIAL,

    /** TCP connection is being established. */
    CONNECTING,

    /** Stream features are being negotiated (TLS, SASL, etc.). */
    NEGOTIATING,

    /** SASL authentication has completed successfully. */
    AUTHENTICATED,

    /** Resource binding has completed. */
    BOUND,

    /** Stream is fully active and ready for stanza exchange. */
    ACTIVE,

    /** Stream is in the process of closing. */
    CLOSING,

    /** Stream has been closed. */
    CLOSED;

    /**
     * Returns whether a transition to the given state is valid.
     *
     * @param target the target state
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(XmppStreamState target) {
        return switch (this) {
            case INITIAL -> target == CONNECTING;
            case CONNECTING -> target == NEGOTIATING || target == CLOSED;
            case NEGOTIATING -> target == AUTHENTICATED || target == CLOSING || target == CLOSED;
            case AUTHENTICATED -> target == BOUND || target == CLOSING || target == CLOSED;
            case BOUND -> target == ACTIVE || target == CLOSING || target == CLOSED;
            case ACTIVE -> target == CLOSING || target == CLOSED;
            case CLOSING -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
