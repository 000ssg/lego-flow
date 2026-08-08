package ssg.legoflow.network.common.asn1;

/**
 * ASN.1 IA5String type (universal tag 0x16).
 *
 * <p>A string of characters from the International Alphabet No. 5 (IA5),
 * which is essentially ASCII (characters 0x00-0x7F).
 *
 * @param value the string value
 * @since 0.1.0
 */
public record Asn1IA5String(String value) implements Asn1Type {

    /**
     * Creates an IA5String with validation.
     *
     * @param value the string value (must not be null)
     */
    public Asn1IA5String {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.IA5_STRING;
    }

    /**
     * Creates an IA5String from a string value.
     *
     * @param value the string value
     * @return the IA5String
     */
    public static Asn1IA5String of(String value) {
        return new Asn1IA5String(value);
    }
}
