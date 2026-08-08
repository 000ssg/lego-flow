package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP result codes as defined in RFC 4511 Section 4.1.9.
 *
 * @since 0.1.0
 */
public enum LdapResultCode {

    /** Operation completed successfully. */
    SUCCESS(0),
    /** An internal error occurred. */
    OPERATIONS_ERROR(1),
    /** A protocol violation was detected. */
    PROTOCOL_ERROR(2),
    /** Time limit exceeded. */
    TIME_LIMIT_EXCEEDED(3),
    /** Size limit exceeded. */
    SIZE_LIMIT_EXCEEDED(4),
    /** Compare operation returned false. */
    COMPARE_FALSE(5),
    /** Compare operation returned true. */
    COMPARE_TRUE(6),
    /** Authentication method not supported. */
    AUTH_METHOD_NOT_SUPPORTED(7),
    /** Stronger authentication required. */
    STRONGER_AUTH_REQUIRED(8),
    /** Referral returned. */
    REFERRAL(10),
    /** Administrative limit exceeded. */
    ADMIN_LIMIT_EXCEEDED(11),
    /** Critical extension not supported. */
    UNAVAILABLE_CRITICAL_EXTENSION(12),
    /** Confidentiality required. */
    CONFIDENTIALITY_REQUIRED(13),
    /** SASL bind in progress. */
    SASL_BIND_IN_PROGRESS(14),
    /** No such attribute. */
    NO_SUCH_ATTRIBUTE(16),
    /** Undefined attribute type. */
    UNDEFINED_ATTRIBUTE_TYPE(17),
    /** Inappropriate matching. */
    INAPPROPRIATE_MATCHING(18),
    /** Constraint violation. */
    CONSTRAINT_VIOLATION(19),
    /** Attribute or value already exists. */
    ATTRIBUTE_OR_VALUE_EXISTS(20),
    /** Invalid attribute syntax. */
    INVALID_ATTRIBUTE_SYNTAX(21),
    /** No such object. */
    NO_SUCH_OBJECT(32),
    /** Alias problem. */
    ALIAS_PROBLEM(33),
    /** Invalid DN syntax. */
    INVALID_DN_SYNTAX(34),
    /** Alias dereferencing problem. */
    ALIAS_DEREFERENCING_PROBLEM(36),
    /** Inappropriate authentication. */
    INAPPROPRIATE_AUTHENTICATION(48),
    /** Invalid credentials. */
    INVALID_CREDENTIALS(49),
    /** Insufficient access rights. */
    INSUFFICIENT_ACCESS_RIGHTS(50),
    /** Server is busy. */
    BUSY(51),
    /** Server is unavailable. */
    UNAVAILABLE(52),
    /** Server is unwilling to perform. */
    UNWILLING_TO_PERFORM(53),
    /** Loop detected. */
    LOOP_DETECT(54),
    /** Naming violation. */
    NAMING_VIOLATION(64),
    /** Object class violation. */
    OBJECT_CLASS_VIOLATION(65),
    /** Not allowed on non-leaf. */
    NOT_ALLOWED_ON_NON_LEAF(66),
    /** Not allowed on RDN. */
    NOT_ALLOWED_ON_RDN(67),
    /** Entry already exists. */
    ENTRY_ALREADY_EXISTS(68),
    /** Object class modifications prohibited. */
    OBJECT_CLASS_MODS_PROHIBITED(69),
    /** Affects multiple DSAs. */
    AFFECTS_MULTIPLE_DSAS(71),
    /** Other error. */
    OTHER(80);

    private final int code;

    LdapResultCode(int code) {
        this.code = code;
    }

    /**
     * Returns the integer result code value.
     *
     * @return the result code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the result code for the given integer value.
     *
     * @param code the integer code
     * @return the result code enum constant
     * @throws IllegalArgumentException if the code is unknown
     */
    public static LdapResultCode of(int code) {
        for (LdapResultCode rc : values()) {
            if (rc.code == code) {
                return rc;
            }
        }
        throw new IllegalArgumentException("Unknown LDAP result code: " + code);
    }
}
