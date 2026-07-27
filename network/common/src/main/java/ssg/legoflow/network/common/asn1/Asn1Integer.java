package ssg.legoflow.network.common.asn1;

import java.math.BigInteger;

/**
 * ASN.1 INTEGER type (universal tag 0x02).
 *
 * <p>Uses {@link BigInteger} for arbitrary precision. Encoded in two's complement
 * with minimal encoding (no redundant leading 0x00 or 0xFF bytes except to
 * preserve sign).
 *
 * @param value the integer value
 * @since 1.0.0
 */
public record Asn1Integer(BigInteger value) implements Asn1Type {

    /**
     * Creates an ASN.1 INTEGER with validation.
     *
     * @param value the integer value (must not be null)
     */
    public Asn1Integer {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.INTEGER;
    }

    /**
     * Creates an ASN.1 INTEGER from a long value.
     *
     * @param value the long value
     * @return the ASN.1 INTEGER
     */
    public static Asn1Integer of(long value) {
        return new Asn1Integer(BigInteger.valueOf(value));
    }

    /**
     * Creates an ASN.1 INTEGER from a BigInteger value.
     *
     * @param value the BigInteger value
     * @return the ASN.1 INTEGER
     */
    public static Asn1Integer of(BigInteger value) {
        return new Asn1Integer(value);
    }
}
