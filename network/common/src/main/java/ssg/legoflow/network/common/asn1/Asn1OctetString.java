package ssg.legoflow.network.common.asn1;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * ASN.1 OCTET STRING type (universal tag 0x04).
 *
 * <p>Represents an arbitrary sequence of bytes. Often used to carry
 * encoded strings, binary data, or nested TLV structures.
 *
 * @param value the raw byte data
 * @since 0.1.0
 */
public record Asn1OctetString(byte[] value) implements Asn1Type {

    /**
     * Creates an ASN.1 OCTET STRING with defensive copy.
     *
     * @param value the byte data (must not be null)
     */
    public Asn1OctetString {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        value = value.clone();
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.OCTET_STRING;
    }

    /**
     * Returns a copy of the byte data.
     *
     * @return copy of the value
     */
    @Override
    public byte[] value() {
        return value.clone();
    }

    /**
     * Returns the raw bytes without copying (for internal codec use).
     *
     * @return the internal byte array
     */
    byte[] rawValue() {
        return value;
    }

    /**
     * Creates an OCTET STRING from a UTF-8 string.
     *
     * @param text the string to encode
     * @return the OCTET STRING
     */
    public static Asn1OctetString of(String text) {
        return new Asn1OctetString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates an OCTET STRING from raw bytes.
     *
     * @param data the byte data
     * @return the OCTET STRING
     */
    public static Asn1OctetString of(byte[] data) {
        return new Asn1OctetString(data);
    }

    /**
     * Interprets this OCTET STRING as a UTF-8 string.
     *
     * @return the string value
     */
    public String asString() {
        return new String(value, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Asn1OctetString other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "Asn1OctetString[length=" + value.length + "]";
    }
}
