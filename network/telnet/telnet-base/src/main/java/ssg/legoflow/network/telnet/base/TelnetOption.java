package ssg.legoflow.network.telnet.base;

/**
 * Standard Telnet options (RFC 855 and related).
 *
 * <p>Option codes are single octets used in negotiation commands
 * (WILL/WONT/DO/DONT) and subnegotiations (SB...SE).
 */
public enum TelnetOption {

    /** Binary Transmission (RFC 856). */
    BINARY(0),

    /** Echo (RFC 857). */
    ECHO(1),

    /** Reconfigure (experimental). */
    RCP(2),

    /** Suppress Go Ahead (RFC 858). */
    SUPPRESS_GO_AHEAD(3),

    /** Appointment (experimental). */
    APPEX(4),

    /** Status. */
    STATUS(5),

    /** Timing Mark Output. */
    TMO(6),

    /** Byte Count Message. */
    BCM(7),

    /** Output Paper Handling. */
    APA(8),

    /** Output Window Size. */
    SL(9),

    /** Terminal Type (RFC 1091). */
    TTYPE(24),

    /** Window Size (NAWS, RFC 1073). */
    NAWS(31),

    /** Linemode (RFC 1143). */
    LINEMODE(32),

    /** X Display Location. */
    XDISPLAY_LOC(35),

    /** Environment Option. */
    ENVIRON(36),

    /** Authentication (RFC 1116). */
    AUTH(37),

    /** Encryption (RFC 1116). */
    ENCRYPT(38),

    /** New Environment. */
    NEW_ENV(39),

    /** Com Port Control. */
    COM_PORT(40),

    /** X Assistant. */
    XASSIST(41),

    /** TN5250 — 5250 terminal emulation over TN5250 (RFC 1662). */
    TN5250(30),

    /** Terminal Speed (RFC 1079). */
    TERMINAL_SPEED(42),

    /** Telnet Output. */
    TO(43),

    /** Net Type. */
    NET_TYPE(44),

    /** Telnet Ready Message. */
    MSG(45),

    /** Suppress Local Echo. */
    SLCE(47),

    /** Terminal ID. */
    TID(50),

    /** TACACS User Identification. */
    TUID(51),

    /** UDPPASSPORT. */
    UDPPASSPORT(52),

    /** TTYSTATUS. */
    TTYSTATUS(53),

    /** 64-bit UNIX Process. */
    UNIX_PROCESS(54),

    /** Binary Patch. */
    BINARY_PATCH(55),

    /** PC-SHOOTOUT. */
    PC_SHOOTOUT(56),

    /** PC More. */
    PC_MORE(57),

    /** PC SysRq. */
    PC_SYSRQ(58),

    /** PC VCP. */
    PC_VCP(59),

    /** PC XMODEM. */
    PC_XMODEM(60),

    /** PC LSSR. */
    PC_LSSR(61),

    /** PC XLAT. */
    PC_XLAT(62),

    /** TN3270 — 3270 terminal emulation over Telnet (RFC 1576). */
    TN3270(255);

    private final int code;

    TelnetOption(int code) {
        this.code = code;
    }

    /**
     * Get the numeric code for this option.
     */
    public int code() {
        return code;
    }

    /**
     * Lookup an option by numeric code.
     *
     * @param code the octet value (0–255)
     * @return the option, or null if unknown
     */
    public static TelnetOption fromCode(int code) {
        for (TelnetOption opt : values()) {
            if (opt.code == code) return opt;
        }
        return null;
    }
}
