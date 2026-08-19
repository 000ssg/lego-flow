package ssg.legoflow.http.header;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MediaTypeTest {

    @Test
    void testParseSimple() {
        // When
        var mt = MediaType.parse("text/html");

        // Then
        assertThat(mt.type()).isEqualTo("text");
        assertThat(mt.subtype()).isEqualTo("html");
        assertThat(mt.parameters()).isEmpty();
    }

    @Test
    void testParseWithParameter() {
        // When
        var mt = MediaType.parse("text/html;charset=utf-8");

        // Then
        assertThat(mt.type()).isEqualTo("text");
        assertThat(mt.subtype()).isEqualTo("html");
        assertThat(mt.parameters()).containsEntry("charset", "utf-8");
    }

    @Test
    void testMatchesExact() {
        // Given
        var mt1 = MediaType.parse("text/html");
        var mt2 = MediaType.parse("text/html");

        // Then
        assertThat(mt1.matches(mt2)).isTrue();
    }

    @Test
    void testMatchesWildcardAll() {
        // Given
        var mt1 = MediaType.parse("*/*");
        var mt2 = MediaType.parse("text/html");

        // Then
        assertThat(mt1.matches(mt2)).isTrue();
        assertThat(mt2.matches(mt1)).isTrue();
    }

    @Test
    void testMatchesWildcardSubtype() {
        // Given
        var mt1 = MediaType.parse("text/*");
        var mt2 = MediaType.parse("text/html");
        var mt3 = MediaType.parse("application/json");

        // Then
        assertThat(mt1.matches(mt2)).isTrue();
        assertThat(mt1.matches(mt3)).isFalse();
    }

    @Test
    void testMatchesDifferentTypes() {
        // Given
        var mt1 = MediaType.parse("text/html");
        var mt2 = MediaType.parse("application/json");

        // Then
        assertThat(mt1.matches(mt2)).isFalse();
    }

    @Test
    void testConstants() {
        assertThat(MediaType.TEXT_PLAIN.type()).isEqualTo("text");
        assertThat(MediaType.TEXT_PLAIN.subtype()).isEqualTo("plain");
        assertThat(MediaType.APPLICATION_JSON.type()).isEqualTo("application");
        assertThat(MediaType.APPLICATION_JSON.subtype()).isEqualTo("json");
        assertThat(MediaType.APPLICATION_OCTET_STREAM.subtype()).isEqualTo("octet-stream");
    }

    @Test
    void testToString() {
        // Given
        var mt = MediaType.parse("text/html;charset=utf-8");

        // Then
        assertThat(mt.toString()).isEqualTo("text/html;charset=utf-8");
    }
}
