package ssg.legoflow.network.common.asn1;

import java.util.ArrayList;
import java.util.List;
/**
 * ASN.1 SEQUENCE type (universal tag 0x30, constructed).
 *
 * <p>An ordered collection of ASN.1 elements. Encoded as the concatenation
 * of the BER-encoded elements.
 *
 * @param elements the ordered list of elements
 * @since 0.1.0
 */
public record Asn1Sequence(List<Asn1Type> elements) implements Asn1Type {

    /**
     * Creates a SEQUENCE with a defensive copy of the element list.
     *
     * @param elements the elements (must not be null)
     */
    public Asn1Sequence {
        if (elements == null) {
            throw new IllegalArgumentException("Elements must not be null");
        }
        elements = List.copyOf(elements);
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.SEQUENCE;
    }

    /**
     * Creates an empty SEQUENCE.
     *
     * @return the empty SEQUENCE
     */
    public static Asn1Sequence empty() {
        return new Asn1Sequence(List.of());
    }

    /**
     * Creates a SEQUENCE from the given elements.
     *
     * @param elements the elements
     * @return the SEQUENCE
     */
    public static Asn1Sequence of(Asn1Type... elements) {
        return new Asn1Sequence(List.of(elements));
    }

    /**
     * Creates a SEQUENCE from a list of elements.
     *
     * @param elements the elements
     * @return the SEQUENCE
     */
    public static Asn1Sequence of(List<Asn1Type> elements) {
        return new Asn1Sequence(elements);
    }

    /**
     * Returns a new builder for constructing a SEQUENCE.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing SEQUENCE elements incrementally.
     */
    public static final class Builder {
        private final List<Asn1Type> elements = new ArrayList<>();

        private Builder() {}

        /**
         * Adds an element to the sequence.
         *
         * @param element the element to add
         * @return this builder
         */
        public Builder add(Asn1Type element) {
            elements.add(element);
            return this;
        }

        /**
         * Builds the SEQUENCE.
         *
         * @return the constructed SEQUENCE
         */
        public Asn1Sequence build() {
            return new Asn1Sequence(elements);
        }
    }
}
