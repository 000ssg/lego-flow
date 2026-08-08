package ssg.legoflow.network.common.asn1;

import ssg.legoflow.network.common.oid.ObjectIdentifier;

/**
 * ASN.1 OBJECT IDENTIFIER type (universal tag 0x06).
 *
 * <p>Represents an OID as a sequence of non-negative integer arcs, e.g. {@code 1.3.6.1.2.1.1}.
 * The first two arcs are combined in the encoding as {@code 40 * arc1 + arc2}.
 *
 * @param oid the object identifier value
 * @since 0.1.0
 */
public record Asn1ObjectIdentifier(ObjectIdentifier oid) implements Asn1Type {

    /**
     * Creates an ASN.1 OBJECT IDENTIFIER with validation.
     *
     * @param oid the OID value (must not be null)
     */
    public Asn1ObjectIdentifier {
        if (oid == null) {
            throw new IllegalArgumentException("OID must not be null");
        }
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.OBJECT_IDENTIFIER;
    }

    /**
     * Creates an OBJECT IDENTIFIER from a dotted string.
     *
     * @param dotted the dotted string representation (e.g. "1.3.6.1.2.1.1")
     * @return the OBJECT IDENTIFIER
     */
    public static Asn1ObjectIdentifier of(String dotted) {
        return new Asn1ObjectIdentifier(ObjectIdentifier.parse(dotted));
    }
}
