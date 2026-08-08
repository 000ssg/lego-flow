package ssg.legoflow.database.postgresql.protocol;

/**
 * Transaction status indicators used in ReadyForQuery messages.
 *
 * @since 0.1.0
 */
public enum TransactionStatus {

    /** Idle (not in a transaction block). */
    IDLE('I'),

    /** In a transaction block. */
    IN_TRANSACTION('T'),

    /** In a failed transaction block. */
    FAILED('E');

    private final byte indicator;

    TransactionStatus(char indicator) {
        this.indicator = (byte) indicator;
    }

    /**
     * Returns the single-byte indicator used in the wire protocol.
     *
     * @return the indicator byte
     */
    public byte indicator() {
        return indicator;
    }

    /**
     * Parses a transaction status from its wire protocol indicator byte.
     *
     * @param b the indicator byte
     * @return the matching status
     * @throws IllegalArgumentException if the byte is not a valid indicator
     */
    public static TransactionStatus fromByte(byte b) {
        return switch (b) {
            case (byte) 'I' -> IDLE;
            case (byte) 'T' -> IN_TRANSACTION;
            case (byte) 'E' -> FAILED;
            default -> throw new IllegalArgumentException(
                    "Unknown transaction status indicator: " + (char) b);
        };
    }
}
