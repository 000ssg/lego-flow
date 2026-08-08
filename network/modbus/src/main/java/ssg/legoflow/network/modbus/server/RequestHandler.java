package ssg.legoflow.network.modbus.server;

import ssg.legoflow.network.modbus.protocol.FunctionCode;
import ssg.legoflow.network.modbus.protocol.ModbusCodec;
import ssg.legoflow.network.modbus.protocol.ModbusException;

import java.nio.ByteBuffer;

/**
 * Handles Modbus request PDUs and produces response PDUs.
 *
 * <p>Processes all supported function codes against a {@link DeviceMemory}
 * instance and generates appropriate responses or exception responses.
 *
 * @since 0.1.0
 */
public final class RequestHandler {

    private final DeviceMemory memory;

    /**
     * Creates a request handler backed by the given device memory.
     *
     * @param memory the device memory
     */
    public RequestHandler(DeviceMemory memory) {
        this.memory = memory;
    }

    /**
     * Processes a request PDU and returns the response PDU.
     *
     * @param pdu the request PDU (function code + data)
     * @return the response PDU
     */
    public byte[] handle(byte[] pdu) {
        int fc = pdu[0] & 0xFF;
        try {
            return switch (fc) {
                case 0x01 -> handleReadBits(pdu, true);
                case 0x02 -> handleReadBits(pdu, false);
                case 0x03 -> handleReadRegisters(pdu, true);
                case 0x04 -> handleReadRegisters(pdu, false);
                case 0x05 -> handleWriteSingleCoil(pdu);
                case 0x06 -> handleWriteSingleRegister(pdu);
                case 0x0F -> handleWriteMultipleCoils(pdu);
                case 0x10 -> handleWriteMultipleRegisters(pdu);
                case 0x17 -> handleReadWriteMultipleRegisters(pdu);
                default -> ModbusCodec.buildExceptionResponse(
                        FunctionCode.of(fc),
                        ModbusException.ExceptionCode.ILLEGAL_FUNCTION);
            };
        } catch (IllegalArgumentException e) {
            return buildExceptionForFc(fc, ModbusException.ExceptionCode.ILLEGAL_DATA_ADDRESS);
        } catch (Exception e) {
            return buildExceptionForFc(fc, ModbusException.ExceptionCode.SERVER_DEVICE_FAILURE);
        }
    }

    private byte[] handleReadBits(byte[] pdu, boolean coils) {
        var buf = ByteBuffer.wrap(pdu, 1, 4);
        int startAddress = buf.getShort() & 0xFFFF;
        int quantity = buf.getShort() & 0xFFFF;

        boolean[] values = coils
                ? memory.readCoils(startAddress, quantity)
                : memory.readDiscreteInputs(startAddress, quantity);

        byte[] packed = ModbusCodec.packBooleans(values);
        var resp = ByteBuffer.allocate(2 + packed.length);
        resp.put(pdu[0]);
        resp.put((byte) packed.length);
        resp.put(packed);
        return resp.array();
    }

    private byte[] handleReadRegisters(byte[] pdu, boolean holding) {
        var buf = ByteBuffer.wrap(pdu, 1, 4);
        int startAddress = buf.getShort() & 0xFFFF;
        int quantity = buf.getShort() & 0xFFFF;

        int[] values = holding
                ? memory.readHoldingRegisters(startAddress, quantity)
                : memory.readInputRegisters(startAddress, quantity);

        int byteCount = quantity * 2;
        var resp = ByteBuffer.allocate(2 + byteCount);
        resp.put(pdu[0]);
        resp.put((byte) byteCount);
        for (int v : values) {
            resp.putShort((short) v);
        }
        return resp.array();
    }

    private byte[] handleWriteSingleCoil(byte[] pdu) {
        var buf = ByteBuffer.wrap(pdu, 1, 4);
        int address = buf.getShort() & 0xFFFF;
        int value = buf.getShort() & 0xFFFF;

        if (value != 0xFF00 && value != 0x0000) {
            return ModbusCodec.buildExceptionResponse(
                    FunctionCode.WRITE_SINGLE_COIL,
                    ModbusException.ExceptionCode.ILLEGAL_DATA_VALUE);
        }

        memory.writeCoil(address, value == 0xFF00);
        return pdu.clone(); // echo request
    }

    private byte[] handleWriteSingleRegister(byte[] pdu) {
        var buf = ByteBuffer.wrap(pdu, 1, 4);
        int address = buf.getShort() & 0xFFFF;
        int value = buf.getShort() & 0xFFFF;

        memory.writeHoldingRegister(address, value);
        return pdu.clone(); // echo request
    }

    private byte[] handleWriteMultipleCoils(byte[] pdu) {
        var buf = ByteBuffer.wrap(pdu, 1, pdu.length - 1);
        int startAddress = buf.getShort() & 0xFFFF;
        int quantity = buf.getShort() & 0xFFFF;
        int byteCount = buf.get() & 0xFF;

        byte[] coilData = new byte[byteCount];
        buf.get(coilData);
        boolean[] values = ModbusCodec.unpackBooleans(coilData, quantity);
        memory.writeCoils(startAddress, values);

        var resp = ByteBuffer.allocate(5);
        resp.put((byte) FunctionCode.WRITE_MULTIPLE_COILS.code());
        resp.putShort((short) startAddress);
        resp.putShort((short) quantity);
        return resp.array();
    }

    private byte[] handleWriteMultipleRegisters(byte[] pdu) {
        var buf = ByteBuffer.wrap(pdu, 1, pdu.length - 1);
        int startAddress = buf.getShort() & 0xFFFF;
        int quantity = buf.getShort() & 0xFFFF;
        int byteCount = buf.get() & 0xFF;

        int[] values = new int[quantity];
        for (int i = 0; i < quantity; i++) {
            values[i] = buf.getShort() & 0xFFFF;
        }
        memory.writeHoldingRegisters(startAddress, values);

        var resp = ByteBuffer.allocate(5);
        resp.put((byte) FunctionCode.WRITE_MULTIPLE_REGISTERS.code());
        resp.putShort((short) startAddress);
        resp.putShort((short) quantity);
        return resp.array();
    }

    private byte[] handleReadWriteMultipleRegisters(byte[] pdu) {
        var buf = ByteBuffer.wrap(pdu, 1, pdu.length - 1);
        int readAddress = buf.getShort() & 0xFFFF;
        int readQuantity = buf.getShort() & 0xFFFF;
        int writeAddress = buf.getShort() & 0xFFFF;
        int writeQuantity = buf.getShort() & 0xFFFF;
        int writeByteCount = buf.get() & 0xFF;

        int[] writeValues = new int[writeQuantity];
        for (int i = 0; i < writeQuantity; i++) {
            writeValues[i] = buf.getShort() & 0xFFFF;
        }
        memory.writeHoldingRegisters(writeAddress, writeValues);

        int[] readValues = memory.readHoldingRegisters(readAddress, readQuantity);
        int readByteCount = readQuantity * 2;
        var resp = ByteBuffer.allocate(2 + readByteCount);
        resp.put((byte) FunctionCode.READ_WRITE_MULTIPLE_REGISTERS.code());
        resp.put((byte) readByteCount);
        for (int v : readValues) {
            resp.putShort((short) v);
        }
        return resp.array();
    }

    private byte[] buildExceptionForFc(int fc, ModbusException.ExceptionCode exCode) {
        return new byte[]{(byte) (fc | 0x80), (byte) exCode.code()};
    }
}
