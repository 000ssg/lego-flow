package ssg.legoflow.network.modbus.protocol;

/**
 * Modbus function codes.
 *
 * <p>Each function code identifies a specific Modbus operation. Exception
 * responses use the function code with bit 7 set (function code + 0x80).
 *
 * @since 1.0.0
 */
public enum FunctionCode {

    /** Read Coils (FC 01) — read 1-2000 contiguous coils. */
    READ_COILS(0x01),
    /** Read Discrete Inputs (FC 02) — read 1-2000 contiguous discrete inputs. */
    READ_DISCRETE_INPUTS(0x02),
    /** Read Holding Registers (FC 03) — read 1-125 contiguous holding registers. */
    READ_HOLDING_REGISTERS(0x03),
    /** Read Input Registers (FC 04) — read 1-125 contiguous input registers. */
    READ_INPUT_REGISTERS(0x04),
    /** Write Single Coil (FC 05) — write one coil. */
    WRITE_SINGLE_COIL(0x05),
    /** Write Single Register (FC 06) — write one holding register. */
    WRITE_SINGLE_REGISTER(0x06),
    /** Write Multiple Coils (FC 15) — write 1-1968 contiguous coils. */
    WRITE_MULTIPLE_COILS(0x0F),
    /** Write Multiple Registers (FC 16) — write 1-123 contiguous holding registers. */
    WRITE_MULTIPLE_REGISTERS(0x10),
    /** Read/Write Multiple Registers (FC 23) — combined read and write. */
    READ_WRITE_MULTIPLE_REGISTERS(0x17);

    private final int code;

    FunctionCode(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric function code.
     *
     * @return the function code byte value
     */
    public int code() {
        return code;
    }

    /**
     * Returns the exception response code (function code + 0x80).
     *
     * @return the exception function code
     */
    public int exceptionCode() {
        return code | 0x80;
    }

    /**
     * Returns the function code for the given byte value.
     *
     * @param code the function code byte
     * @return the corresponding enum constant
     * @throws IllegalArgumentException if the code is unknown
     */
    public static FunctionCode of(int code) {
        for (FunctionCode fc : values()) {
            if (fc.code == code) {
                return fc;
            }
        }
        throw new IllegalArgumentException("Unknown function code: 0x" + Integer.toHexString(code));
    }

    /**
     * Returns whether the given code is an exception response.
     *
     * @param code the function code byte
     * @return true if the high bit is set
     */
    public static boolean isException(int code) {
        return (code & 0x80) != 0;
    }

    /**
     * Returns the original function code from an exception code.
     *
     * @param exceptionCode the exception function code (with high bit set)
     * @return the original function code
     */
    public static FunctionCode fromException(int exceptionCode) {
        return of(exceptionCode & 0x7F);
    }
}
