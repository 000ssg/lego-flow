package ssg.legoflow.http.header;

import java.util.Objects;

/**
 * Represents an HTTP language tag such as "en-US".
 */
public record LanguageTag(String primaryTag, String subTag) {

    public LanguageTag {
        Objects.requireNonNull(primaryTag, "primaryTag must not be null");
    }

    /**
     * Parses a language tag string such as "en-US".
     */
    public static LanguageTag parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip();
        int dashIndex = trimmed.indexOf('-');
        if (dashIndex >= 0) {
            String primary = trimmed.substring(0, dashIndex).strip();
            String sub = trimmed.substring(dashIndex + 1).strip();
            return new LanguageTag(primary, sub);
        }
        return new LanguageTag(trimmed, null);
    }

    /**
     * Checks whether this language tag matches the given other tag.
     * A wildcard primary tag ("*") matches any primary tag.
     */
    public boolean matches(LanguageTag other) {
        Objects.requireNonNull(other, "other must not be null");

        if ("*".equals(this.primaryTag) || "*".equals(other.primaryTag)) {
            return true;
        }
        if (!this.primaryTag.equalsIgnoreCase(other.primaryTag)) {
            return false;
        }
        if (this.subTag == null || other.subTag == null) {
            return true;
        }
        return this.subTag.equalsIgnoreCase(other.subTag);
    }

    @Override
    public String toString() {
        if (subTag == null || subTag.isEmpty()) {
            return primaryTag;
        }
        return primaryTag + "-" + subTag;
    }
}
