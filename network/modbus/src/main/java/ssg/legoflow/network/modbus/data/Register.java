package ssg.legoflow.network.modbus.data;

/**
 * A 16-bit holding register value (read/write).
 *
 * <p>Holding registers are 16-bit values that can be read and written.
 * In Modbus, holding registers occupy the address space 40001-49999.
 *
 * @param address the register address
 * @param value   the register value (0-65535)
 * @since 1.0.0
 */
public record Register(int address, int value) {

    /**
     * Creates a register with validation.
     */
    public Register {
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("Address out of range: " + address);
        }
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of range: " + value);
        }
    }

    /**
     * Creates a register with the given address and value.
     *
     * @param address the register address
     * @param value   the register value
     * @return the register
     */
    public static Register of(int address, int value) {
        return new Register(address, value);
    }
}
