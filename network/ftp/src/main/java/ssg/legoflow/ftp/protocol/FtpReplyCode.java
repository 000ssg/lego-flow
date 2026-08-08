package ssg.legoflow.ftp.protocol;

/**
 * Standard FTP reply codes as defined in RFC 959.
 *
 * <p>Reply codes are three-digit numbers where:
 * <ul>
 *   <li>1xx — Positive Preliminary (action started, expect another reply)</li>
 *   <li>2xx — Positive Completion (action completed successfully)</li>
 *   <li>3xx — Positive Intermediate (command accepted, send another command)</li>
 *   <li>4xx — Transient Negative (temporary failure, try again)</li>
 *   <li>5xx — Permanent Negative (command failed, do not retry)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public enum FtpReplyCode {

    // --- 1xx Positive Preliminary ---

    /** 110 Restart marker reply. */
    RESTART_MARKER(110, "Restart marker reply"),
    /** 120 Service ready in nnn minutes. */
    SERVICE_READY_IN_MINUTES(120, "Service ready in nnn minutes"),
    /** 125 Data connection already open; transfer starting. */
    DATA_CONNECTION_OPEN(125, "Data connection already open; transfer starting"),
    /** 150 File status okay; about to open data connection. */
    FILE_STATUS_OK(150, "File status okay; about to open data connection"),

    // --- 2xx Positive Completion ---

    /** 200 Command okay. */
    COMMAND_OK(200, "Command okay"),
    /** 202 Command not implemented, superfluous at this site. */
    COMMAND_NOT_IMPLEMENTED_SUPERFLUOUS(202, "Command not implemented, superfluous at this site"),
    /** 211 System status, or system help reply. */
    SYSTEM_STATUS(211, "System status or system help reply"),
    /** 212 Directory status. */
    DIRECTORY_STATUS(212, "Directory status"),
    /** 213 File status. */
    FILE_STATUS(213, "File status"),
    /** 214 Help message. */
    HELP_MESSAGE(214, "Help message"),
    /** 215 NAME system type. */
    NAME_SYSTEM_TYPE(215, "NAME system type"),
    /** 220 Service ready for new user. */
    SERVICE_READY(220, "Service ready for new user"),
    /** 221 Service closing control connection. */
    SERVICE_CLOSING(221, "Service closing control connection"),
    /** 225 Data connection open; no transfer in progress. */
    DATA_CONNECTION_OPEN_NO_TRANSFER(225, "Data connection open; no transfer in progress"),
    /** 226 Closing data connection. Transfer complete. */
    CLOSING_DATA_CONNECTION(226, "Closing data connection. Transfer complete"),
    /** 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2). */
    ENTERING_PASSIVE_MODE(227, "Entering Passive Mode"),
    /** 229 Entering Extended Passive Mode (|||port|). */
    ENTERING_EXTENDED_PASSIVE_MODE(229, "Entering Extended Passive Mode"),
    /** 230 User logged in, proceed. */
    USER_LOGGED_IN(230, "User logged in, proceed"),
    /** 234 Security data exchange complete (AUTH TLS accepted). */
    SECURITY_DATA_EXCHANGE_COMPLETE(234, "Security data exchange complete"),
    /** 250 Requested file action okay, completed. */
    FILE_ACTION_OK(250, "Requested file action okay, completed"),
    /** 257 "PATHNAME" created. */
    PATHNAME_CREATED(257, "Pathname created"),

    // --- 3xx Positive Intermediate ---

    /** 331 User name okay, need password. */
    USER_OK_NEED_PASSWORD(331, "User name okay, need password"),
    /** 332 Need account for login. */
    NEED_ACCOUNT(332, "Need account for login"),
    /** 334 Security mechanism accepted (intermediate). */
    SECURITY_MECHANISM_ACCEPTED(334, "Security mechanism accepted"),
    /** 350 Requested file action pending further information. */
    FILE_ACTION_PENDING(350, "Requested file action pending further information"),

    // --- 4xx Transient Negative ---

    /** 421 Service not available, closing control connection. */
    SERVICE_NOT_AVAILABLE(421, "Service not available, closing control connection"),
    /** 425 Can't open data connection. */
    CANT_OPEN_DATA_CONNECTION(425, "Can't open data connection"),
    /** 426 Connection closed; transfer aborted. */
    CONNECTION_CLOSED_TRANSFER_ABORTED(426, "Connection closed; transfer aborted"),
    /** 431 Need unavailable resource for security. */
    NEED_UNAVAILABLE_RESOURCE(431, "Need unavailable resource for security"),
    /** 450 Requested file action not taken; file unavailable. */
    FILE_ACTION_NOT_TAKEN(450, "Requested file action not taken"),
    /** 451 Requested action aborted: local error in processing. */
    ACTION_ABORTED_LOCAL_ERROR(451, "Requested action aborted: local error"),
    /** 452 Requested action not taken: insufficient storage space. */
    INSUFFICIENT_STORAGE(452, "Requested action not taken: insufficient storage space"),

    // --- 5xx Permanent Negative ---

    /** 500 Syntax error, command unrecognized. */
    SYNTAX_ERROR(500, "Syntax error, command unrecognized"),
    /** 501 Syntax error in parameters or arguments. */
    SYNTAX_ERROR_PARAMETERS(501, "Syntax error in parameters or arguments"),
    /** 502 Command not implemented. */
    COMMAND_NOT_IMPLEMENTED(502, "Command not implemented"),
    /** 503 Bad sequence of commands. */
    BAD_COMMAND_SEQUENCE(503, "Bad sequence of commands"),
    /** 504 Command not implemented for that parameter. */
    COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER(504, "Command not implemented for that parameter"),
    /** 521 Data connection cannot be opened with this PROT setting. */
    DATA_CONNECTION_CANNOT_BE_OPENED(521, "Data connection cannot be opened with this PROT setting"),
    /** 522 Network protocol not supported. */
    NETWORK_PROTOCOL_NOT_SUPPORTED(522, "Network protocol not supported"),
    /** 530 Not logged in. */
    NOT_LOGGED_IN(530, "Not logged in"),
    /** 532 Need account for storing files. */
    NEED_ACCOUNT_FOR_STORING(532, "Need account for storing files"),
    /** 534 Security mechanism not accepted. */
    SECURITY_MECHANISM_NOT_ACCEPTED(534, "Security mechanism not accepted"),
    /** 550 Requested action not taken: file unavailable. */
    FILE_UNAVAILABLE(550, "Requested action not taken: file unavailable"),
    /** 551 Requested action aborted: page type unknown. */
    PAGE_TYPE_UNKNOWN(551, "Requested action aborted: page type unknown"),
    /** 552 Requested file action aborted: exceeded storage allocation. */
    EXCEEDED_STORAGE_ALLOCATION(552, "Exceeded storage allocation"),
    /** 553 Requested action not taken: file name not allowed. */
    FILE_NAME_NOT_ALLOWED(553, "Requested action not taken: file name not allowed");

    private final int code;
    private final String description;

    FtpReplyCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the three-digit numeric reply code.
     *
     * @return the reply code
     */
    public int code() {
        return code;
    }

    /**
     * Returns a human-readable description of this reply code.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Returns {@code true} if this is a positive preliminary reply (1xx).
     *
     * @return true for 1xx codes
     */
    public boolean isPreliminary() {
        return code >= 100 && code < 200;
    }

    /**
     * Returns {@code true} if this is a positive completion reply (2xx).
     *
     * @return true for 2xx codes
     */
    public boolean isPositiveCompletion() {
        return code >= 200 && code < 300;
    }

    /**
     * Returns {@code true} if this is a positive intermediate reply (3xx).
     *
     * @return true for 3xx codes
     */
    public boolean isPositiveIntermediate() {
        return code >= 300 && code < 400;
    }

    /**
     * Returns {@code true} if this is a transient negative reply (4xx).
     *
     * @return true for 4xx codes
     */
    public boolean isTransientNegative() {
        return code >= 400 && code < 500;
    }

    /**
     * Returns {@code true} if this is a permanent negative reply (5xx).
     *
     * @return true for 5xx codes
     */
    public boolean isPermanentNegative() {
        return code >= 500 && code < 600;
    }

    /**
     * Looks up a reply code by its numeric value.
     *
     * @param code the three-digit code
     * @return the matching {@link FtpReplyCode}
     * @throws IllegalArgumentException if no match is found
     */
    public static FtpReplyCode fromCode(int code) {
        for (FtpReplyCode rc : values()) {
            if (rc.code == code) {
                return rc;
            }
        }
        throw new IllegalArgumentException("Unknown FTP reply code: " + code);
    }
}
