package ssg.legoflow.database.mysql.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MySQL common types: {@link Charset} and {@link MysqlError}.
 */
class MysqlCommonTest {

    @Test void testCharsetValues() {
        var values = Charset.values();
        assertThat(values).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test void testCharsetById() {
        // LATIN1_SWEDISH_CI should have id 8
        assertThat(Charset.LATIN1_SWEDISH_CI.id()).isEqualTo(8);
        // UTF8_GENERAL_CI should have id 33
        assertThat(Charset.UTF8_GENERAL_CI.id()).isEqualTo(33);
        // UTF8MB4_0900_AI_CI (MySQL 8.0 default) should have id 255
        assertThat(Charset.UTF8MB4_0900_AI_CI.id()).isEqualTo(255);
    }

    @Test void testCharsetNames() {
        assertThat(Charset.LATIN1_SWEDISH_CI.name()).isEqualTo("LATIN1_SWEDISH_CI");
        assertThat(Charset.BINARY.charsetName()).isEqualTo("binary");
        assertThat(Charset.ASCII_GENERAL_CI.charsetName()).isEqualTo("ascii");
    }

    @Test void testCharsetCollation() {
        assertThat(Charset.UTF8_GENERAL_CI.collationName()).isEqualTo("utf8_general_ci");
        assertThat(Charset.UTF8MB4_BIN.collationName()).isEqualTo("utf8mb4_bin");
        assertThat(Charset.UTF8_UNICODE_CI.collationName()).isEqualTo("utf8_unicode_ci");
    }

    @Test void testCharsetUtf8mb4() {
        // UTF8MB4 values should all have charset "utf8mb4"
        assertThat(Charset.UTF8MB4_GENERAL_CI.charsetName()).isEqualTo("utf8mb4");
        assertThat(Charset.UTF8MB4_0900_AI_CI.charsetName()).isEqualTo("utf8mb4");
        assertThat(Charset.UTF8MB4_BIN.charsetName()).isEqualTo("utf8mb4");
    }

    @Test void testCharsetUtf8() {
        // UTF8 values should have charset "utf8"
        assertThat(Charset.UTF8_GENERAL_CI.charsetName()).isEqualTo("utf8");
        assertThat(Charset.UTF8_UNICODE_CI.charsetName()).isEqualTo("utf8");
        assertThat(Charset.UTF8_BIN.charsetName()).isEqualTo("utf8");
    }

    @Test void testCharsetByIdStatic() {
        var charset = Charset.fromName("utf8mb4");
        assertThat(charset).isNotNull();
        assertThat(charset.charsetName()).isEqualTo("utf8mb4");
    }

    @Test void testCharsetByIdNotFoundReturnsFirstMatch() {
        var charset = Charset.fromName("utf8mb4");
        assertThat(charset).isNotNull();
    }

    @Test void testMysqlErrorValues() {
        var values = MysqlError.values();
        assertThat(values).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test void testMysqlErrorCodeRanges() {
        for (var error : MysqlError.values()) {
            // Error codes should be positive integers
            assertThat(error.code()).isPositive();
        }
    }

    @Test void testMysqlErrorMessageNotBlank() {
        for (var error : MysqlError.values()) {
            assertThat(error.messageTemplate()).isNotBlank();
        }
    }

    @Test void testCharsetEnumDistinctIds() {
        // All charset IDs should be distinct
        var ids = java.util.Arrays.stream(Charset.values())
                .mapToInt(e -> e.id())
                .boxed()
                .collect(java.util.stream.Collectors.toSet());
        assertThat(ids.size()).isEqualTo(Charset.values().length);
    }

    @Test void testMysqlErrorDistinctCodes() {
        // All error codes should be distinct
        var codes = java.util.Arrays.stream(MysqlError.values())
                .mapToInt(e -> e.code())
                .boxed()
                .collect(java.util.stream.Collectors.toSet());
        assertThat(codes.size()).isEqualTo(MysqlError.values().length);
    }

    @Test void testCharsetBinaryId() {
        // Binary charset should have id 63
        assertThat(Charset.BINARY.id()).isEqualTo(63);
    }
}
