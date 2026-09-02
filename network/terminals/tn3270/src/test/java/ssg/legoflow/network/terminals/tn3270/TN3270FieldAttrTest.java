package ssg.legoflow.network.terminals.tn3270;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for TN3270 field attributes.
 */
class TN3270FieldAttrTest {

    @Test
    void testNormal() {
        assertThat(TN3270FieldAttr.NORMAL.primary()).isEqualTo(0x00);
        assertThat(TN3270FieldAttr.NORMAL.secondary()).isEqualTo(0x00);
        assertThat(TN3270FieldAttr.NORMAL.label()).isEqualTo("normal");
        assertThat(TN3270FieldAttr.NORMAL.isEditable()).isTrue();
        assertThat(TN3270FieldAttr.NORMAL.isProtected()).isFalse();
    }

    @Test
    void testReadOnly() {
        assertThat(TN3270FieldAttr.READ_ONLY.primary()).isEqualTo(0x01);
        assertThat(TN3270FieldAttr.READ_ONLY.isEditable()).isFalse();
        assertThat(TN3270FieldAttr.READ_ONLY.isProtected()).isTrue();
    }

    @Test
    void testProtectedEqualsReadOnly() {
        assertThat(TN3270FieldAttr.PROTECTED).isSameAs(TN3270FieldAttr.READ_ONLY);
    }

    @Test
    void testBold() {
        assertThat(TN3270FieldAttr.BOLD.isBold()).isTrue();
        assertThat(TN3270FieldAttr.BOLD.primary()).isEqualTo(0x02);
    }

    @Test
    void testUnderline() {
        assertThat(TN3270FieldAttr.UNDERLINE.isUnderlined()).isTrue();
        assertThat(TN3270FieldAttr.UNDERLINE.primary()).isEqualTo(0x04);
    }

    @Test
    void testReverse() {
        assertThat(TN3270FieldAttr.REVERSE.isReversed()).isTrue();
        assertThat(TN3270FieldAttr.REVERSE.primary()).isEqualTo(0x08);
    }

    @Test
    void testDark() {
        assertThat(TN3270FieldAttr.DARK.isBold()).isFalse();
        assertThat(TN3270FieldAttr.DARK.primary()).isEqualTo(0x10);
    }

    @Test
    void testFlash() {
        assertThat(TN3270FieldAttr.FLASH.isFlashing()).isTrue();
        assertThat(TN3270FieldAttr.FLASH.primary()).isEqualTo(0x20);
    }

    @Test
    void testHole() {
        assertThat(TN3270FieldAttr.HOLE.primary()).isEqualTo(0x40);
    }

    @Test
    void testSecondaryAttributes() {
        assertThat(TN3270FieldAttr.NORMAL_SECONDARY.secondary()).isEqualTo(0x01);
        assertThat(TN3270FieldAttr.NOT_EMPHASIZED.secondary()).isEqualTo(0x02);
        assertThat(TN3270FieldAttr.ITALIC.secondary()).isEqualTo(0x03);
    }

    @Test
    void testBackgroundColors() {
        assertThat(TN3270FieldAttr.NORMAL_BG.secondary()).isEqualTo(0x10);
        assertThat(TN3270FieldAttr.BLUE_BG.secondary()).isEqualTo(0x11);
        assertThat(TN3270FieldAttr.PURPLE_BG.secondary()).isEqualTo(0x12);
        assertThat(TN3270FieldAttr.GREEN_BG.secondary()).isEqualTo(0x13);
        assertThat(TN3270FieldAttr.CYAN_BG.secondary()).isEqualTo(0x14);
        assertThat(TN3270FieldAttr.RED_BG.secondary()).isEqualTo(0x15);
        assertThat(TN3270FieldAttr.YELLOW_BG.secondary()).isEqualTo(0x16);
        assertThat(TN3270FieldAttr.WHITE_BG.secondary()).isEqualTo(0x17);
    }

    @Test
    void testWithSecondary() {
        TN3270FieldAttr combined = TN3270FieldAttr.BOLD.withSecondary(0x11);
        assertThat(combined.primary()).isEqualTo(0x02);
        assertThat(combined.secondary()).isEqualTo(0x11);
        assertThat(combined.label()).contains("bold");
    }

    @Test
    void testBoldNotProtected() {
        assertThat(TN3270FieldAttr.BOLD.isEditable()).isTrue();
    }

    @Test
    void testToString() {
        String s = TN3270FieldAttr.READ_ONLY.toString();
        assertThat(s).contains("0x01");
        assertThat(s).contains("readOnly");
    }

    @Test
    void testNonBoldFields() {
        assertThat(TN3270FieldAttr.NORMAL.isBold()).isFalse();
        assertThat(TN3270FieldAttr.UNDERLINE.isBold()).isFalse();
    }

    @Test
    void testNonUnderlineFields() {
        assertThat(TN3270FieldAttr.NORMAL.isUnderlined()).isFalse();
        assertThat(TN3270FieldAttr.BOLD.isUnderlined()).isFalse();
    }

    @Test
    void testNonReverseFields() {
        assertThat(TN3270FieldAttr.NORMAL.isReversed()).isFalse();
        assertThat(TN3270FieldAttr.BOLD.isReversed()).isFalse();
    }

    @Test
    void testNonFlashingFields() {
        assertThat(TN3270FieldAttr.NORMAL.isFlashing()).isFalse();
        assertThat(TN3270FieldAttr.READ_ONLY.isFlashing()).isFalse();
    }
}
