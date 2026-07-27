package ssg.legoflow.network.common.asn1;

/**
 * ASN.1 ENUMERATED type (universal tag 0x0A).
 *
 * <p>Encoded identically to INTEGER but semantically represents a value from
 * a defined set of named values.
 *
 * @param value the enumerated integer value
 * @since 1.0.0
 */
public record Asn1Enumerated(int value) implements Asn1Type {

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.ENUMERATED;
    }

    /**
     * Creates an ENUMERATED from an integer value.
     *
     * @param value the enumerated value
     * @return the ENUMERATED
     */
    public static Asn1Enumerated of(int value) {
        return new Asn1Enumerated(value);
    }
}
