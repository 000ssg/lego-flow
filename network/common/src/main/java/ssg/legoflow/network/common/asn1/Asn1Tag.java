package ssg.legoflow.network.common.asn1;

/**
 * ASN.1 tag consisting of a tag class, constructed flag, and tag number.
 *
 * <p>The tag identifies the type of an ASN.1 value in the BER/DER encoding.
 * Tags are encoded as one or more bytes in the TLV (Tag-Length-Value) structure.
 *
 * @param tagClass    the tag class (UNIVERSAL, APPLICATION, CONTEXT_SPECIFIC, PRIVATE)
 * @param constructed whether this is a constructed (vs primitive) encoding
 * @param number      the tag number (0-30 for short form, 31+ for long form)
 * @since 0.1.0
 */
public record Asn1Tag(TagClass tagClass, boolean constructed, int number) {

    /**
     * ASN.1 tag classes as defined in X.680.
     */
    public enum TagClass {
        /** Universal class (0) — types defined in X.680. */
        UNIVERSAL(0),
        /** Application class (1) — types specific to an application. */
        APPLICATION(1),
        /** Context-specific class (2) — types within a structured type. */
        CONTEXT_SPECIFIC(2),
        /** Private class (3) — privately defined types. */
        PRIVATE(3);

        private final int value;

        TagClass(int value) {
            this.value = value;
        }

        /**
         * Returns the two-bit class value (0-3).
         *
         * @return the class value
         */
        public int value() {
            return value;
        }

        /**
         * Returns the tag class for the given two-bit value.
         *
         * @param value the class value (0-3)
         * @return the corresponding tag class
         * @throws IllegalArgumentException if the value is not 0-3
         */
        public static TagClass of(int value) {
            return switch (value) {
                case 0 -> UNIVERSAL;
                case 1 -> APPLICATION;
                case 2 -> CONTEXT_SPECIFIC;
                case 3 -> PRIVATE;
                default -> throw new IllegalArgumentException("Invalid tag class: " + value);
            };
        }
    }

    /**
     * Creates a tag with validation.
     *
     * @param tagClass    the tag class
     * @param constructed whether this is a constructed encoding
     * @param number      the tag number (must be non-negative)
     */
    public Asn1Tag {
        if (tagClass == null) {
            throw new IllegalArgumentException("Tag class must not be null");
        }
        if (number < 0) {
            throw new IllegalArgumentException("Tag number must be non-negative: " + number);
        }
    }

    /**
     * Creates a UNIVERSAL primitive tag with the given number.
     *
     * @param number the tag number
     * @return the tag
     */
    public static Asn1Tag universal(int number) {
        return new Asn1Tag(TagClass.UNIVERSAL, false, number);
    }

    /**
     * Creates a UNIVERSAL constructed tag with the given number.
     *
     * @param number the tag number
     * @return the tag
     */
    public static Asn1Tag universalConstructed(int number) {
        return new Asn1Tag(TagClass.UNIVERSAL, true, number);
    }

    /**
     * Creates a CONTEXT_SPECIFIC tag with the given number.
     *
     * @param number      the tag number
     * @param constructed whether this is a constructed encoding
     * @return the tag
     */
    public static Asn1Tag contextSpecific(int number, boolean constructed) {
        return new Asn1Tag(TagClass.CONTEXT_SPECIFIC, constructed, number);
    }

    /**
     * Encodes this tag's first byte value (for short-form tags with number &lt; 31).
     *
     * @return the single-byte tag encoding
     */
    public int firstByte() {
        int b = (tagClass.value() << 6);
        if (constructed) {
            b |= 0x20;
        }
        if (number >= 31) {
            b |= 0x1F;
        } else {
            b |= number;
        }
        return b;
    }

    // Well-known universal tags
    /** BOOLEAN tag (0x01). */
    public static final Asn1Tag BOOLEAN = universal(0x01);
    /** INTEGER tag (0x02). */
    public static final Asn1Tag INTEGER = universal(0x02);
    /** BIT STRING tag (0x03). */
    public static final Asn1Tag BIT_STRING = universal(0x03);
    /** OCTET STRING tag (0x04). */
    public static final Asn1Tag OCTET_STRING = universal(0x04);
    /** NULL tag (0x05). */
    public static final Asn1Tag NULL = universal(0x05);
    /** OBJECT IDENTIFIER tag (0x06). */
    public static final Asn1Tag OBJECT_IDENTIFIER = universal(0x06);
    /** ENUMERATED tag (0x0A). */
    public static final Asn1Tag ENUMERATED = universal(0x0A);
    /** UTF8String tag (0x0C). */
    public static final Asn1Tag UTF8_STRING = universal(0x0C);
    /** PrintableString tag (0x13). */
    public static final Asn1Tag PRINTABLE_STRING = universal(0x13);
    /** IA5String tag (0x16). */
    public static final Asn1Tag IA5_STRING = universal(0x16);
    /** GeneralizedTime tag (0x18). */
    public static final Asn1Tag GENERALIZED_TIME = universal(0x18);
    /** SEQUENCE tag (0x30 = constructed). */
    public static final Asn1Tag SEQUENCE = universalConstructed(0x10);
    /** SET tag (0x31 = constructed). */
    public static final Asn1Tag SET = universalConstructed(0x11);
}
