package ssg.legoflow.network.terminals.base.display;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TermAttrTest {

    @Test
    void testDefaultAttr() {
        TermAttr attr = TermAttr.DEFAULT;
        assertThat(attr.foreground()).isEqualTo(TermAttr.WHITE);
        assertThat(attr.background()).isEqualTo(TermAttr.BLACK);
        assertThat(attr.bold()).isFalse();
        assertThat(attr.dim()).isFalse();
        assertThat(attr.italic()).isFalse();
        assertThat(attr.underline()).isEqualTo(TermAttr.UNDERLINE_NONE);
        assertThat(attr.blink()).isFalse();
        assertThat(attr.reverse()).isFalse();
        assertThat(attr.hidden()).isFalse();
        assertThat(attr.strikethrough()).isFalse();
        assertThat(attr.isDefault()).isTrue();
    }

    @Test
    void testBuilderWithStyles() {
        TermAttr attr = TermAttr.builder()
                .bold(true)
                .italic(true)
                .underline(TermAttr.UNDERLINE_DOUBLE)
                .build();
        assertThat(attr.bold()).isTrue();
        assertThat(attr.italic()).isTrue();
        assertThat(attr.underline()).isEqualTo(TermAttr.UNDERLINE_DOUBLE);
    }

    @Test
    void testBuilderWithColors() {
        TermAttr attr = TermAttr.builder()
                .foreground(TermAttr.RED)
                .background(TermAttr.BLUE)
                .build();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.BLUE);
    }

    @Test
    void testBuilderWith256Color() {
        TermAttr attr = TermAttr.builder()
                .foreground256(196)
                .background256(235)
                .build();
        assertThat(attr.fgMode()).isEqualTo(1);
        assertThat(attr.fgColor()).isEqualTo(196);
        assertThat(attr.bgMode()).isEqualTo(1);
        assertThat(attr.bgColor()).isEqualTo(235);
    }

    @Test
    void testBuilderWithTrueColor() {
        TermAttr attr = TermAttr.builder()
                .foregroundRgb(0xFF0000)
                .backgroundRgb(0x0000FF)
                .build();
        assertThat(attr.fgMode()).isEqualTo(2);
        assertThat(attr.fgColor()).isEqualTo(0xFF0000);
        assertThat(attr.bgMode()).isEqualTo(2);
        assertThat(attr.bgColor()).isEqualTo(0x0000FF);
    }

    @Test
    void testReset() {
        TermAttr attr = TermAttr.builder()
                .bold(true)
                .foreground(TermAttr.RED)
                .reset()
                .build();
        assertThat(attr).isEqualTo(TermAttr.DEFAULT);
    }

    @Test
    void testToBuilder() {
        TermAttr base = TermAttr.builder().bold(true).foreground(TermAttr.GREEN).build();
        TermAttr modified = base.toBuilder().italic(true).build();
        assertThat(modified.bold()).isTrue();
        assertThat(modified.italic()).isTrue();
        assertThat(modified.foreground()).isEqualTo(TermAttr.GREEN);
    }

    @Test
    void testEquality() {
        TermAttr a = TermAttr.builder().bold(true).foreground(TermAttr.RED).build();
        TermAttr b = TermAttr.builder().bold(true).foreground(TermAttr.RED).build();
        TermAttr c = TermAttr.builder().bold(true).foreground(TermAttr.GREEN).build();
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void testColorConstants() {
        assertThat(TermAttr.BLACK).isZero();
        assertThat(TermAttr.WHITE).isEqualTo(7);
    }

    @Test
    void testUnderlineConstants() {
        assertThat(TermAttr.UNDERLINE_NONE).isZero();
        assertThat(TermAttr.UNDERLINE_SINGLE).isEqualTo(1);
    }
}
