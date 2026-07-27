package ssg.legoflow.ssh.transport;

/**
 * SSH message type constants as defined across RFC 4250, RFC 4253, RFC 4252, and RFC 4254.
 *
 * <p>Message numbers 1-19 are transport layer generic, 20-29 are algorithm negotiation,
 * 30-49 are key exchange method specific, 50-59 are user authentication generic,
 * 60-79 are user authentication method specific, and 80-127 are connection protocol.
 *
 * @since 1.0.0
 */
public enum SshMessageType {

    // --- Transport layer generic (1-19) ---
    /** Disconnect message. */
    SSH_MSG_DISCONNECT(1),
    /** Ignore message (used for padding). */
    SSH_MSG_IGNORE(2),
    /** Unimplemented message notification. */
    SSH_MSG_UNIMPLEMENTED(3),
    /** Debug message. */
    SSH_MSG_DEBUG(4),
    /** Service request. */
    SSH_MSG_SERVICE_REQUEST(5),
    /** Service accept. */
    SSH_MSG_SERVICE_ACCEPT(6),
    /** Extension info (RFC 8308). */
    SSH_MSG_EXT_INFO(7),
    /** New keys message (signals switch to new encryption). */
    SSH_MSG_NEWKEYS(21),

    // --- Algorithm negotiation (20-29) ---
    /** Key exchange initialization. */
    SSH_MSG_KEXINIT(20),

    // --- Key exchange method specific (30-49) ---
    /** DH key exchange init (RFC 4253). */
    SSH_MSG_KEXDH_INIT(30),
    /** DH key exchange reply (RFC 4253). */
    SSH_MSG_KEXDH_REPLY(31),
    /** ECDH key exchange init (RFC 5656). */
    SSH_MSG_KEX_ECDH_INIT(30),
    /** ECDH key exchange reply (RFC 5656). */
    SSH_MSG_KEX_ECDH_REPLY(31),

    // --- User authentication generic (50-59) ---
    /** Authentication request. */
    SSH_MSG_USERAUTH_REQUEST(50),
    /** Authentication failure. */
    SSH_MSG_USERAUTH_FAILURE(51),
    /** Authentication success. */
    SSH_MSG_USERAUTH_SUCCESS(52),
    /** Authentication banner. */
    SSH_MSG_USERAUTH_BANNER(53),

    // --- User authentication method specific (60-79) ---
    /** Public key OK. */
    SSH_MSG_USERAUTH_PK_OK(60),
    /** Password change request. */
    SSH_MSG_USERAUTH_PASSWD_CHANGEREQ(60),
    /** Keyboard-interactive info request. */
    SSH_MSG_USERAUTH_INFO_REQUEST(60),
    /** Keyboard-interactive info response. */
    SSH_MSG_USERAUTH_INFO_RESPONSE(61),

    // --- Connection protocol (80-127) ---
    /** Global request. */
    SSH_MSG_GLOBAL_REQUEST(80),
    /** Request success. */
    SSH_MSG_REQUEST_SUCCESS(81),
    /** Request failure. */
    SSH_MSG_REQUEST_FAILURE(82),
    /** Channel open. */
    SSH_MSG_CHANNEL_OPEN(90),
    /** Channel open confirmation. */
    SSH_MSG_CHANNEL_OPEN_CONFIRMATION(91),
    /** Channel open failure. */
    SSH_MSG_CHANNEL_OPEN_FAILURE(92),
    /** Channel window adjust. */
    SSH_MSG_CHANNEL_WINDOW_ADJUST(93),
    /** Channel data. */
    SSH_MSG_CHANNEL_DATA(94),
    /** Channel extended data. */
    SSH_MSG_CHANNEL_EXTENDED_DATA(95),
    /** Channel EOF. */
    SSH_MSG_CHANNEL_EOF(96),
    /** Channel close. */
    SSH_MSG_CHANNEL_CLOSE(97),
    /** Channel request. */
    SSH_MSG_CHANNEL_REQUEST(98),
    /** Channel success. */
    SSH_MSG_CHANNEL_SUCCESS(99),
    /** Channel failure. */
    SSH_MSG_CHANNEL_FAILURE(100);

    private final int code;

    SshMessageType(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric message type code.
     *
     * @return the message type code (1-255)
     */
    public int code() {
        return code;
    }

    /**
     * Resolves a message type from its numeric code.
     *
     * <p>Note: some codes are shared between DH and ECDH (30, 31) and between
     * different auth methods (60, 61). This method returns the first match.
     *
     * @param code the message type code
     * @return the matching message type
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static SshMessageType fromCode(int code) {
        for (SshMessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SSH message type code: " + code);
    }
}
