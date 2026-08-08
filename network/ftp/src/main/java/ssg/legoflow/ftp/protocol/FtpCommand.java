package ssg.legoflow.ftp.protocol;

/**
 * Enumeration of all FTP commands as defined in RFC 959 and extensions.
 *
 * <p>Includes commands from:
 * <ul>
 *   <li>RFC 959 — File Transfer Protocol (core)</li>
 *   <li>RFC 2389 — Feature negotiation (FEAT, OPTS)</li>
 *   <li>RFC 2428 — FTP Extensions for IPv6 (EPRT, EPSV)</li>
 *   <li>RFC 3659 — Extensions to FTP (SIZE, MDTM, MLST, MLSD)</li>
 *   <li>RFC 4217 — Securing FTP with TLS (AUTH, PBSZ, PROT, CCC)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public enum FtpCommand {

    // --- RFC 959 Access Control Commands ---

    /** User name for authentication. */
    USER,
    /** Password for authentication. */
    PASS,
    /** Account information. */
    ACCT,
    /** Change working directory. */
    CWD,
    /** Change to parent directory. */
    CDUP,
    /** Structure mount. */
    SMNT,
    /** Logout and close control connection. */
    QUIT,
    /** Reinitialize session. */
    REIN,

    // --- RFC 959 Transfer Parameter Commands ---

    /** Data port (active mode). */
    PORT,
    /** Passive mode. */
    PASV,
    /** Representation type (ASCII, IMAGE, EBCDIC). */
    TYPE,
    /** File structure (File, Record, Page). */
    STRU,
    /** Transfer mode (Stream, Block, Compressed). */
    MODE,

    // --- RFC 959 Service Commands ---

    /** Retrieve a file. */
    RETR,
    /** Store a file. */
    STOR,
    /** Store unique. */
    STOU,
    /** Append to a file. */
    APPE,
    /** Allocate storage space. */
    ALLO,
    /** Restart transfer at marker. */
    REST,
    /** Rename from. */
    RNFR,
    /** Rename to. */
    RNTO,
    /** Delete file. */
    DELE,
    /** Remove directory. */
    RMD,
    /** Make directory. */
    MKD,
    /** Print working directory. */
    PWD,
    /** List files/directories. */
    LIST,
    /** Name list (filenames only). */
    NLST,
    /** Site-specific command. */
    SITE,
    /** System type. */
    SYST,
    /** Status. */
    STAT,
    /** Help. */
    HELP,
    /** No operation (keep-alive). */
    NOOP,
    /** Abort current transfer. */
    ABOR,

    // --- RFC 2389 Feature Negotiation ---

    /** List supported features. */
    FEAT,
    /** Set options for a feature. */
    OPTS,

    // --- RFC 3659 Extensions ---

    /** File size. */
    SIZE,
    /** File modification time. */
    MDTM,
    /** Machine-readable listing of a single entry. */
    MLST,
    /** Machine-readable directory listing. */
    MLSD,

    // --- RFC 2428 IPv6 Extensions ---

    /** Extended data port (active mode, IPv6-capable). */
    EPRT,
    /** Extended passive mode (IPv6-capable). */
    EPSV,

    // --- RFC 4217 TLS Security ---

    /** Authentication/security mechanism (AUTH TLS / AUTH SSL). */
    AUTH,
    /** Protection buffer size. */
    PBSZ,
    /** Data channel protection level (C = Clear, P = Private). */
    PROT,
    /** Clear command channel (downgrade from TLS). */
    CCC,

    // --- Miscellaneous ---

    /** Enable UTF-8 encoding. */
    UTF8;

    /**
     * Parses a command string into an {@link FtpCommand}.
     *
     * @param text the command string (case-insensitive)
     * @return the matching command
     * @throws IllegalArgumentException if the command is not recognized
     */
    public static FtpCommand parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Command text must not be null or blank");
        }
        return valueOf(text.trim().toUpperCase());
    }

    /**
     * Returns the command as it appears on the wire (uppercase name).
     *
     * @return the command string
     */
    public String wireForm() {
        return name();
    }
}
