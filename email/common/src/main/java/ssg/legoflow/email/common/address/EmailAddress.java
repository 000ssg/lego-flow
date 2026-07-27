package ssg.legoflow.email.common.address;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * RFC 5322 email address consisting of a local-part and domain.
 *
 * <p>Represents the {@code local-part@domain} portion of an email address.
 * Validation is intentionally lenient to handle real-world addresses.
 *
 * @since 1.0.0
 */
public final class EmailAddress {

    private static final Pattern BASIC_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private final String localPart;
    private final String domain;

    /**
     * Creates an email address from local part and domain.
     *
     * @param localPart the local part (before @)
     * @param domain    the domain (after @)
     * @throws IllegalArgumentException if either part is null or empty
     */
    public EmailAddress(String localPart, String domain) {
        if (localPart == null || localPart.isEmpty()) {
            throw new IllegalArgumentException("Local part must not be empty");
        }
        if (domain == null || domain.isEmpty()) {
            throw new IllegalArgumentException("Domain must not be empty");
        }
        this.localPart = localPart;
        this.domain = domain;
    }

    /**
     * Parses an email address from a string.
     *
     * @param address the address string (e.g., "user@example.com")
     * @return the parsed EmailAddress
     * @throws IllegalArgumentException if the address is invalid
     */
    public static EmailAddress parse(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address must not be empty");
        }
        String trimmed = address.trim();
        // Remove angle brackets if present
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        int at = trimmed.lastIndexOf('@');
        if (at < 1 || at >= trimmed.length() - 1) {
            throw new IllegalArgumentException("Invalid email address: " + address);
        }
        return new EmailAddress(trimmed.substring(0, at), trimmed.substring(at + 1));
    }

    /**
     * Returns the local part (before @).
     *
     * @return the local part
     */
    public String localPart() {
        return localPart;
    }

    /**
     * Returns the domain (after @).
     *
     * @return the domain
     */
    public String domain() {
        return domain;
    }

    /**
     * Returns the full address string.
     *
     * @return the address as "local-part@domain"
     */
    public String address() {
        return localPart + "@" + domain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailAddress other)) return false;
        // Local part is case-sensitive, domain is case-insensitive per RFC 5321
        return localPart.equals(other.localPart)
                && domain.equalsIgnoreCase(other.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localPart, domain.toLowerCase());
    }

    @Override
    public String toString() {
        return address();
    }
}
