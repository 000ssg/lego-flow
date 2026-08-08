package ssg.legoflow.network.common.asn1;

/**
 * ASN.1 NULL type (universal tag 0x05).
 *
 * <p>Represents the absence of a value. Encoded as tag 0x05 with zero-length content.
 *
 * @since 0.1.0
 */
public record Asn1Null() implements Asn1Type {

    /** Singleton NULL instance. */
    public static final Asn1Null INSTANCE = new Asn1Null();

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.NULL;
    }
}
