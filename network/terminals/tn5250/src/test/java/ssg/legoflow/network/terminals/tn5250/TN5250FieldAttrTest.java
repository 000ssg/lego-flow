package ssg.legoflow.network.terminals.tn5250;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TN5250 field attributes.
 */
class TN5250FieldAttrTest {

    @Test
    void testNormal() {
        assertThat(TN5250FieldAttr.NORMAL.isEditable()).isTrue();
        assertThat(TN5250FieldAttr.NORMAL.isEmphasized()).isFalse();
        assertThat(TN5250FieldAttr.NORMAL.isAutoSkip()).isFalse();
        assertThat(TN5250FieldAttr.NORMAL.isBlank()).isFalse();
    }

    @Test
    void testEmphasis() {
        assertThat(TN5250FieldAttr.EMPHASIS.isEmphasized()).isTrue();
        assertThat(TN5250FieldAttr.EMPHASIS.isEditable()).isTrue();
    }

    @Test
    void testAutoSkip() {
        assertThat(TN5250FieldAttr.AUTO_SKIP.isAutoSkip()).isTrue();
        assertThat(TN5250FieldAttr.AUTO_SKIP.isEditable()).isTrue();
    }

    @Test
    void testBlank() {
        assertThat(TN5250FieldAttr.BLANK.isBlank()).isTrue();
        assertThat(TN5250FieldAttr.BLANK.isEditable()).isFalse();
    }

    @Test
    void testEmphasisAutoSkip() {
        TN5250FieldAttr attr = TN5250FieldAttr.EMPHASIS_AUTO_SKIP;
        assertThat(attr.isEmphasized()).isTrue();
        assertThat(attr.isAutoSkip()).isTrue();
    }

    @Test
    void testEmphasisBlank() {
        TN5250FieldAttr attr = TN5250FieldAttr.EMPHASIS_BLANK;
        assertThat(attr.isEmphasized()).isTrue();
        assertThat(attr.isBlank()).isTrue();
    }

    @Test
    void testFull() {
        TN5250FieldAttr attr = TN5250FieldAttr.FULL;
        assertThat(attr.isEmphasized()).isTrue();
        assertThat(attr.isAutoSkip()).isTrue();
        assertThat(attr.isBlank()).isTrue();
    }

    @Test
    void testEncodeDecodeNormal() {
        assertThat(TN5250FieldAttr.NORMAL.encode()).isEqualTo(0x00);
        assertThat(TN5250FieldAttr.decode(0x00)).isEqualTo(TN5250FieldAttr.NORMAL);
    }

    @Test
    void testEncodeDecodeEmphasis() {
        assertThat(TN5250FieldAttr.EMPHASIS.encode()).isEqualTo(0x01);
        assertThat(TN5250FieldAttr.decode(0x01)).isEqualTo(TN5250FieldAttr.EMPHASIS);
    }

    @Test
    void testEncodeDecodeAutoSkip() {
        assertThat(TN5250FieldAttr.AUTO_SKIP.encode()).isEqualTo(0x02);
        assertThat(TN5250FieldAttr.decode(0x02)).isEqualTo(TN5250FieldAttr.AUTO_SKIP);
    }

    @Test
    void testEncodeDecodeBlank() {
        assertThat(TN5250FieldAttr.BLANK.encode()).isEqualTo(0x04);
        assertThat(TN5250FieldAttr.decode(0x04)).isEqualTo(TN5250FieldAttr.BLANK);
    }

    @Test
    void testEncodeDecodeFull() {
        assertThat(TN5250FieldAttr.FULL.encode()).isEqualTo(0x07);
        assertThat(TN5250FieldAttr.decode(0x07)).isEqualTo(TN5250FieldAttr.FULL);
    }

    @Test
    void testEncodeDecodeEmphasisAutoSkip() {
        assertThat(TN5250FieldAttr.EMPHASIS_AUTO_SKIP.encode()).isEqualTo(0x03);
        assertThat(TN5250FieldAttr.decode(0x03))
            .isEqualTo(TN5250FieldAttr.EMPHASIS_AUTO_SKIP);
    }

    @Test
    void testToString() {
        String s = TN5250FieldAttr.BLANK.toString();
        assertThat(s).contains("blank=true");
    }

    @Test
    void testDecodeReservedBits() {
        TN5250FieldAttr attr = TN5250FieldAttr.decode(0x00);
        assertThat(attr).isEqualTo(TN5250FieldAttr.NORMAL);
    }
}
