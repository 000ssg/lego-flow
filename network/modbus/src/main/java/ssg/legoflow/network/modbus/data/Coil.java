package ssg.legoflow.network.modbus.data;

/**
 * A single-bit coil value (read/write).
 *
 * <p>Coils represent single-bit outputs that can be read and written.
 * In Modbus, coils occupy the address space 00001-09999.
 *
 * @param address the coil address
 * @param value   the coil state (true = ON, false = OFF)
 * @since 1.0.0
 */
public record Coil(int address, boolean value) {

    /**
     * Creates a coil with validation.
     */
    public Coil {
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("Address out of range: " + address);
        }
    }

    /**
     * Creates an ON coil.
     *
     * @param address the coil address
     * @return the coil
     */
    public static Coil on(int address) {
        return new Coil(address, true);
    }

    /**
     * Creates an OFF coil.
     *
     * @param address the coil address
     * @return the coil
     */
    public static Coil off(int address) {
        return new Coil(address, false);
    }
}
