package ssg.legoflow.http.header;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RangeUnitTest {

    @Test
    void testBytesConstant() {
        assertThat(RangeUnit.BYTES.unit()).isEqualTo("bytes");
        assertThat(RangeUnit.BYTES.toString()).isEqualTo("bytes");
    }

    @Test
    void testParseBytes() {
        assertThat(RangeUnit.parse("bytes")).isSameAs(RangeUnit.BYTES);
    }

    @Test
    void testParseBytesCaseInsensitive() {
        assertThat(RangeUnit.parse("BYTES")).isEqualTo(RangeUnit.BYTES);
        assertThat(RangeUnit.parse("Bytes")).isEqualTo(RangeUnit.BYTES);
    }

    @Test
    void testParseByteSPadding() {
        assertThat(RangeUnit.parse("  bytes  ")).isEqualTo(RangeUnit.BYTES);
    }

    @Test
    void testParseCustomUnit() {
        RangeUnit custom = RangeUnit.parse("bits");
        assertThat(custom.unit()).isEqualTo("bits");
        assertThat(custom.toString()).isEqualTo("bits");
    }

    @Test
    void testParseNullThrows() {
        assertThatThrownBy(() -> RangeUnit.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorNullThrows() {
        assertThatThrownBy(() -> new RangeUnit(null))
                .isInstanceOf(NullPointerException.class);
    }
}
