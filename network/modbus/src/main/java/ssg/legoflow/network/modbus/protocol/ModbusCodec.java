package ssg.legoflow.network.modbus.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.util.Arrays;

/**
 * Codec for encoding and decoding Modbus TCP frames.
 *
 * <p>Handles the MBAP header and PDU serialization/deserialization for
 * Modbus TCP communication.
 *
 * @since 0.1.0
 */
public final class ModbusCodec {

    private ModbusCodec() {}

    /**
     * Encodes a Modbus frame to bytes.
     *
     * @param frame the frame to encode
     * @return the encoded bytes (MBAP header + PDU)
     */
    public static byte[] encode(ModbusFrame frame) {
        byte[] header = frame.header().encode();
        byte[] pdu = frame.pdu();
        byte[] result = new byte[header.length + pdu.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(pdu, 0, result, header.length, pdu.length);
        return result;
    }

    /**
     * Writes a Modbus frame to an output stream.
     *
     * @param frame the frame to write
     * @param out   the output stream
     * @throws IOException if writing fails
     */
    public static void write(ModbusFrame frame, OutputStream out) throws IOException {
        out.write(encode(frame));
        out.flush();
    }

    /**
     * Reads a Modbus frame from an input stream.
     *
     * @param in the input stream
     * @return the decoded frame
     * @throws IOException     if reading fails
     * @throws ModbusException if the frame is malformed
     */
    public static ModbusFrame read(InputStream in) throws IOException {
        byte[] headerBytes = in.readNBytes(MbapHeader.HEADER_SIZE);
        if (headerBytes.length < MbapHeader.HEADER_SIZE) {
            throw new ModbusException("Incomplete MBAP header: " + headerBytes.length + " bytes");
        }
        MbapHeader header = MbapHeader.decode(headerBytes);

        int pduLength = header.length() - 1; // subtract unit ID
        if (pduLength <= 0) {
            throw new ModbusException("Invalid PDU length: " + pduLength);
        }
        byte[] pdu = in.readNBytes(pduLength);
        if (pdu.length < pduLength) {
            throw new ModbusException("Incomplete PDU: expected " + pduLength +
                    " bytes, got " + pdu.length);
        }

        return new ModbusFrame(header, pdu);
    }

    /**
     * Builds a Read Coils (FC 01) request PDU.
     *
     * @param startAddress the starting coil address
     * @param quantity     the number of coils to read (1-2000)
     * @return the PDU bytes
     */
    public static byte[] buildReadCoilsRequest(int startAddress, int quantity) {
        validateRange(quantity, 1, 2000, "quantity");
        return buildReadRequest(FunctionCode.READ_COILS, startAddress, quantity);
    }

    /**
     * Builds a Read Discrete Inputs (FC 02) request PDU.
     *
     * @param startAddress the starting input address
     * @param quantity     the number of inputs to read (1-2000)
     * @return the PDU bytes
     */
    public static byte[] buildReadDiscreteInputsRequest(int startAddress, int quantity) {
        validateRange(quantity, 1, 2000, "quantity");
        return buildReadRequest(FunctionCode.READ_DISCRETE_INPUTS, startAddress, quantity);
    }

    /**
     * Builds a Read Holding Registers (FC 03) request PDU.
     *
     * @param startAddress the starting register address
     * @param quantity     the number of registers to read (1-125)
     * @return the PDU bytes
     */
    public static byte[] buildReadHoldingRegistersRequest(int startAddress, int quantity) {
        validateRange(quantity, 1, 125, "quantity");
        return buildReadRequest(FunctionCode.READ_HOLDING_REGISTERS, startAddress, quantity);
    }

    /**
     * Builds a Read Input Registers (FC 04) request PDU.
     *
     * @param startAddress the starting register address
     * @param quantity     the number of registers to read (1-125)
     * @return the PDU bytes
     */
    public static byte[] buildReadInputRegistersRequest(int startAddress, int quantity) {
        validateRange(quantity, 1, 125, "quantity");
        return buildReadRequest(FunctionCode.READ_INPUT_REGISTERS, startAddress, quantity);
    }

    /**
     * Builds a Write Single Coil (FC 05) request PDU.
     *
     * @param address the coil address
     * @param value   true for ON, false for OFF
     * @return the PDU bytes
     */
    public static byte[] buildWriteSingleCoilRequest(int address, boolean value) {
        var buf = ByteBuffer.allocate(5);
        buf.put((byte) FunctionCode.WRITE_SINGLE_COIL.code());
        buf.putShort((short) address);
        buf.putShort(value ? (short) 0xFF00 : (short) 0x0000);
        return buf.array();
    }

    /**
     * Builds a Write Single Register (FC 06) request PDU.
     *
     * @param address the register address
     * @param value   the register value (0-65535)
     * @return the PDU bytes
     */
    public static byte[] buildWriteSingleRegisterRequest(int address, int value) {
        var buf = ByteBuffer.allocate(5);
        buf.put((byte) FunctionCode.WRITE_SINGLE_REGISTER.code());
        buf.putShort((short) address);
        buf.putShort((short) value);
        return buf.array();
    }

    /**
     * Builds a Write Multiple Coils (FC 15) request PDU.
     *
     * @param startAddress the starting coil address
     * @param values       the coil values
     * @return the PDU bytes
     */
    public static byte[] buildWriteMultipleCoilsRequest(int startAddress, boolean[] values) {
        validateRange(values.length, 1, 1968, "coil count");
        int byteCount = (values.length + 7) / 8;
        var buf = ByteBuffer.allocate(6 + byteCount);
        buf.put((byte) FunctionCode.WRITE_MULTIPLE_COILS.code());
        buf.putShort((short) startAddress);
        buf.putShort((short) values.length);
        buf.put((byte) byteCount);
        byte[] coilBytes = packBooleans(values);
        buf.put(coilBytes);
        return buf.array();
    }

    /**
     * Builds a Write Multiple Registers (FC 16) request PDU.
     *
     * @param startAddress the starting register address
     * @param values       the register values
     * @return the PDU bytes
     */
    public static byte[] buildWriteMultipleRegistersRequest(int startAddress, int[] values) {
        validateRange(values.length, 1, 123, "register count");
        int byteCount = values.length * 2;
        var buf = ByteBuffer.allocate(6 + byteCount);
        buf.put((byte) FunctionCode.WRITE_MULTIPLE_REGISTERS.code());
        buf.putShort((short) startAddress);
        buf.putShort((short) values.length);
        buf.put((byte) byteCount);
        for (int v : values) {
            buf.putShort((short) v);
        }
        return buf.array();
    }

    /**
     * Builds a Read/Write Multiple Registers (FC 23) request PDU.
     *
     * @param readAddress  the starting read address
     * @param readQuantity the number of registers to read
     * @param writeAddress the starting write address
     * @param writeValues  the register values to write
     * @return the PDU bytes
     */
    public static byte[] buildReadWriteMultipleRegistersRequest(
            int readAddress, int readQuantity, int writeAddress, int[] writeValues) {
        validateRange(readQuantity, 1, 125, "read quantity");
        validateRange(writeValues.length, 1, 121, "write count");
        int writeByteCount = writeValues.length * 2;
        var buf = ByteBuffer.allocate(10 + writeByteCount);
        buf.put((byte) FunctionCode.READ_WRITE_MULTIPLE_REGISTERS.code());
        buf.putShort((short) readAddress);
        buf.putShort((short) readQuantity);
        buf.putShort((short) writeAddress);
        buf.putShort((short) writeValues.length);
        buf.put((byte) writeByteCount);
        for (int v : writeValues) {
            buf.putShort((short) v);
        }
        return buf.array();
    }

    /**
     * Builds an exception response PDU.
     *
     * @param functionCode  the original function code
     * @param exceptionCode the exception code
     * @return the PDU bytes
     */
    public static byte[] buildExceptionResponse(FunctionCode functionCode,
                                                 ModbusException.ExceptionCode exceptionCode) {
        return new byte[]{
                (byte) functionCode.exceptionCode(),
                (byte) exceptionCode.code()
        };
    }

    /**
     * Parses coil/discrete input response data into boolean values.
     *
     * @param data     the response data bytes (after byte count)
     * @param quantity the expected number of values
     * @return the boolean values
     */
    public static boolean[] unpackBooleans(byte[] data, int quantity) {
        boolean[] result = new boolean[quantity];
        for (int i = 0; i < quantity; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            result[i] = (data[byteIndex] & (1 << bitIndex)) != 0;
        }
        return result;
    }

    /**
     * Parses register response data into integer values.
     *
     * @param data the response data bytes (after byte count)
     * @return the register values (unsigned 16-bit)
     */
    public static int[] unpackRegisters(byte[] data) {
        int count = data.length / 2;
        int[] result = new int[count];
        var buf = ByteBuffer.wrap(data);
        for (int i = 0; i < count; i++) {
            result[i] = buf.getShort() & 0xFFFF;
        }
        return result;
    }

    /**
     * Packs boolean values into bytes for coil writes.
     *
     * @param values the boolean values
     * @return the packed bytes
     */
    public static byte[] packBooleans(boolean[] values) {
        int byteCount = (values.length + 7) / 8;
        byte[] result = new byte[byteCount];
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                result[i / 8] |= (byte) (1 << (i % 8));
            }
        }
        return result;
    }

    private static byte[] buildReadRequest(FunctionCode fc, int startAddress, int quantity) {
        var buf = ByteBuffer.allocate(5);
        buf.put((byte) fc.code());
        buf.putShort((short) startAddress);
        buf.putShort((short) quantity);
        return buf.array();
    }

    private static void validateRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be " + min + "-" + max + ": " + value);
        }
    }
}
