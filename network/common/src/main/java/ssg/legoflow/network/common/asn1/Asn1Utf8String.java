package ssg.legoflow.network.common.asn1;

/**
 * ASN.1 UTF8String type (universal tag 0x0C).
 *
 * <p>A string encoded in UTF-8.
 *
 * @param value the string value
 * @since 1.0.0
 */
public record Asn1Utf8String(String value) implements Asn1Type {

    /**
     * Creates a UTF8String with validation.
     *
     * @param value the string value (must not be null)
     */
    public Asn1Utf8String {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.UTF8_STRING;
    }

    /**
     * Creates a UTF8String from a string value.
     *
     * @param value the string value
     * @return the UTF8String
     */
    public static Asn1Utf8String of(String value) {
        return new Asn1Utf8String(value);
    }
}
