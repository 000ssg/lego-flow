package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ContentTransferEncoding}.
 */
class ContentTransferEncodingTest {

    @Test
    void testParseAllValues() {
        assertThat(ContentTransferEncoding.parse("7bit")).isEqualTo(ContentTransferEncoding.SEVEN_BIT);
        assertThat(ContentTransferEncoding.parse("8bit")).isEqualTo(ContentTransferEncoding.EIGHT_BIT);
        assertThat(ContentTransferEncoding.parse("binary")).isEqualTo(ContentTransferEncoding.BINARY);
        assertThat(ContentTransferEncoding.parse("quoted-printable"))
                .isEqualTo(ContentTransferEncoding.QUOTED_PRINTABLE);
        assertThat(ContentTransferEncoding.parse("base64")).isEqualTo(ContentTransferEncoding.BASE64);
    }

    @Test
    void testParseCaseInsensitive() {
        assertThat(ContentTransferEncoding.parse("Base64")).isEqualTo(ContentTransferEncoding.BASE64);
        assertThat(ContentTransferEncoding.parse("QUOTED-PRINTABLE"))
                .isEqualTo(ContentTransferEncoding.QUOTED_PRINTABLE);
    }

    @Test
    void testParseNullDefaults() {
        assertThat(ContentTransferEncoding.parse(null)).isEqualTo(ContentTransferEncoding.SEVEN_BIT);
    }

    @Test
    void testParseUnknownThrows() {
        assertThatThrownBy(() -> ContentTransferEncoding.parse("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testValue() {
        assertThat(ContentTransferEncoding.BASE64.value()).isEqualTo("base64");
        assertThat(ContentTransferEncoding.QUOTED_PRINTABLE.value()).isEqualTo("quoted-printable");
    }

    @Test
    void testToString() {
        assertThat(ContentTransferEncoding.SEVEN_BIT.toString()).isEqualTo("7bit");
    }
}
