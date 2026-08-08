package ssg.legoflow.network.ldap.protocol;

/**
 * Search scope as defined in RFC 4511 Section 4.5.1.
 *
 * @since 0.1.0
 */
public enum SearchScope {

    /** Search only the base object. */
    BASE_OBJECT(0),
    /** Search one level below the base object. */
    SINGLE_LEVEL(1),
    /** Search the entire subtree below the base object. */
    WHOLE_SUBTREE(2);

    private final int value;

    SearchScope(int value) {
        this.value = value;
    }

    /**
     * Returns the integer value for this scope.
     *
     * @return the scope value
     */
    public int value() {
        return value;
    }

    /**
     * Returns the search scope for the given integer value.
     *
     * @param value the integer value
     * @return the search scope
     * @throws IllegalArgumentException if the value is invalid
     */
    public static SearchScope of(int value) {
        return switch (value) {
            case 0 -> BASE_OBJECT;
            case 1 -> SINGLE_LEVEL;
            case 2 -> WHOLE_SUBTREE;
            default -> throw new IllegalArgumentException("Unknown search scope: " + value);
        };
    }
}
