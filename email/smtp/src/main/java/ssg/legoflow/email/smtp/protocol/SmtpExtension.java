package ssg.legoflow.email.smtp.protocol;

import java.util.*;

/**
 * ESMTP extensions advertised in EHLO response.
 *
 * <p>Each extension has a keyword and optional parameters. Extensions are
 * parsed from EHLO response lines and used to negotiate capabilities.
 *
 * @since 0.1.0
 */
public enum SmtpExtension {

    /** Maximum message size in bytes (RFC 1870). */
    SIZE("SIZE"),
    /** 8-bit MIME transport (RFC 6152). */
    EIGHT_BIT_MIME("8BITMIME"),
    /** TLS upgrade (RFC 3207). */
    STARTTLS("STARTTLS"),
    /** SASL authentication (RFC 4954). */
    AUTH("AUTH"),
    /** Command pipelining (RFC 2920). */
    PIPELINING("PIPELINING"),
    /** BDAT chunked transfer (RFC 3030). */
    CHUNKING("CHUNKING"),
    /** Delivery Status Notifications (RFC 3461). */
    DSN("DSN"),
    /** Enhanced status codes (RFC 2034). */
    ENHANCED_STATUS_CODES("ENHANCEDSTATUSCODES"),
    /** Internationalized email addresses (RFC 6531). */
    SMTPUTF8("SMTPUTF8");

    private final String keyword;

    SmtpExtension(String keyword) {
        this.keyword = keyword;
    }

    /**
     * Returns the EHLO keyword for this extension.
     *
     * @return the keyword string
     */
    public String keyword() {
        return keyword;
    }

    /**
     * Parses an extension keyword to an enum value.
     *
     * @param keyword the keyword (case-insensitive)
     * @return the matching extension, or empty if not recognized
     */
    public static Optional<SmtpExtension> fromKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        String upper = keyword.trim().toUpperCase();
        for (SmtpExtension ext : values()) {
            if (ext.keyword.equals(upper)) {
                return Optional.of(ext);
            }
        }
        return Optional.empty();
    }

    /**
     * Parses EHLO response lines into a map of extensions with their parameters.
     *
     * <p>Each line has the format: {@code keyword [SP parameters]}. The first
     * line (domain) is skipped. Example:
     * <pre>
     *   mail.example.com
     *   SIZE 10485760
     *   8BITMIME
     *   AUTH PLAIN LOGIN CRAM-MD5
     *   STARTTLS
     *   PIPELINING
     *   ENHANCEDSTATUSCODES
     * </pre>
     *
     * @param ehloLines the EHLO response lines (first line is the domain greeting)
     * @return map of recognized extensions to their parameter strings
     */
    public static Map<SmtpExtension, String> parseEhlo(List<String> ehloLines) {
        var result = new EnumMap<SmtpExtension, String>(SmtpExtension.class);
        for (int i = 1; i < ehloLines.size(); i++) {
            String line = ehloLines.get(i).trim();
            if (line.isEmpty()) continue;

            String keyword;
            String params;
            int sp = line.indexOf(' ');
            if (sp >= 0) {
                keyword = line.substring(0, sp);
                params = line.substring(sp + 1).trim();
            } else {
                keyword = line;
                params = "";
            }

            fromKeyword(keyword).ifPresent(ext -> result.put(ext, params));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Extracts the AUTH mechanisms from EHLO parameters.
     *
     * @param authParams the AUTH extension parameters (space-separated mechanisms)
     * @return list of mechanism names (uppercase)
     */
    public static List<String> parseAuthMechanisms(String authParams) {
        if (authParams == null || authParams.isBlank()) {
            return List.of();
        }
        return List.of(authParams.trim().toUpperCase().split("\\s+"));
    }

    /**
     * Extracts the SIZE limit from EHLO parameters.
     *
     * @param sizeParams the SIZE extension parameter
     * @return the maximum message size in bytes, or 0 if not specified
     */
    public static long parseSizeLimit(String sizeParams) {
        if (sizeParams == null || sizeParams.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(sizeParams.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
