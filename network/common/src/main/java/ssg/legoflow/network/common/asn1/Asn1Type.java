package ssg.legoflow.network.common.asn1;

/**
 * Sealed interface for all ASN.1 types supported by the BER/DER codec.
 *
 * <p>Each permitted implementation represents a specific ASN.1 type with its
 * associated tag and value. The sealed hierarchy enables exhaustive pattern
 * matching in {@code switch} expressions.
 *
 * @since 0.1.0
 */
public sealed interface Asn1Type
        permits Asn1Boolean, Asn1Integer, Asn1BitString, Asn1OctetString,
                Asn1Null, Asn1ObjectIdentifier, Asn1Enumerated,
                Asn1Utf8String, Asn1PrintableString, Asn1IA5String,
                Asn1GeneralizedTime, Asn1Sequence, Asn1Set,
                Asn1ContextSpecific {

    /**
     * Returns the ASN.1 tag for this type.
     *
     * @return the tag identifying this type
     */
    Asn1Tag tag();
}
