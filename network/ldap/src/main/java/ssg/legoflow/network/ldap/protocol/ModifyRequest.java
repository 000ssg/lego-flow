package ssg.legoflow.network.ldap.protocol;

import java.util.List;

/**
 * LDAP Modify Request (APPLICATION 6) as defined in RFC 4511 Section 4.6.
 *
 * <pre>{@code
 * ModifyRequest ::= [APPLICATION 6] SEQUENCE {
 *     object   LDAPDN,
 *     changes  SEQUENCE OF change SEQUENCE {
 *         operation  ENUMERATED { add(0), delete(1), replace(2) },
 *         modification PartialAttribute
 *     }
 * }
 * }</pre>
 *
 * @param object  the DN of the entry to modify
 * @param changes the list of modifications
 * @since 0.1.0
 */
public record ModifyRequest(
        String object,
        List<Change> changes
) implements LdapProtocolOp {

    /** APPLICATION tag number for ModifyRequest. */
    public static final int TAG = 6;

    /** Creates a modify request with validation. */
    public ModifyRequest {
        if (object == null) throw new IllegalArgumentException("Object must not be null");
        if (changes == null) throw new IllegalArgumentException("Changes must not be null");
        changes = List.copyOf(changes);
    }

    @Override
    public int tagNumber() { return TAG; }

    /**
     * A single modification within a modify request.
     *
     * @param operation    the modification operation
     * @param modification the attribute to modify
     * @since 0.1.0
     */
    public record Change(ModifyOperation operation, LdapAttribute modification) {
        /** Creates a change with validation. */
        public Change {
            if (operation == null) throw new IllegalArgumentException("Operation must not be null");
            if (modification == null) throw new IllegalArgumentException("Modification must not be null");
        }
    }

    /**
     * Modification operations.
     *
     * @since 0.1.0
     */
    public enum ModifyOperation {
        /** Add values to the attribute. */
        ADD(0),
        /** Delete values from the attribute. */
        DELETE(1),
        /** Replace all values of the attribute. */
        REPLACE(2);

        private final int value;

        ModifyOperation(int value) { this.value = value; }

        /**
         * Returns the integer value.
         *
         * @return the value
         */
        public int value() { return value; }

        /**
         * Returns the operation for the given integer value.
         *
         * @param value the integer value
         * @return the operation
         */
        public static ModifyOperation of(int value) {
            return switch (value) {
                case 0 -> ADD;
                case 1 -> DELETE;
                case 2 -> REPLACE;
                default -> throw new IllegalArgumentException("Unknown modify operation: " + value);
            };
        }
    }
}
