package ssg.legoflow.http.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TransferEncodingTest {

    @Test
    void testChunkedValue() {
        assertThat(TransferEncoding.CHUNKED.value()).isEqualTo("chunked");
    }

    @Test
    void testIdentityValue() {
        assertThat(TransferEncoding.IDENTITY.value()).isEqualTo("identity");
    }

    @Test
    void testParseChunked() {
        assertThat(TransferEncoding.parse("chunked")).isSameAs(TransferEncoding.CHUNKED);
    }

    @Test
    void testParseChunkedCaseInsensitive() {
        assertThat(TransferEncoding.parse("CHUNKED")).isSameAs(TransferEncoding.CHUNKED);
        assertThat(TransferEncoding.parse("Chunked")).isSameAs(TransferEncoding.CHUNKED);
    }

    @Test
    void testParseIdentity() {
        assertThat(TransferEncoding.parse("identity")).isSameAs(TransferEncoding.IDENTITY);
    }

    @Test
    void testParseWithWhitespace() {
        assertThat(TransferEncoding.parse("  chunked  ")).isSameAs(TransferEncoding.CHUNKED);
    }

    @Test
    void testParseUnknownThrows() {
        assertThatThrownBy(() -> TransferEncoding.parse("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown transfer encoding");
    }

    @Test
    void testParseNullThrows() {
        assertThatThrownBy(() -> TransferEncoding.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testToString() {
        assertThat(TransferEncoding.CHUNKED.toString()).isEqualTo("chunked");
        assertThat(TransferEncoding.IDENTITY.toString()).isEqualTo("identity");
    }

    @Test
    void testValuesEnumeration() {
        assertThat(TransferEncoding.values()).hasSize(2);
    }
}
