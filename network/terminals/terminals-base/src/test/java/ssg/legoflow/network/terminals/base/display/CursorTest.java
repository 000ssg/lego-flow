package ssg.legoflow.network.terminals.base.display;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CursorTest {

    @Test
    void testInitialPosition() {
        Cursor c = new Cursor(1, 1);
        assertThat(c.row()).isEqualTo(1);
        assertThat(c.col()).isEqualTo(1);
        assertThat(c.visible()).isTrue();
    }

    @Test
    void testSetPosition() {
        Cursor c = new Cursor(1, 1);
        c.setPos(5, 10);
        assertThat(c.row()).isEqualTo(5);
        assertThat(c.col()).isEqualTo(10);
    }

    @Test
    void testUp() {
        Cursor c = new Cursor(10, 5);
        c.up(3);
        assertThat(c.row()).isEqualTo(7);
        c.up(20); // clamp to 1
        assertThat(c.row()).isEqualTo(1);
    }

    @Test
    void testDown() {
        Cursor c = new Cursor(1, 5);
        c.down(3);
        assertThat(c.row()).isEqualTo(4);
    }

    @Test
    void testForward() {
        Cursor c = new Cursor(1, 1);
        c.forward(5);
        assertThat(c.col()).isEqualTo(6);
    }

    @Test
    void testBackClampsToOne() {
        Cursor c = new Cursor(1, 3);
        c.back(10);
        assertThat(c.col()).isEqualTo(1);
    }

    @Test
    void testVisibility() {
        Cursor c = new Cursor(1, 1);
        assertThat(c.visible()).isTrue();
        c.hide();
        assertThat(c.visible()).isFalse();
        c.show();
        assertThat(c.visible()).isTrue();
        c.toggle();
        assertThat(c.visible()).isFalse();
    }

    @Test
    void testCloneCreatesCopy() {
        Cursor original = new Cursor(5, 10);
        original.hide();
        Cursor copy = original.clone();
        assertThat(copy.row()).isEqualTo(5);
        assertThat(copy.col()).isEqualTo(10);
        assertThat(copy.visible()).isFalse();
    }
}
