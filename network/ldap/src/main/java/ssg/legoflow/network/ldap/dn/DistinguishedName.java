package ssg.legoflow.network.ldap.dn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An LDAP Distinguished Name (DN) as defined in RFC 4514.
 *
 * <p>A DN is a sequence of Relative Distinguished Names (RDNs), ordered from
 * most specific (leftmost) to least specific (rightmost). For example:
 * {@code cn=John Doe,ou=People,dc=example,dc=com}
 *
 * <p>This class is immutable and thread-safe.
 *
 * @since 0.1.0
 */
public final class DistinguishedName {

    private final List<Rdn> rdns;

    /**
     * Creates a DN from a list of RDNs (most specific first).
     *
     * @param rdns the RDN components
     */
    public DistinguishedName(List<Rdn> rdns) {
        if (rdns == null) {
            throw new IllegalArgumentException("RDNs must not be null");
        }
        this.rdns = List.copyOf(rdns);
    }

    /**
     * Returns the RDN components (most specific first).
     *
     * @return the unmodifiable list of RDNs
     */
    public List<Rdn> rdns() {
        return rdns;
    }

    /**
     * Returns true if this DN is empty (root DSE).
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return rdns.isEmpty();
    }

    /**
     * Returns the parent DN (all RDNs except the first).
     *
     * @return the parent DN, or an empty DN if this is a single-component DN
     */
    public DistinguishedName parent() {
        if (rdns.size() <= 1) {
            return new DistinguishedName(List.of());
        }
        return new DistinguishedName(rdns.subList(1, rdns.size()));
    }

    /**
     * Returns true if this DN is a descendant of the given base DN.
     *
     * @param base the potential ancestor DN
     * @return true if this DN is under the base DN
     */
    public boolean isDescendantOf(DistinguishedName base) {
        if (base.rdns.size() >= rdns.size()) {
            return false;
        }
        int offset = rdns.size() - base.rdns.size();
        for (int i = 0; i < base.rdns.size(); i++) {
            if (!rdns.get(offset + i).equalsIgnoreCase(base.rdns.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if this DN equals or is a descendant of the given base DN.
     *
     * @param base the potential ancestor DN
     * @return true if this DN is under or equals the base DN
     */
    public boolean isUnder(DistinguishedName base) {
        return equalsIgnoreCase(base) || isDescendantOf(base);
    }

    /**
     * Returns true if this DN equals the given DN ignoring case.
     *
     * @param other the other DN
     * @return true if equal ignoring case
     */
    public boolean equalsIgnoreCase(DistinguishedName other) {
        if (rdns.size() != other.rdns.size()) return false;
        for (int i = 0; i < rdns.size(); i++) {
            if (!rdns.get(i).equalsIgnoreCase(other.rdns.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a DN string in RFC 4514 format.
     *
     * @param dnString the DN string
     * @return the parsed DN
     * @throws DnParseException if the string is invalid
     */
    public static DistinguishedName parse(String dnString) {
        return DnParser.parse(dnString);
    }

    /**
     * Returns an empty DN (root DSE).
     *
     * @return the empty DN
     */
    public static DistinguishedName empty() {
        return new DistinguishedName(List.of());
    }

    /**
     * Returns the DN in RFC 4514 string representation.
     *
     * @return the DN string
     */
    @Override
    public String toString() {
        if (rdns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rdns.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(rdns.get(i));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DistinguishedName other && rdns.equals(other.rdns);
    }

    @Override
    public int hashCode() {
        return rdns.hashCode();
    }
}
