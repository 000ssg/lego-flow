package ssg.legoflow.network.ldap.protocol;

/**
 * Alias dereferencing policy as defined in RFC 4511 Section 4.5.1.
 *
 * @since 0.1.0
 */
public enum DerefAliases {

    /** Never dereference aliases. */
    NEVER_DEREF_ALIASES(0),
    /** Dereference aliases while searching below the base. */
    DEREF_IN_SEARCHING(1),
    /** Dereference the base object if it is an alias. */
    DEREF_FINDING_BASE_OBJ(2),
    /** Always dereference aliases. */
    DEREF_ALWAYS(3);

    private final int value;

    DerefAliases(int value) {
        this.value = value;
    }

    /**
     * Returns the integer value.
     *
     * @return the value
     */
    public int value() {
        return value;
    }

    /**
     * Returns the deref aliases policy for the given value.
     *
     * @param value the integer value
     * @return the deref aliases policy
     * @throws IllegalArgumentException if the value is invalid
     */
    public static DerefAliases of(int value) {
        return switch (value) {
            case 0 -> NEVER_DEREF_ALIASES;
            case 1 -> DEREF_IN_SEARCHING;
            case 2 -> DEREF_FINDING_BASE_OBJ;
            case 3 -> DEREF_ALWAYS;
            default -> throw new IllegalArgumentException("Unknown deref aliases: " + value);
        };
    }
}
