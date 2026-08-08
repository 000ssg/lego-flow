package ssg.legoflow.network.modbus.data;

/**
 * A single-bit discrete input value (read-only).
 *
 * <p>Discrete inputs represent single-bit inputs that can only be read.
 * In Modbus, discrete inputs occupy the address space 10001-19999.
 *
 * @param address the discrete input address
 * @param value   the input state (true = ON, false = OFF)
 * @since 0.1.0
 */
public record DiscreteInput(int address, boolean value) {

    /**
     * Creates a discrete input with validation.
     */
    public DiscreteInput {
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("Address out of range: " + address);
        }
    }
}
