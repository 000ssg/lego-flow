package ssg.legoflow.network.modbus.protocol;

import java.nio.ByteBuffer;

/**
 * Modbus Application Protocol (MBAP) header for Modbus TCP.
 *
 * <p>The MBAP header is 7 bytes:
 * <ul>
 *   <li>Transaction ID (2 bytes) — identifies the request/response pair</li>
 *   <li>Protocol ID (2 bytes) — always 0x0000 for Modbus TCP</li>
 *   <li>Length (2 bytes) — number of following bytes (Unit ID + PDU)</li>
 *   <li>Unit ID (1 byte) — identifies the remote server (slave)</li>
 * </ul>
 *
 * @param transactionId the transaction identifier (0-65535)
 * @param protocolId    the protocol identifier (always 0 for Modbus TCP)
 * @param length        the number of following bytes
 * @param unitId        the unit identifier (0-255)
 * @since 0.1.0
 */
public record MbapHeader(int transactionId, int protocolId, int length, int unitId) {

    /** MBAP header size in bytes. */
    public static final int HEADER_SIZE = 7;
    /** Modbus TCP protocol ID (always 0). */
    public static final int MODBUS_PROTOCOL_ID = 0;

    /**
     * Creates an MBAP header with validation.
     */
    public MbapHeader {
        if (transactionId < 0 || transactionId > 0xFFFF) {
            throw new IllegalArgumentException("Transaction ID out of range: " + transactionId);
        }
        if (protocolId != MODBUS_PROTOCOL_ID) {
            throw new IllegalArgumentException("Invalid protocol ID: " + protocolId);
        }
        if (length < 0 || length > 0xFFFF) {
            throw new IllegalArgumentException("Length out of range: " + length);
        }
        if (unitId < 0 || unitId > 0xFF) {
            throw new IllegalArgumentException("Unit ID out of range: " + unitId);
        }
    }

    /**
     * Creates an MBAP header for a request.
     *
     * @param transactionId the transaction ID
     * @param unitId        the unit ID
     * @param pduLength     the length of the PDU data
     * @return the header
     */
    public static MbapHeader request(int transactionId, int unitId, int pduLength) {
        return new MbapHeader(transactionId, MODBUS_PROTOCOL_ID, pduLength + 1, unitId);
    }

    /**
     * Encodes this header to a byte array.
     *
     * @return the 7-byte header
     */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(HEADER_SIZE);
        buf.putShort((short) transactionId);
        buf.putShort((short) protocolId);
        buf.putShort((short) length);
        buf.put((byte) unitId);
        return buf.array();
    }

    /**
     * Decodes an MBAP header from a byte array.
     *
     * @param data the byte array (at least 7 bytes)
     * @return the decoded header
     * @throws ModbusException if the data is invalid
     */
    public static MbapHeader decode(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new ModbusException("MBAP header too short: " + data.length);
        }
        var buf = ByteBuffer.wrap(data);
        int transactionId = buf.getShort() & 0xFFFF;
        int protocolId = buf.getShort() & 0xFFFF;
        int length = buf.getShort() & 0xFFFF;
        int unitId = buf.get() & 0xFF;

        if (protocolId != MODBUS_PROTOCOL_ID) {
            throw new ModbusException("Invalid Modbus protocol ID: " + protocolId);
        }

        return new MbapHeader(transactionId, protocolId, length, unitId);
    }
}
