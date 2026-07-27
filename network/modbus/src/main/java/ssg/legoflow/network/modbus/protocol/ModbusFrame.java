package ssg.legoflow.network.modbus.protocol;

import java.util.Arrays;

/**
 * A complete Modbus TCP frame consisting of an MBAP header and PDU data.
 *
 * @param header the MBAP header
 * @param pdu    the Protocol Data Unit (function code + data)
 * @since 1.0.0
 */
public record ModbusFrame(MbapHeader header, byte[] pdu) {

    /**
     * Creates a Modbus frame with defensive copy of PDU.
     */
    public ModbusFrame {
        if (header == null) {
            throw new IllegalArgumentException("Header must not be null");
        }
        if (pdu == null || pdu.length == 0) {
            throw new IllegalArgumentException("PDU must not be null or empty");
        }
        pdu = pdu.clone();
    }

    /**
     * Returns a copy of the PDU data.
     *
     * @return the PDU bytes
     */
    @Override
    public byte[] pdu() {
        return pdu.clone();
    }

    /**
     * Returns the function code from the PDU.
     *
     * @return the function code byte
     */
    public int functionCode() {
        return pdu[0] & 0xFF;
    }

    /**
     * Returns whether this frame is an exception response.
     *
     * @return true if the function code has the high bit set
     */
    public boolean isException() {
        return FunctionCode.isException(functionCode());
    }

    /**
     * Returns the PDU data (after the function code byte).
     *
     * @return the data portion
     */
    public byte[] data() {
        return Arrays.copyOfRange(pdu, 1, pdu.length);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ModbusFrame other
                && header.equals(other.header)
                && Arrays.equals(pdu, other.pdu);
    }

    @Override
    public int hashCode() {
        return 31 * header.hashCode() + Arrays.hashCode(pdu);
    }
}
