package ssg.legoflow.ftp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class FtpStructureTest {

    @ParameterizedTest
    @EnumSource(FtpStructure.class)
    void testCode(FtpStructure structure) {
        String code = structure.code();
        assertThat(code).isNotNull().hasSize(1);
        
        // Verify round-trip: fromCode(structure.code()) == structure
        assertThat(FtpStructure.fromCode(code)).isSameAs(structure);
    }

    @Test
    void testFileStructure() {
        assertThat(FtpStructure.FILE.code()).isEqualTo("F");
        assertThat(FtpStructure.fromCode("F")).isSameAs(FtpStructure.FILE);
        assertThat(FtpStructure.fromCode("f")).isSameAs(FtpStructure.FILE);
    }

    @Test
    void testRecordStructure() {
        assertThat(FtpStructure.RECORD.code()).isEqualTo("R");
        assertThat(FtpStructure.fromCode("R")).isSameAs(FtpStructure.RECORD);
        assertThat(FtpStructure.fromCode("r")).isSameAs(FtpStructure.RECORD);
    }

    @Test
    void testPageStructure() {
        assertThat(FtpStructure.PAGE.code()).isEqualTo("P");
        assertThat(FtpStructure.fromCode("P")).isSameAs(FtpStructure.PAGE);
        assertThat(FtpStructure.fromCode("p")).isSameAs(FtpStructure.PAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t", "\n"})
    void testFromCodeRejectsInvalidInput(String input) {
        assertThatThrownBy(() -> FtpStructure.fromCode(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void testFromCodeWithWhitespace() {
        assertThat(FtpStructure.fromCode(" F ")).isSameAs(FtpStructure.FILE);
        assertThat(FtpStructure.fromCode(" R ")).isSameAs(FtpStructure.RECORD);
        assertThat(FtpStructure.fromCode(" P ")).isSameAs(FtpStructure.PAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "A", "Z", "FF", "FILE", "file"})
    void testFromCodeUnknown(String code) {
        assertThatThrownBy(() -> FtpStructure.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown structure code");
    }

    @Test
    void testValues() {
        FtpStructure[] values = FtpStructure.values();
        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(
                FtpStructure.FILE,
                FtpStructure.RECORD,
                FtpStructure.PAGE
        );
    }

    @Test
    void testValueOf() {
        assertThat(FtpStructure.valueOf("FILE")).isSameAs(FtpStructure.FILE);
        assertThat(FtpStructure.valueOf("RECORD")).isSameAs(FtpStructure.RECORD);
        assertThat(FtpStructure.valueOf("PAGE")).isSameAs(FtpStructure.PAGE);
    }
}
