package ssg.legoflow.network.common.asn1;

/**
 * ASN.1 PrintableString type (universal tag 0x13).
 *
 * <p>A restricted string containing only printable characters: A-Z, a-z, 0-9,
 * space, and {@code '()+,-./:=?}.
 *
 * @param value the string value
 * @since 0.1.0
 */
public record Asn1PrintableString(String value) implements Asn1Type {

    /**
     * Creates a PrintableString with validation.
     *
     * @param value the string value (must not be null)
     */
    public Asn1PrintableString {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.PRINTABLE_STRING;
    }

    /**
     * Creates a PrintableString from a string value.
     *
     * @param value the string value
     * @return the PrintableString
     */
    public static Asn1PrintableString of(String value) {
        return new Asn1PrintableString(value);
    }
}
