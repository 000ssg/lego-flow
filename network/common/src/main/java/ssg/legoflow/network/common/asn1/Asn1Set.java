package ssg.legoflow.network.common.asn1;

import java.util.ArrayList;
import java.util.List;

/**
 * ASN.1 SET type (universal tag 0x31, constructed).
 *
 * <p>An unordered collection of ASN.1 elements. In DER encoding, elements
 * must be sorted by their encoded tag value for canonical ordering.
 *
 * @param elements the list of elements
 * @since 0.1.0
 */
public record Asn1Set(List<Asn1Type> elements) implements Asn1Type {

    /**
     * Creates a SET with a defensive copy of the element list.
     *
     * @param elements the elements (must not be null)
     */
    public Asn1Set {
        if (elements == null) {
            throw new IllegalArgumentException("Elements must not be null");
        }
        elements = List.copyOf(elements);
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.SET;
    }

    /**
     * Creates an empty SET.
     *
     * @return the empty SET
     */
    public static Asn1Set empty() {
        return new Asn1Set(List.of());
    }

    /**
     * Creates a SET from the given elements.
     *
     * @param elements the elements
     * @return the SET
     */
    public static Asn1Set of(Asn1Type... elements) {
        return new Asn1Set(List.of(elements));
    }

    /**
     * Creates a SET from a list of elements.
     *
     * @param elements the elements
     * @return the SET
     */
    public static Asn1Set of(List<Asn1Type> elements) {
        return new Asn1Set(elements);
    }

    /**
     * Returns a new builder for constructing a SET.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing SET elements incrementally.
     */
    public static final class Builder {
        private final List<Asn1Type> elements = new ArrayList<>();

        private Builder() {}

        /**
         * Adds an element to the set.
         *
         * @param element the element to add
         * @return this builder
         */
        public Builder add(Asn1Type element) {
            elements.add(element);
            return this;
        }

        /**
         * Builds the SET.
         *
         * @return the constructed SET
         */
        public Asn1Set build() {
            return new Asn1Set(elements);
        }
    }
}
