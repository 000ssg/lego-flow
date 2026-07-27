package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MultipartType}.
 */
class MultipartTypeTest {

    @Test
    void testParseAll() {
        assertThat(MultipartType.parse("mixed")).isEqualTo(MultipartType.MIXED);
        assertThat(MultipartType.parse("alternative")).isEqualTo(MultipartType.ALTERNATIVE);
        assertThat(MultipartType.parse("related")).isEqualTo(MultipartType.RELATED);
        assertThat(MultipartType.parse("digest")).isEqualTo(MultipartType.DIGEST);
        assertThat(MultipartType.parse("report")).isEqualTo(MultipartType.REPORT);
        assertThat(MultipartType.parse("signed")).isEqualTo(MultipartType.SIGNED);
    }

    @Test
    void testParseCaseInsensitive() {
        assertThat(MultipartType.parse("MIXED")).isEqualTo(MultipartType.MIXED);
        assertThat(MultipartType.parse("Alternative")).isEqualTo(MultipartType.ALTERNATIVE);
    }

    @Test
    void testParseUnknownThrows() {
        assertThatThrownBy(() -> MultipartType.parse("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTryParseReturnsNull() {
        assertThat(MultipartType.tryParse("unknown")).isNull();
    }

    @Test
    void testTryParseReturnsValue() {
        assertThat(MultipartType.tryParse("mixed")).isEqualTo(MultipartType.MIXED);
    }

    @Test
    void testContentType() {
        assertThat(MultipartType.MIXED.contentType()).isEqualTo("multipart/mixed");
        assertThat(MultipartType.ALTERNATIVE.contentType()).isEqualTo("multipart/alternative");
    }

    @Test
    void testSubtype() {
        assertThat(MultipartType.RELATED.subtype()).isEqualTo("related");
    }
}
