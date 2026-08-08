package ssg.legoflow.network.modbus.protocol;

/**
 * Exception for Modbus protocol errors.
 *
 * @since 0.1.0
 */
public class ModbusException extends RuntimeException {

    private final ExceptionCode exceptionCode;

    /**
     * Creates a Modbus exception with a message.
     *
     * @param message the error message
     */
    public ModbusException(String message) {
        super(message);
        this.exceptionCode = null;
    }

    /**
     * Creates a Modbus exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public ModbusException(String message, Throwable cause) {
        super(message, cause);
        this.exceptionCode = null;
    }

    /**
     * Creates a Modbus exception with a standard exception code.
     *
     * @param exceptionCode the Modbus exception code
     * @param message       the error message
     */
    public ModbusException(ExceptionCode exceptionCode, String message) {
        super(message);
        this.exceptionCode = exceptionCode;
    }

    /**
     * Returns the Modbus exception code, if any.
     *
     * @return the exception code, or null
     */
    public ExceptionCode exceptionCode() {
        return exceptionCode;
    }

    /**
     * Standard Modbus exception codes.
     */
    public enum ExceptionCode {
        /** Function code not supported (01). */
        ILLEGAL_FUNCTION(0x01),
        /** Data address not allowed (02). */
        ILLEGAL_DATA_ADDRESS(0x02),
        /** Data value not allowed (03). */
        ILLEGAL_DATA_VALUE(0x03),
        /** Unrecoverable error on server device (04). */
        SERVER_DEVICE_FAILURE(0x04),
        /** Request accepted, processing takes time (05). */
        ACKNOWLEDGE(0x05),
        /** Server device busy (06). */
        SERVER_DEVICE_BUSY(0x06);

        private final int code;

        ExceptionCode(int code) {
            this.code = code;
        }

        /**
         * Returns the numeric exception code.
         *
         * @return the code byte value
         */
        public int code() {
            return code;
        }

        /**
         * Returns the exception code for the given byte value.
         *
         * @param code the code value
         * @return the corresponding exception code
         * @throws IllegalArgumentException if the code is unknown
         */
        public static ExceptionCode of(int code) {
            for (ExceptionCode ec : values()) {
                if (ec.code == code) {
                    return ec;
                }
            }
            throw new IllegalArgumentException("Unknown exception code: " + code);
        }
    }
}
