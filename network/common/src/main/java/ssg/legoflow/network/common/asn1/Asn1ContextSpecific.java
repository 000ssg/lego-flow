package ssg.legoflow.network.common.asn1;

import java.util.Arrays;
/**
 * ASN.1 context-specific tagged value ({@code [0]}, {@code [1]}, etc.).
 *
 * <p>Context-specific tags are used within structured types to distinguish
 * between different optional or alternative fields. They can be either:
 * <ul>
 *   <li><b>Explicit</b> (constructed): wraps another ASN.1 type in an outer TLV</li>
 *   <li><b>Implicit</b> (primitive): replaces the inner type's tag with the context tag</li>
 * </ul>
 *
 * @param tagNumber   the context-specific tag number (0, 1, 2, ...)
 * @param constructed whether this uses constructed (explicit) encoding
 * @param value       the inner value (for constructed) or raw bytes (for primitive)
 * @param rawBytes    the raw byte content (for implicit/primitive encoding)
 * @since 0.1.0
 */
public record Asn1ContextSpecific(
        int tagNumber,
        boolean constructed,
        Asn1Type value,
        byte[] rawBytes
) implements Asn1Type {

    /**
     * Creates a context-specific tagged value.
     *
     * @param tagNumber   the tag number
     * @param constructed whether this uses constructed encoding
     * @param value       the inner ASN.1 value (may be null for primitive)
     * @param rawBytes    the raw byte content (may be null for constructed)
     */
    public Asn1ContextSpecific {
        if (tagNumber < 0) {
            throw new IllegalArgumentException("Tag number must be non-negative: " + tagNumber);
        }
        if (rawBytes != null) {
            rawBytes = rawBytes.clone();
        }
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.contextSpecific(tagNumber, constructed);
    }

    /**
     * Returns a copy of the raw bytes.
     *
     * @return copy of the raw bytes, or null if this is a constructed encoding
     */
    @Override
    public byte[] rawBytes() {
        return rawBytes != null ? rawBytes.clone() : null;
    }

    /**
     * Returns the raw bytes without copying (for internal codec use).
     *
     * @return the internal byte array
     */
    byte[] rawBytesInternal() {
        return rawBytes;
    }

    /**
     * Creates an explicit (constructed) context-specific tagged value.
     *
     * @param tagNumber the tag number
     * @param value     the inner ASN.1 value
     * @return the context-specific tagged value
     */
    public static Asn1ContextSpecific explicit(int tagNumber, Asn1Type value) {
        return new Asn1ContextSpecific(tagNumber, true, value, null);
    }

    /**
     * Creates an implicit (primitive) context-specific tagged value with raw bytes.
     *
     * @param tagNumber the tag number
     * @param rawBytes  the raw byte content
     * @return the context-specific tagged value
     */
    public static Asn1ContextSpecific implicit(int tagNumber, byte[] rawBytes) {
        return new Asn1ContextSpecific(tagNumber, false, null, rawBytes);
    }

    /**
     * Creates an implicit (constructed) context-specific tagged value wrapping elements.
     *
     * @param tagNumber the tag number
     * @param value     the inner ASN.1 value (will be encoded as constructed)
     * @return the context-specific tagged value
     */
    public static Asn1ContextSpecific implicitConstructed(int tagNumber, Asn1Type value) {
        return new Asn1ContextSpecific(tagNumber, true, value, null);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Asn1ContextSpecific other
                && tagNumber == other.tagNumber
                && constructed == other.constructed
                && java.util.Objects.equals(value, other.value)
                && Arrays.equals(rawBytes, other.rawBytes);
    }

    @Override
    public int hashCode() {
        int h = 31 * tagNumber + Boolean.hashCode(constructed);
        h = 31 * h + java.util.Objects.hashCode(value);
        h = 31 * h + Arrays.hashCode(rawBytes);
        return h;
    }

    @Override
    public String toString() {
        if (constructed && value != null) {
            return "Asn1ContextSpecific[" + tagNumber + ", EXPLICIT, " + value + "]";
        } else {
            return "Asn1ContextSpecific[" + tagNumber + ", IMPLICIT, " +
                    (rawBytes != null ? rawBytes.length + " bytes" : "null") + "]";
        }
    }
}
