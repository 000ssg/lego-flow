package ssg.legoflow.ftp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class FtpTransferModeTest {

    @ParameterizedTest
    @EnumSource(FtpTransferMode.class)
    void testCode(FtpTransferMode mode) {
        String code = mode.code();
        assertThat(code).isNotNull().hasSize(1);
        
        // Verify round-trip: fromCode(mode.code()) == mode
        assertThat(FtpTransferMode.fromCode(code)).isSameAs(mode);
    }

    @Test
    void testStreamMode() {
        assertThat(FtpTransferMode.STREAM.code()).isEqualTo("S");
        assertThat(FtpTransferMode.fromCode("S")).isSameAs(FtpTransferMode.STREAM);
        assertThat(FtpTransferMode.fromCode("s")).isSameAs(FtpTransferMode.STREAM);
    }

    @Test
    void testBlockMode() {
        assertThat(FtpTransferMode.BLOCK.code()).isEqualTo("B");
        assertThat(FtpTransferMode.fromCode("B")).isSameAs(FtpTransferMode.BLOCK);
        assertThat(FtpTransferMode.fromCode("b")).isSameAs(FtpTransferMode.BLOCK);
    }

    @Test
    void testCompressedMode() {
        assertThat(FtpTransferMode.COMPRESSED.code()).isEqualTo("C");
        assertThat(FtpTransferMode.fromCode("C")).isSameAs(FtpTransferMode.COMPRESSED);
        assertThat(FtpTransferMode.fromCode("c")).isSameAs(FtpTransferMode.COMPRESSED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t", "\n"})
    void testFromCodeRejectsInvalidInput(String input) {
        assertThatThrownBy(() -> FtpTransferMode.fromCode(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void testFromCodeWithWhitespace() {
        assertThat(FtpTransferMode.fromCode(" S ")).isSameAs(FtpTransferMode.STREAM);
        assertThat(FtpTransferMode.fromCode(" B ")).isSameAs(FtpTransferMode.BLOCK);
        assertThat(FtpTransferMode.fromCode(" C ")).isSameAs(FtpTransferMode.COMPRESSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "A", "Z", "SS", "BLOCK", "stream"})
    void testFromCodeUnknown(String code) {
        assertThatThrownBy(() -> FtpTransferMode.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown transfer mode code");
    }

    @Test
    void testValues() {
        FtpTransferMode[] values = FtpTransferMode.values();
        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(
                FtpTransferMode.STREAM,
                FtpTransferMode.BLOCK,
                FtpTransferMode.COMPRESSED
        );
    }

    @Test
    void testValueOf() {
        assertThat(FtpTransferMode.valueOf("STREAM")).isSameAs(FtpTransferMode.STREAM);
        assertThat(FtpTransferMode.valueOf("BLOCK")).isSameAs(FtpTransferMode.BLOCK);
        assertThat(FtpTransferMode.valueOf("COMPRESSED")).isSameAs(FtpTransferMode.COMPRESSED);
    }
}
