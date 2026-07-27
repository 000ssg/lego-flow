package ssg.legoflow.database.mysql.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ColumnType}.
 */
class ColumnTypeTest {

    @Test
    void testFromCode_allTypes() {
        for (var type : ColumnType.values()) {
            assertThat(ColumnType.fromCode(type.code())).isEqualTo(type);
        }
    }

    @Test
    void testFromCode_unknownThrows() {
        assertThatThrownBy(() -> ColumnType.fromCode(0xFF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCodes() {
        assertThat(ColumnType.TINY.code()).isEqualTo(0x01);
        assertThat(ColumnType.SHORT.code()).isEqualTo(0x02);
        assertThat(ColumnType.LONG.code()).isEqualTo(0x03);
        assertThat(ColumnType.FLOAT.code()).isEqualTo(0x04);
        assertThat(ColumnType.DOUBLE.code()).isEqualTo(0x05);
        assertThat(ColumnType.LONGLONG.code()).isEqualTo(0x08);
        assertThat(ColumnType.VARCHAR.code()).isEqualTo(0x0F);
        assertThat(ColumnType.BLOB.code()).isEqualTo(0xFC);
        assertThat(ColumnType.VAR_STRING.code()).isEqualTo(0xFD);
        assertThat(ColumnType.STRING.code()).isEqualTo(0xFE);
    }

    @Test
    void testIsNumeric() {
        assertThat(ColumnType.LONG.isNumeric()).isTrue();
        assertThat(ColumnType.LONGLONG.isNumeric()).isTrue();
        assertThat(ColumnType.DOUBLE.isNumeric()).isTrue();
        assertThat(ColumnType.FLOAT.isNumeric()).isTrue();
        assertThat(ColumnType.TINY.isNumeric()).isTrue();
        assertThat(ColumnType.VAR_STRING.isNumeric()).isFalse();
        assertThat(ColumnType.BLOB.isNumeric()).isFalse();
    }

    @Test
    void testIsString() {
        assertThat(ColumnType.VARCHAR.isString()).isTrue();
        assertThat(ColumnType.VAR_STRING.isString()).isTrue();
        assertThat(ColumnType.STRING.isString()).isTrue();
        assertThat(ColumnType.JSON.isString()).isTrue();
        assertThat(ColumnType.LONG.isString()).isFalse();
    }

    @Test
    void testIsBlob() {
        assertThat(ColumnType.BLOB.isBlob()).isTrue();
        assertThat(ColumnType.TINY_BLOB.isBlob()).isTrue();
        assertThat(ColumnType.MEDIUM_BLOB.isBlob()).isTrue();
        assertThat(ColumnType.LONG_BLOB.isBlob()).isTrue();
        assertThat(ColumnType.VARCHAR.isBlob()).isFalse();
    }

    @Test
    void testIsTemporal() {
        assertThat(ColumnType.DATE.isTemporal()).isTrue();
        assertThat(ColumnType.DATETIME.isTemporal()).isTrue();
        assertThat(ColumnType.TIMESTAMP.isTemporal()).isTrue();
        assertThat(ColumnType.TIME.isTemporal()).isTrue();
        assertThat(ColumnType.YEAR.isTemporal()).isTrue();
        assertThat(ColumnType.LONG.isTemporal()).isFalse();
    }

    @Test
    void testNull_code() {
        assertThat(ColumnType.NULL.code()).isEqualTo(0x06);
    }

    @Test
    void testSpecialTypes() {
        assertThat(ColumnType.JSON.code()).isEqualTo(0xF5);
        assertThat(ColumnType.NEWDECIMAL.code()).isEqualTo(0xF6);
        assertThat(ColumnType.ENUM.code()).isEqualTo(0xF7);
        assertThat(ColumnType.SET.code()).isEqualTo(0xF8);
    }
}
