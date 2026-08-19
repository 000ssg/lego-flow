package ssg.legoflow.email.imap.protocol;


/**
 * IMAP SORT criteria per RFC 5256.
 *
 * <p>Specifies the field to sort by and the direction (ascending or descending).
 * Multiple sort criteria can be combined for tie-breaking.
 *
 * @since 0.1.0
 */
public record SortCriteria(SortKey key, boolean reverse) {

    /**
     * Sort keys supported by the SORT extension.
     */
    public enum SortKey {
        /** Sort by INTERNALDATE (arrival time). */
        ARRIVAL("ARRIVAL"),
        /** Sort by CC header. */
        CC("CC"),
        /** Sort by Date header (sent date). */
        DATE("DATE"),
        /** Sort by From header. */
        FROM("FROM"),
        /** Sort by message size. */
        SIZE("SIZE"),
        /** Sort by Subject header (with "Re:" stripping). */
        SUBJECT("SUBJECT"),
        /** Sort by To header. */
        TO("TO");

        private final String text;

        SortKey(String text) { this.text = text; }

        /** Returns the protocol text. */
        public String text() { return text; }

        /**
         * Parses a sort key string.
         *
         * @param text the key text
         * @return the sort key
         */
        public static SortKey parse(String text) {
            return valueOf(text.toUpperCase());
        }
    }

    /**
     * Creates an ascending sort criterion.
     *
     * @param key the sort key
     * @return the sort criterion
     */
    public static SortCriteria ascending(SortKey key) {
        return new SortCriteria(key, false);
    }

    /**
     * Creates a descending sort criterion.
     *
     * @param key the sort key
     * @return the sort criterion
     */
    public static SortCriteria descending(SortKey key) {
        return new SortCriteria(key, true);
    }

    /**
     * Formats this sort criterion for the IMAP wire protocol.
     *
     * @return the formatted string
     */
    public String toWire() {
        return reverse ? "REVERSE " + key.text() : key.text();
    }

    /**
     * IMAP THREAD algorithm identifiers.
     */
    public enum ThreadAlgorithm {
        /** ORDEREDSUBJECT: group by base subject, sort by date within groups. */
        ORDEREDSUBJECT("ORDEREDSUBJECT"),
        /** REFERENCES: thread by References and In-Reply-To headers. */
        REFERENCES("REFERENCES");

        private final String text;

        ThreadAlgorithm(String text) { this.text = text; }

        /** Returns the protocol text. */
        public String text() { return text; }

        /**
         * Parses a thread algorithm string.
         *
         * @param text the algorithm text
         * @return the thread algorithm
         */
        public static ThreadAlgorithm parse(String text) {
            return valueOf(text.toUpperCase());
        }
    }
}
