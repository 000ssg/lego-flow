package ssg.legoflow.network.modbus.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.modbus.protocol.ModbusCodec;
import ssg.legoflow.network.modbus.protocol.ModbusFrame;
import java.io.IOException;
/**
 * High-level Modbus TCP client.
 *
 * <p>Provides typed methods for all supported Modbus operations.
 * Handles frame construction, communication, and response parsing.
 *
 * @since 0.1.0
 */
public final class ModbusClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusClient.class);

    private final ModbusConnection connection;
    private final int unitId;

    /**
     * Creates a client connected to the given server.
     *
     * @param host   the server hostname
     * @param port   the server port
     * @param unitId the default unit ID
     * @throws IOException if connection fails
     */
    public ModbusClient(String host, int port, int unitId) throws IOException {
        this.connection = new ModbusConnection(host, port);
        this.unitId = unitId;
        LOG.debug("Modbus client connected to {}:{}", host, port);
    }

    /**
     * Creates a client with unit ID 1.
     *
     * @param host the server hostname
     * @param port the server port
     * @throws IOException if connection fails
     */
    public ModbusClient(String host, int port) throws IOException {
        this(host, port, 1);
    }

    /**
     * Reads coils (FC 01).
     *
     * @param startAddress the starting address
     * @param quantity     the number of coils (1-2000)
     * @return the coil values
     * @throws IOException if communication fails
     */
    public boolean[] readCoils(int startAddress, int quantity) throws IOException {
        byte[] pdu = ModbusCodec.buildReadCoilsRequest(startAddress, quantity);
        ModbusFrame response = connection.sendRequest(unitId, pdu);
        byte[] data = response.data();
        int byteCount = data[0] & 0xFF;
        byte[] coilData = new byte[byteCount];
        System.arraycopy(data, 1, coilData, 0, byteCount);
        return ModbusCodec.unpackBooleans(coilData, quantity);
    }

    /**
     * Reads discrete inputs (FC 02).
     *
     * @param startAddress the starting address
     * @param quantity     the number of inputs (1-2000)
     * @return the input values
     * @throws IOException if communication fails
     */
    public boolean[] readDiscreteInputs(int startAddress, int quantity) throws IOException {
        byte[] pdu = ModbusCodec.buildReadDiscreteInputsRequest(startAddress, quantity);
        ModbusFrame response = connection.sendRequest(unitId, pdu);
        byte[] data = response.data();
        int byteCount = data[0] & 0xFF;
        byte[] inputData = new byte[byteCount];
        System.arraycopy(data, 1, inputData, 0, byteCount);
        return ModbusCodec.unpackBooleans(inputData, quantity);
    }

    /**
     * Reads holding registers (FC 03).
     *
     * @param startAddress the starting address
     * @param quantity     the number of registers (1-125)
     * @return the register values (unsigned 16-bit)
     * @throws IOException if communication fails
     */
    public int[] readHoldingRegisters(int startAddress, int quantity) throws IOException {
        byte[] pdu = ModbusCodec.buildReadHoldingRegistersRequest(startAddress, quantity);
        ModbusFrame response = connection.sendRequest(unitId, pdu);
        byte[] data = response.data();
        int byteCount = data[0] & 0xFF;
        byte[] regData = new byte[byteCount];
        System.arraycopy(data, 1, regData, 0, byteCount);
        return ModbusCodec.unpackRegisters(regData);
    }

    /**
     * Reads input registers (FC 04).
     *
     * @param startAddress the starting address
     * @param quantity     the number of registers (1-125)
     * @return the register values (unsigned 16-bit)
     * @throws IOException if communication fails
     */
    public int[] readInputRegisters(int startAddress, int quantity) throws IOException {
        byte[] pdu = ModbusCodec.buildReadInputRegistersRequest(startAddress, quantity);
        ModbusFrame response = connection.sendRequest(unitId, pdu);
        byte[] data = response.data();
        int byteCount = data[0] & 0xFF;
        byte[] regData = new byte[byteCount];
        System.arraycopy(data, 1, regData, 0, byteCount);
        return ModbusCodec.unpackRegisters(regData);
    }

    /**
     * Writes a single coil (FC 05).
     *
     * @param address the coil address
     * @param value   true for ON, false for OFF
     * @throws IOException if communication fails
     */
    public void writeSingleCoil(int address, boolean value) throws IOException {
        byte[] pdu = ModbusCodec.buildWriteSingleCoilRequest(address, value);
        connection.sendRequest(unitId, pdu);
    }

    /**
     * Writes a single register (FC 06).
     *
     * @param address the register address
     * @param value   the register value (0-65535)
     * @throws IOException if communication fails
     */
    public void writeSingleRegister(int address, int value) throws IOException {
        byte[] pdu = ModbusCodec.buildWriteSingleRegisterRequest(address, value);
        connection.sendRequest(unitId, pdu);
    }

    /**
     * Writes multiple coils (FC 15).
     *
     * @param startAddress the starting address
     * @param values       the coil values
     * @throws IOException if communication fails
     */
    public void writeMultipleCoils(int startAddress, boolean[] values) throws IOException {
        byte[] pdu = ModbusCodec.buildWriteMultipleCoilsRequest(startAddress, values);
        connection.sendRequest(unitId, pdu);
    }

    /**
     * Writes multiple registers (FC 16).
     *
     * @param startAddress the starting address
     * @param values       the register values
     * @throws IOException if communication fails
     */
    public void writeMultipleRegisters(int startAddress, int[] values) throws IOException {
        byte[] pdu = ModbusCodec.buildWriteMultipleRegistersRequest(startAddress, values);
        connection.sendRequest(unitId, pdu);
    }

    /**
     * Reads and writes multiple registers (FC 23).
     *
     * @param readAddress  the starting read address
     * @param readQuantity the number of registers to read
     * @param writeAddress the starting write address
     * @param writeValues  the register values to write
     * @return the read register values
     * @throws IOException if communication fails
     */
    public int[] readWriteMultipleRegisters(int readAddress, int readQuantity,
                                             int writeAddress, int[] writeValues) throws IOException {
        byte[] pdu = ModbusCodec.buildReadWriteMultipleRegistersRequest(
                readAddress, readQuantity, writeAddress, writeValues);
        ModbusFrame response = connection.sendRequest(unitId, pdu);
        byte[] data = response.data();
        int byteCount = data[0] & 0xFF;
        byte[] regData = new byte[byteCount];
        System.arraycopy(data, 1, regData, 0, byteCount);
        return ModbusCodec.unpackRegisters(regData);
    }

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connection.isConnected();
    }

    @Override
    public void close() throws IOException {
        connection.close();
        LOG.debug("Modbus client closed");
    }
}
