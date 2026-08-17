package ssg.legoflow.network.telnet.base;

/**
 * Telnet protocol commands (RFC 854).
 *
 * <p>All Telnet protocol commands are prefixed by the IAC (Interpret As Command,
 * octet 255) byte on the wire. The wire format for a command is:
 * {@code IAC <command> [option]}.
 *
 * <p>Single-byte commands (no option): NOP, DM, BRK, IP, AO, AYT, EC, EL, GA, SE.
 * Negotiation commands (with option byte): WILL, WONT, DO, DONT.
 * Subnegotiation delimiters: SB...SE.
 */
public enum TelnetCommand {

    // --- Subnegotiation ---
    /** Start Subnegotiation. */
    SB(250),

    /** End Subnegotiation. */
    SE(240),

    // --- Single-byte commands ---
    /** No Operation (keep-alive). */
    NOP(241),

    /** Data Mark (flush output). */
    DM(242),

    /** Break signal. */
    BRK(243),

    /** Interrupt Process. */
    IP(244),

    /** Abort Output. */
    AO(245),

    /** Are You There. */
    AYT(246),

    /** Erase Character. */
    EC(247),

    /** Erase Line. */
    EL(248),

    /** Go Ahead. */
    GA(249),

    // --- Negotiation commands (WILL/WONT/DO/DONT) ---
    /** Will — local side requests to enable an option. */
    WILL(251),

    /** Won't — local side declines to enable an option. */
    WONT(252),

    /** Do — local side requests remote to enable an option. */
    DO(253),

    /** Don't — local side declines remote's request. */
    DONT(254);

    private final int code;

    TelnetCommand(int code) {
        this.code = code;
    }

    /**
     * Get the numeric code for this command.
     */
    public int code() {
        return code;
    }

    /**
     * Check if this command carries an option byte (WILL/WONT/DO/DONT).
     */
    public boolean hasOption() {
        return this == WILL || this == WONT || this == DO || this == DONT;
    }

    /**
     * Lookup a command by numeric code.
     *
     * @param code the octet value (0–255)
     * @return the command, or null if unknown
     */
    public static TelnetCommand fromCode(int code) {
        for (TelnetCommand cmd : values()) {
            if (cmd.code == code) return cmd;
        }
        return null;
    }
}
