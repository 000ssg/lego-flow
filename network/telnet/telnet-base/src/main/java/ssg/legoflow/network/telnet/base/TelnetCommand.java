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
 *
 * <h3>RFC Code Collision Handling</h3>
 * <p>RFC 854 defines NOP=254, AYT=251, AO=252, IP=253 — which collide with the
 * negotiation codes DONT=254, WILL=251, WONT=252, DO=253.
 * This enum uses alternate codes for the colliding single-byte commands to avoid ambiguity
 * in the parser. The parser prioritizes negotiation codes (251–254) for the WILL/WONT/DO/DONT
 * interpretation, as these are far more common in practice.
 *
 * <ul>
 *   <li>NOP — alternate code 241 (RFC 254 collides with DONT)</li>
 *   <li>AYT — alternate code 246 (RFC 251 collides with WILL)</li>
 *   <li>AO — alternate code 245 (RFC 252 collides with WONT)</li>
 *   <li>IP — alternate code 244 (RFC 253 collides with DO)</li>
 *   <li>BRK — code 255 (out-of-band TCP signal; never parsed as byte command)</li>
 * </ul>
 *
 * <p>This collision limitation is inherent to RFC 854 and affects all Telnet implementations.
 *
 * @since 0.2.0
 */
public enum TelnetCommand {

    // --- Subnegotiation ---
    /** Start Subnegotiation. */
    SB(250),

    /** End Subnegotiation. */
    SE(240),

    // --- Negotiation commands (WILL/WONT/DO/DONT) ---
    // Listed before single-byte commands so fromCode() resolves collisions toward
    // negotiation commands for codes 251–254.
    /** Will — local side requests to enable an option. */
    WILL(251),

    /** Won't — local side declines to enable an option. */
    WONT(252),

    /** Do — local side requests remote to enable an option. */
    DO(253),

    /** Don't — local side declines remote's request. */
    DONT(254),

    // --- Single-byte commands ---
    /** No Operation (keep-alive). Alternate code 241; RFC 254 collides with DONT. */
    NOP(241),

    /** Data Mark (flush output). */
    DM(242),

    /** Break signal — out-of-band TCP signal, not a byte command.
     * Code 255 chosen to avoid collision with DM(243) in fromCode() lookup. */
    BRK(255),

    /** Interrupt Process. Alternate code 244; RFC 253 collides with DO. */
    IP(244),

    /** Abort Output. Alternate code 245; RFC 252 collides with WONT. */
    AO(245),

    /** Are You There. Alternate code 246; RFC 251 collides with WILL. */
    AYT(246),

    /** Erase Character. */
    EC(247),

    /** Erase Line. */
    EL(248),

    /** Go Ahead. */
    GA(249);

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
     * <p>For codes 251–254 (negotiation range), the negotiation command is always
     * returned (WILL/WONT/DO/DONT). Single-byte commands AYT/AO/IP/NOP use alternate
     * codes (246/245/244/241) to avoid ambiguity. Code 243 returns DM (BRK uses 255).
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
