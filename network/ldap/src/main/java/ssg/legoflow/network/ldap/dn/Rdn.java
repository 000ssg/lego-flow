package ssg.legoflow.network.ldap.dn;

import java.util.List;

/**
 * A Relative Distinguished Name (RDN) consisting of one or more
 * attribute type-value pairs.
 *
 * <p>Multi-valued RDNs are separated by '+' in the string representation.
 * For example: {@code cn=John Doe+uid=jdoe}
 *
 * <p>This class is immutable and thread-safe.
 *
 * @param components the attribute type-value pairs
 * @since 1.0.0
 */
public record Rdn(List<RdnComponent> components) {

    /**
     * Creates an RDN with validation.
     */
    public Rdn {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("RDN must have at least one component");
        }
        components = List.copyOf(components);
    }

    /**
     * Creates a single-valued RDN.
     *
     * @param type  the attribute type
     * @param value the attribute value
     * @return the RDN
     */
    public static Rdn of(String type, String value) {
        return new Rdn(List.of(new RdnComponent(type, value)));
    }

    /**
     * Returns the first component's type.
     *
     * @return the type
     */
    public String type() {
        return components.getFirst().type();
    }

    /**
     * Returns the first component's value.
     *
     * @return the value
     */
    public String value() {
        return components.getFirst().value();
    }

    /**
     * Case-insensitive comparison.
     *
     * @param other the other RDN
     * @return true if equal ignoring case
     */
    public boolean equalsIgnoreCase(Rdn other) {
        if (components.size() != other.components.size()) return false;
        for (int i = 0; i < components.size(); i++) {
            RdnComponent a = components.get(i);
            RdnComponent b = other.components.get(i);
            if (!a.type().equalsIgnoreCase(b.type()) ||
                !a.value().equalsIgnoreCase(b.value())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) sb.append('+');
            RdnComponent c = components.get(i);
            sb.append(c.type()).append('=').append(DnParser.escapeValue(c.value()));
        }
        return sb.toString();
    }

    /**
     * An attribute type-value pair within an RDN.
     *
     * @param type  the attribute type (e.g. "cn", "dc")
     * @param value the attribute value
     * @since 1.0.0
     */
    public record RdnComponent(String type, String value) {
        /** Creates a component with validation. */
        public RdnComponent {
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("RDN type must not be null or empty");
            }
            if (value == null) {
                throw new IllegalArgumentException("RDN value must not be null");
            }
        }
    }
}
