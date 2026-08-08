package ssg.legoflow.media.sip.protocol;

/**
 * SIP request methods as defined in RFC 3261 and extensions.
 *
 * <p>Includes core methods from RFC 3261 (INVITE, ACK, BYE, CANCEL, REGISTER, OPTIONS)
 * and extension methods (REFER, SUBSCRIBE, NOTIFY, MESSAGE, INFO, PRACK, UPDATE).
 *
 * @since 0.1.0
 */
public enum SipMethod {

    /** Initiate a session (RFC 3261). */
    INVITE,

    /** Acknowledge final response to INVITE (RFC 3261). */
    ACK,

    /** Terminate a session (RFC 3261). */
    BYE,

    /** Cancel a pending INVITE (RFC 3261). */
    CANCEL,

    /** Register contact bindings (RFC 3261). */
    REGISTER,

    /** Query capabilities (RFC 3261). */
    OPTIONS,

    /** Transfer a call (RFC 3515). */
    REFER,

    /** Subscribe to event notifications (RFC 6665). */
    SUBSCRIBE,

    /** Deliver event notification (RFC 6665). */
    NOTIFY,

    /** Instant messaging (RFC 3428). */
    MESSAGE,

    /** Mid-session information (RFC 6086). */
    INFO,

    /** Provisional acknowledgement (RFC 3262). */
    PRACK,

    /** Update session parameters (RFC 3311). */
    UPDATE;

    /**
     * Parses a method name (case-insensitive).
     *
     * @param name the method name
     * @return the matching method
     * @throws IllegalArgumentException if the name is unknown
     */
    public static SipMethod fromName(String name) {
        for (SipMethod m : values()) {
            if (m.name().equalsIgnoreCase(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown SIP method: " + name);
    }
}
