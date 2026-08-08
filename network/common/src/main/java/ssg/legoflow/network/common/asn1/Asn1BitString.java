package ssg.legoflow.network.common.asn1;

import java.util.Arrays;

/**
 * ASN.1 BIT STRING type (universal tag 0x03).
 *
 * <p>A bit string is encoded with a leading byte indicating the number of unused
 * bits (0-7) in the last byte of data. In DER, the unused bits must be zero.
 *
 * @param unusedBits the number of unused bits in the last byte (0-7)
 * @param data       the bit string data bytes
 * @since 0.1.0
 */
public record Asn1BitString(int unusedBits, byte[] data) implements Asn1Type {

    /**
     * Creates an ASN.1 BIT STRING with validation.
     *
     * @param unusedBits the number of unused bits (0-7)
     * @param data       the data bytes (defensively copied)
     */
    public Asn1BitString {
        if (unusedBits < 0 || unusedBits > 7) {
            throw new IllegalArgumentException("Unused bits must be 0-7: " + unusedBits);
        }
        if (data == null) {
            throw new IllegalArgumentException("Data must not be null");
        }
        if (data.length == 0 && unusedBits != 0) {
            throw new IllegalArgumentException("Unused bits must be 0 for empty data");
        }
        data = data.clone();
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.BIT_STRING;
    }

    /**
     * Returns a copy of the data bytes.
     *
     * @return copy of the data
     */
    @Override
    public byte[] data() {
        return data.clone();
    }

    /**
     * Returns the raw data bytes without copying (for internal codec use).
     *
     * @return the internal data array
     */
    byte[] rawData() {
        return data;
    }

    /**
     * Creates a BIT STRING with no unused bits.
     *
     * @param data the data bytes
     * @return the BIT STRING
     */
    public static Asn1BitString of(byte[] data) {
        return new Asn1BitString(0, data);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Asn1BitString other
                && unusedBits == other.unusedBits
                && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return 31 * unusedBits + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "Asn1BitString[unusedBits=" + unusedBits + ", data=" + Arrays.toString(data) + "]";
    }
}
