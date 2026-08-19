package ssg.legoflow.http.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HstsPolicyTest {

    @Test
    void testDefaultValues() {
        // Given
        var policy = new HstsPolicy();

        // Then
        assertThat(policy.getMaxAge()).isEqualTo(31536000L);
        assertThat(policy.isIncludeSubDomains()).isTrue();
        assertThat(policy.isPreload()).isFalse();
    }

    @Test
    void testCustomValues() {
        // Given
        var policy = new HstsPolicy(86400, false, true);

        // Then
        assertThat(policy.getMaxAge()).isEqualTo(86400L);
        assertThat(policy.isIncludeSubDomains()).isFalse();
        assertThat(policy.isPreload()).isTrue();
    }

    @Test
    void testToHeaderValueDefault() {
        // Given
        var policy = new HstsPolicy();

        // When
        String header = policy.toHeaderValue();

        // Then
        assertThat(header).contains("max-age=31536000");
        assertThat(header).contains("includeSubDomains");
        assertThat(header).doesNotContain("preload");
    }

    @Test
    void testToHeaderValueWithPreload() {
        // Given
        var policy = new HstsPolicy(31536000, true, true);

        // When
        String header = policy.toHeaderValue();

        // Then
        assertThat(header).contains("max-age=31536000");
        assertThat(header).contains("includeSubDomains");
        assertThat(header).contains("preload");
    }

    @Test
    void testParseFullHeader() {
        // When
        var policy = HstsPolicy.parse("max-age=86400; includeSubDomains; preload");

        // Then
        assertThat(policy.getMaxAge()).isEqualTo(86400L);
        assertThat(policy.isIncludeSubDomains()).isTrue();
        assertThat(policy.isPreload()).isTrue();
    }

    @Test
    void testParseMaxAgeOnly() {
        // When
        var policy = HstsPolicy.parse("max-age=0");

        // Then
        assertThat(policy.getMaxAge()).isZero();
    }

    @Test
    void testParseNullReturnsDefaults() {
        // When
        var policy = HstsPolicy.parse(null);

        // Then
        assertThat(policy.getMaxAge()).isEqualTo(31536000L);
    }
}
