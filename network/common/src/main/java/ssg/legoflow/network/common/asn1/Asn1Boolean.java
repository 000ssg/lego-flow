package ssg.legoflow.network.common.asn1;

/**
 * ASN.1 BOOLEAN type (universal tag 0x01).
 *
 * <p>In BER, any non-zero value means true. In DER, true must be encoded as 0xFF.
 *
 * @param value the boolean value
 * @since 1.0.0
 */
public record Asn1Boolean(boolean value) implements Asn1Type {

    /** Constant TRUE instance. */
    public static final Asn1Boolean TRUE = new Asn1Boolean(true);
    /** Constant FALSE instance. */
    public static final Asn1Boolean FALSE = new Asn1Boolean(false);

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.BOOLEAN;
    }

    /**
     * Returns a cached instance for the given value.
     *
     * @param value the boolean value
     * @return the corresponding instance
     */
    public static Asn1Boolean of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
