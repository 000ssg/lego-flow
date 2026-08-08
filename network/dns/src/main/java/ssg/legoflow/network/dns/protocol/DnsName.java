package ssg.legoflow.network.dns.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Represents a DNS domain name as a sequence of labels.
 *
 * <p>Domain names in DNS are case-insensitive for comparison purposes
 * (RFC 4343). This class preserves the original case but compares
 * in a case-insensitive manner.
 *
 * <p>The root domain is represented by an empty label list and the
 * string {@code "."}.
 *
 * @since 0.1.0
 */
public final class DnsName implements Comparable<DnsName> {

    /** The root domain name. */
    public static final DnsName ROOT = new DnsName(Collections.emptyList());

    private final List<String> labels;
    private final String canonical;

    private DnsName(List<String> labels) {
        this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
        this.canonical = buildCanonical(this.labels);
    }

    /**
     * Parses a domain name from its dotted string representation.
     *
     * @param name the domain name string (e.g., "www.example.com" or "www.example.com.")
     * @return the parsed domain name
     * @throws IllegalArgumentException if the name is invalid
     * @since 0.1.0
     */
    public static DnsName of(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isEmpty() || name.equals(".")) {
            return ROOT;
        }
        // Remove trailing dot if present
        if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        String[] parts = name.split("\\.");
        List<String> labels = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Empty label in domain name: " + name);
            }
            if (part.length() > 63) {
                throw new IllegalArgumentException("Label exceeds 63 bytes: " + part);
            }
            labels.add(part);
        }
        if (labels.isEmpty()) {
            return ROOT;
        }
        return new DnsName(labels);
    }

    /**
     * Creates a domain name from a list of labels.
     *
     * @param labels the labels (top-level last)
     * @return the domain name
     * @since 0.1.0
     */
    public static DnsName fromLabels(List<String> labels) {
        Objects.requireNonNull(labels, "labels must not be null");
        if (labels.isEmpty()) {
            return ROOT;
        }
        return new DnsName(labels);
    }

    /**
     * Returns the labels of this domain name.
     *
     * @return unmodifiable list of labels (leftmost first)
     * @since 0.1.0
     */
    public List<String> labels() {
        return labels;
    }

    /**
     * Returns the number of labels.
     *
     * @return the label count
     * @since 0.1.0
     */
    public int labelCount() {
        return labels.size();
    }

    /**
     * Returns whether this is the root domain.
     *
     * @return {@code true} if this is the root
     * @since 0.1.0
     */
    public boolean isRoot() {
        return labels.isEmpty();
    }

    /**
     * Returns the parent domain (removes the leftmost label).
     *
     * @return the parent domain, or {@link #ROOT} if already root
     * @since 0.1.0
     */
    public DnsName parent() {
        if (labels.size() <= 1) {
            return ROOT;
        }
        return new DnsName(labels.subList(1, labels.size()));
    }

    /**
     * Returns whether this domain name is a subdomain of the given domain.
     *
     * @param other the potential parent domain
     * @return {@code true} if this is a subdomain of {@code other}
     * @since 0.1.0
     */
    public boolean isSubdomainOf(DnsName other) {
        if (other.isRoot()) {
            return true;
        }
        if (labels.size() < other.labels.size()) {
            return false;
        }
        int offset = labels.size() - other.labels.size();
        for (int i = 0; i < other.labels.size(); i++) {
            if (!labels.get(offset + i).equalsIgnoreCase(other.labels.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this domain name matches the given wildcard pattern.
     * A wildcard is a name starting with the label {@code "*"}.
     *
     * @param wildcard the wildcard domain name
     * @return {@code true} if this name matches the wildcard
     * @since 0.1.0
     */
    public boolean matchesWildcard(DnsName wildcard) {
        if (wildcard.labels.isEmpty() || !"*".equals(wildcard.labels.get(0))) {
            return equals(wildcard);
        }
        // Wildcard matches any single label at the leftmost position
        if (labels.size() != wildcard.labels.size()) {
            return false;
        }
        for (int i = 1; i < wildcard.labels.size(); i++) {
            if (!labels.get(i).equalsIgnoreCase(wildcard.labels.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Prepends a label to this domain name.
     *
     * @param label the label to prepend
     * @return a new domain name with the label prepended
     * @since 0.1.0
     */
    public DnsName prepend(String label) {
        Objects.requireNonNull(label, "label must not be null");
        List<String> newLabels = new ArrayList<>();
        newLabels.add(label);
        newLabels.addAll(labels);
        return new DnsName(newLabels);
    }

    /**
     * Returns the wire-format length of this domain name (including the
     * terminating zero byte), without compression.
     *
     * @return the wire length in bytes
     * @since 0.1.0
     */
    public int wireLength() {
        int len = 1; // terminating zero
        for (String label : labels) {
            len += 1 + label.getBytes(StandardCharsets.US_ASCII).length;
        }
        return len;
    }

    /**
     * Converts this name to its canonical (lowercase) wire form for
     * DNSSEC canonical ordering.
     *
     * @return the canonical lowercase name string
     * @since 0.1.0
     */
    public String toCanonical() {
        return canonical;
    }

    /**
     * Returns the fully-qualified domain name string with a trailing dot.
     *
     * @return the FQDN string
     * @since 0.1.0
     */
    public String toFqdn() {
        if (labels.isEmpty()) {
            return ".";
        }
        return String.join(".", labels) + ".";
    }

    @Override
    public int compareTo(DnsName other) {
        return canonical.compareTo(other.canonical);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DnsName other)) return false;
        return canonical.equals(other.canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    @Override
    public String toString() {
        if (labels.isEmpty()) {
            return ".";
        }
        return String.join(".", labels);
    }

    private static String buildCanonical(List<String> labels) {
        if (labels.isEmpty()) {
            return ".";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(labels.get(i).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
