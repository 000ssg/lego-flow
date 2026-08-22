package ssg.legoflow.http.header;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class EntityTagTest {

    @Test
    void testParseStrongTag() {
        // When
        var tag = EntityTag.parse("\"abc123\"");

        // Then
        assertThat(tag.value()).isEqualTo("abc123");
        assertThat(tag.weak()).isFalse();
    }

    @Test
    void testParseWeakTag() {
        // When
        var tag = EntityTag.parse("W/\"abc123\"");

        // Then
        assertThat(tag.value()).isEqualTo("abc123");
        assertThat(tag.weak()).isTrue();
    }

    @Test
    void testParseWeakTagLowercase() {
        // When
        var tag = EntityTag.parse("w/\"abc123\"");

        // Then
        assertThat(tag.value()).isEqualTo("abc123");
        assertThat(tag.weak()).isTrue();
    }

    @Test
    void testStrongComparisonBothStrong() {
        // Given
        var tag1 = EntityTag.parse("\"abc\"");
        var tag2 = EntityTag.parse("\"abc\"");

        // Then
        assertThat(tag1.matches(tag2, true)).isTrue();
    }

    @Test
    void testStrongComparisonOneWeak() {
        // Given
        var strong = EntityTag.parse("\"abc\"");
        var weak = EntityTag.parse("W/\"abc\"");

        // Then
        assertThat(strong.matches(weak, true)).isFalse();
        assertThat(weak.matches(strong, true)).isFalse();
    }

    @Test
    void testWeakComparisonMatchesSameValue() {
        // Given
        var strong = EntityTag.parse("\"abc\"");
        var weak = EntityTag.parse("W/\"abc\"");

        // Then
        assertThat(strong.matches(weak, false)).isTrue();
        assertThat(weak.matches(strong, false)).isTrue();
    }

    @Test
    void testToStringStrong() {
        // Given
        var tag = new EntityTag("abc123", false);

        // Then
        assertThat(tag.toString()).isEqualTo("\"abc123\"");
    }

    @Test
    void testToStringWeak() {
        // Given
        var tag = new EntityTag("abc123", true);

        // Then
        assertThat(tag.toString()).isEqualTo("W/\"abc123\"");
    }
}
