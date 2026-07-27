package ssg.legoflow.network.modbus.server;

import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory Modbus device data store.
 *
 * <p>Stores four data tables per the Modbus data model:
 * <ul>
 *   <li>Coils — single-bit, read/write (FC 01, 05, 15)</li>
 *   <li>Discrete Inputs — single-bit, read-only (FC 02)</li>
 *   <li>Holding Registers — 16-bit, read/write (FC 03, 06, 16)</li>
 *   <li>Input Registers — 16-bit, read-only (FC 04)</li>
 * </ul>
 *
 * <p>Thread-safe: uses read-write locks for concurrent access.
 *
 * @since 1.0.0
 */
public final class DeviceMemory {

    private static final int MAX_ADDRESS = 65536;

    private final boolean[] coils;
    private final boolean[] discreteInputs;
    private final int[] holdingRegisters;
    private final int[] inputRegisters;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a device memory with the default address space (65536 addresses).
     */
    public DeviceMemory() {
        this(MAX_ADDRESS);
    }

    /**
     * Creates a device memory with the given address space size.
     *
     * @param size the number of addresses in each data table
     */
    public DeviceMemory(int size) {
        this.coils = new boolean[size];
        this.discreteInputs = new boolean[size];
        this.holdingRegisters = new int[size];
        this.inputRegisters = new int[size];
    }

    // --- Coils ---

    /**
     * Reads coil values.
     *
     * @param startAddress the starting address
     * @param quantity     the number of coils to read
     * @return the coil values
     */
    public boolean[] readCoils(int startAddress, int quantity) {
        lock.readLock().lock();
        try {
            validateAddress(startAddress, quantity, coils.length);
            return Arrays.copyOfRange(coils, startAddress, startAddress + quantity);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Writes a single coil.
     *
     * @param address the coil address
     * @param value   the coil state
     */
    public void writeCoil(int address, boolean value) {
        lock.writeLock().lock();
        try {
            validateAddress(address, 1, coils.length);
            coils[address] = value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Writes multiple coils.
     *
     * @param startAddress the starting address
     * @param values       the coil values
     */
    public void writeCoils(int startAddress, boolean[] values) {
        lock.writeLock().lock();
        try {
            validateAddress(startAddress, values.length, coils.length);
            System.arraycopy(values, 0, coils, startAddress, values.length);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Discrete Inputs ---

    /**
     * Reads discrete input values.
     *
     * @param startAddress the starting address
     * @param quantity     the number of inputs to read
     * @return the input values
     */
    public boolean[] readDiscreteInputs(int startAddress, int quantity) {
        lock.readLock().lock();
        try {
            validateAddress(startAddress, quantity, discreteInputs.length);
            return Arrays.copyOfRange(discreteInputs, startAddress, startAddress + quantity);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets a discrete input value (for simulation/testing).
     *
     * @param address the input address
     * @param value   the input state
     */
    public void setDiscreteInput(int address, boolean value) {
        lock.writeLock().lock();
        try {
            validateAddress(address, 1, discreteInputs.length);
            discreteInputs[address] = value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Holding Registers ---

    /**
     * Reads holding register values.
     *
     * @param startAddress the starting address
     * @param quantity     the number of registers to read
     * @return the register values
     */
    public int[] readHoldingRegisters(int startAddress, int quantity) {
        lock.readLock().lock();
        try {
            validateAddress(startAddress, quantity, holdingRegisters.length);
            return Arrays.copyOfRange(holdingRegisters, startAddress, startAddress + quantity);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Writes a single holding register.
     *
     * @param address the register address
     * @param value   the register value (0-65535)
     */
    public void writeHoldingRegister(int address, int value) {
        lock.writeLock().lock();
        try {
            validateAddress(address, 1, holdingRegisters.length);
            holdingRegisters[address] = value & 0xFFFF;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Writes multiple holding registers.
     *
     * @param startAddress the starting address
     * @param values       the register values
     */
    public void writeHoldingRegisters(int startAddress, int[] values) {
        lock.writeLock().lock();
        try {
            validateAddress(startAddress, values.length, holdingRegisters.length);
            for (int i = 0; i < values.length; i++) {
                holdingRegisters[startAddress + i] = values[i] & 0xFFFF;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Input Registers ---

    /**
     * Reads input register values.
     *
     * @param startAddress the starting address
     * @param quantity     the number of registers to read
     * @return the register values
     */
    public int[] readInputRegisters(int startAddress, int quantity) {
        lock.readLock().lock();
        try {
            validateAddress(startAddress, quantity, inputRegisters.length);
            return Arrays.copyOfRange(inputRegisters, startAddress, startAddress + quantity);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets an input register value (for simulation/testing).
     *
     * @param address the register address
     * @param value   the register value (0-65535)
     */
    public void setInputRegister(int address, int value) {
        lock.writeLock().lock();
        try {
            validateAddress(address, 1, inputRegisters.length);
            inputRegisters[address] = value & 0xFFFF;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void validateAddress(int startAddress, int quantity, int maxSize) {
        if (startAddress < 0 || startAddress + quantity > maxSize) {
            throw new IllegalArgumentException(
                    "Address range out of bounds: " + startAddress + "+" + quantity);
        }
    }
}
