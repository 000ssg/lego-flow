package ssg.legoflow.http.header;

import java.util.Objects;

/**
 * Represents an HTTP product token such as "Mozilla/5.0".
 */
public record ProductToken(String product, String version) {

    public ProductToken {
        Objects.requireNonNull(product, "product must not be null");
    }

    /**
     * Parses a product token string such as "Mozilla/5.0".
     */
    public static ProductToken parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip();
        int slashIndex = trimmed.indexOf('/');
        if (slashIndex >= 0) {
            String product = trimmed.substring(0, slashIndex).strip();
            String version = trimmed.substring(slashIndex + 1).strip();
            return new ProductToken(product, version);
        }
        return new ProductToken(trimmed, null);
    }

    @Override
    public String toString() {
        if (version == null || version.isEmpty()) {
            return product;
        }
        return product + "/" + version;
    }
}
