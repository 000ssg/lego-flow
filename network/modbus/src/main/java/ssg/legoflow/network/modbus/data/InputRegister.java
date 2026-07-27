package ssg.legoflow.network.modbus.data;

/**
 * A 16-bit input register value (read-only).
 *
 * <p>Input registers are 16-bit values that can only be read.
 * In Modbus, input registers occupy the address space 30001-39999.
 *
 * @param address the register address
 * @param value   the register value (0-65535)
 * @since 1.0.0
 */
public record InputRegister(int address, int value) {

    /**
     * Creates an input register with validation.
     */
    public InputRegister {
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("Address out of range: " + address);
        }
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of range: " + value);
        }
    }

    /**
     * Creates an input register with the given address and value.
     *
     * @param address the register address
     * @param value   the register value
     * @return the input register
     */
    public static InputRegister of(int address, int value) {
        return new InputRegister(address, value);
    }
}
