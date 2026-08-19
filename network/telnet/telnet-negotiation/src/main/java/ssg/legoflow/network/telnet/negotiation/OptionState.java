package ssg.legoflow.network.telnet.negotiation;

/**
 * State of a Telnet option for one side of the connection (RFC 855).
 *
 * <p>Each option has four states per side:
 * <ul>
 *   <li>OFF — disabled</li>
 *   <li>OFF_DEF — disabled, awaiting further negotiation</li>
 *   <li>ON_DEF — enabled, awaiting confirmation</li>
 *   <li>ON — enabled and confirmed</li>
 * </ul>
 */
public enum OptionState {
    /** Option is disabled. */
    OFF,

    /** Option is disabled, but we sent a request to change it. */
    OFF_DEF,

    /** Option is enabled provisionally, awaiting peer response. */
    ON_DEF,

    /** Option is enabled and confirmed by peer. */
    ON;
}
