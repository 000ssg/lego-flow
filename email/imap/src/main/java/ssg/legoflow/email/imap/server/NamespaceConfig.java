package ssg.legoflow.email.imap.server;

import java.util.List;
import java.util.Objects;

/**
 * NAMESPACE configuration per RFC 2342.
 *
 * <p>Defines personal, other users', and shared namespace prefixes
 * with their hierarchy delimiters.
 *
 * @param personal the personal namespace entries
 * @param otherUsers the other users' namespace entries
 * @param shared the shared namespace entries
 * @since 1.0.0
 */
public record NamespaceConfig(
        List<NamespaceEntry> personal,
        List<NamespaceEntry> otherUsers,
        List<NamespaceEntry> shared) {

    /**
     * A single namespace entry with prefix and delimiter.
     *
     * @param prefix    the namespace prefix (e.g., "", "#shared/")
     * @param delimiter the hierarchy delimiter (e.g., "/")
     */
    public record NamespaceEntry(String prefix, String delimiter) {
        public NamespaceEntry {
            Objects.requireNonNull(prefix);
            Objects.requireNonNull(delimiter);
        }

        /**
         * Formats this entry for the IMAP wire protocol.
         *
         * @return the formatted entry
         */
        public String toWire() {
            return "(\"" + prefix + "\" \"" + delimiter + "\")";
        }
    }

    /**
     * Creates a default namespace configuration with a single personal namespace.
     *
     * @param delimiter the hierarchy delimiter
     * @return the default configuration
     */
    public static NamespaceConfig defaultConfig(String delimiter) {
        return new NamespaceConfig(
                List.of(new NamespaceEntry("", delimiter)),
                List.of(),
                List.of());
    }

    /**
     * Formats this configuration for the IMAP NAMESPACE response.
     *
     * @return the formatted NAMESPACE response
     */
    public String toWire() {
        return formatList(personal) + " " + formatList(otherUsers) + " " + formatList(shared);
    }

    private String formatList(List<NamespaceEntry> entries) {
        if (entries.isEmpty()) return "NIL";
        StringBuilder sb = new StringBuilder("(");
        for (NamespaceEntry entry : entries) {
            sb.append(entry.toWire());
        }
        sb.append(")");
        return sb.toString();
    }
}
