package ssg.legoflow.network.terminals.base.event;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for TerminalEvent sealed interface and its implementations.
 *
 * Verifies that each event type correctly reports its type() and
 * that records carry the expected data.
 */
class TerminalEventTest {

    // ── CharacterEvent ──

    @Test
    void testCharacterEvent() {
        var chars = java.util.List.of('H', 'e', 'l', 'l', 'o');
        TerminalEvent event = new TerminalEvent.CharacterEvent(1, 5, chars);

        assertThat(event.type()).isEqualTo(TerminalEvent.Type.CHARACTER);
        TerminalEvent.CharacterEvent ce = (TerminalEvent.CharacterEvent) event;
        assertThat(ce.row()).isEqualTo(1);
        assertThat(ce.col()).isEqualTo(5);
        assertThat(ce.chars()).containsSequence('H', 'e', 'l');
    }

    @Test
    void testCharacterEventEmpty() {
        TerminalEvent event = new TerminalEvent.CharacterEvent(10, 1, java.util.List.of());
        assertThat(event.type()).isEqualTo(TerminalEvent.Type.CHARACTER);
    }

    // ── CursorEvent ──

    @Test
    void testCursorEvent() {
        TerminalEvent event = new TerminalEvent.CursorEvent(5, 10, true);

        assertThat(event.type()).isEqualTo(TerminalEvent.Type.CURSOR);
        TerminalEvent.CursorEvent ce = (TerminalEvent.CursorEvent) event;
        assertThat(ce.row()).isEqualTo(5);
        assertThat(ce.col()).isEqualTo(10);
        assertThat(ce.visible()).isTrue();
    }

    @Test
    void testCursorEventHidden() {
        TerminalEvent event = new TerminalEvent.CursorEvent(1, 1, false);
        TerminalEvent.CursorEvent ce = (TerminalEvent.CursorEvent) event;
        assertThat(ce.visible()).isFalse();
    }

    // ── LineEvent ──

    @Test
    void testLineEventInsert() {
        TerminalEvent event = new TerminalEvent.LineEvent(5, 3, TerminalEvent.LineEvent.LineAction.INSERT);

        assertThat(event.type()).isEqualTo(TerminalEvent.Type.LINE);
        TerminalEvent.LineEvent le = (TerminalEvent.LineEvent) event;
        assertThat(le.row()).isEqualTo(5);
        assertThat(le.count()).isEqualTo(3);
        assertThat(le.action()).isEqualTo(TerminalEvent.LineEvent.LineAction.INSERT);
    }

    @Test
    void testLineEventDelete() {
        TerminalEvent event = new TerminalEvent.LineEvent(1, 1, TerminalEvent.LineEvent.LineAction.DELETE);
        TerminalEvent.LineEvent le = (TerminalEvent.LineEvent) event;
        assertThat(le.action()).isEqualTo(TerminalEvent.LineEvent.LineAction.DELETE);
    }

    @Test
    void testLineActionEnum() {
        assertThat(TerminalEvent.LineEvent.LineAction.values()).hasSize(2);
        assertThat(TerminalEvent.LineEvent.LineAction.INSERT).isNotNull();
        assertThat(TerminalEvent.LineEvent.LineAction.DELETE).isNotNull();
    }

    // ── ScrollEvent ──

    @Test
    void testScrollEventDown() {
        TerminalEvent event = new TerminalEvent.ScrollEvent(1, 24, TerminalEvent.ScrollEvent.ScrollDirection.DOWN);

        assertThat(event.type()).isEqualTo(TerminalEvent.Type.SCROLL);
        TerminalEvent.ScrollEvent se = (TerminalEvent.ScrollEvent) event;
        assertThat(se.scrollTop()).isEqualTo(1);
        assertThat(se.scrollBottom()).isEqualTo(24);
        assertThat(se.direction()).isEqualTo(TerminalEvent.ScrollEvent.ScrollDirection.DOWN);
    }

    @Test
    void testScrollEventUp() {
        TerminalEvent event = new TerminalEvent.ScrollEvent(1, 24, TerminalEvent.ScrollEvent.ScrollDirection.UP);
        TerminalEvent.ScrollEvent se = (TerminalEvent.ScrollEvent) event;
        assertThat(se.direction()).isEqualTo(TerminalEvent.ScrollEvent.ScrollDirection.UP);
    }

    @Test
    void testScrollEventRegionChange() {
        TerminalEvent event = new TerminalEvent.ScrollEvent(5, 20, TerminalEvent.ScrollEvent.ScrollDirection.REGION_CHANGE);
        TerminalEvent.ScrollEvent se = (TerminalEvent.ScrollEvent) event;
        assertThat(se.direction()).isEqualTo(TerminalEvent.ScrollEvent.ScrollDirection.REGION_CHANGE);
    }

    @Test
    void testScrollDirectionEnum() {
        assertThat(TerminalEvent.ScrollEvent.ScrollDirection.values()).hasSize(3);
    }

    // ── ClearEvent ──

    @Test
    void testClearEventDisplay() {
        TerminalEvent event = new TerminalEvent.ClearEvent(TerminalEvent.ClearEvent.ClearScope.DISPLAY);

        assertThat(event.type()).isEqualTo(TerminalEvent.Type.CLEAR);
        TerminalEvent.ClearEvent ce = (TerminalEvent.ClearEvent) event;
        assertThat(ce.scope()).isEqualTo(TerminalEvent.ClearEvent.ClearScope.DISPLAY);
    }

    @Test
    void testClearEventLine() {
        TerminalEvent event = new TerminalEvent.ClearEvent(TerminalEvent.ClearEvent.ClearScope.LINE);
        TerminalEvent.ClearEvent ce = (TerminalEvent.ClearEvent) event;
        assertThat(ce.scope()).isEqualTo(TerminalEvent.ClearEvent.ClearScope.LINE);
    }

    @Test
    void testClearEventAll() {
        TerminalEvent event = new TerminalEvent.ClearEvent(TerminalEvent.ClearEvent.ClearScope.ALL);
        TerminalEvent.ClearEvent ce = (TerminalEvent.ClearEvent) event;
        assertThat(ce.scope()).isEqualTo(TerminalEvent.ClearEvent.ClearScope.ALL);
    }

    @Test
    void testClearScopeEnum() {
        assertThat(TerminalEvent.ClearEvent.ClearScope.values()).hasSize(3);
    }

    // ── AttributeEvent ──

    @Test
    void testAttributeEvent() {
        TerminalEvent event = new TerminalEvent.AttributeEvent(3, 5, 10);

        assertThat(event.type()).isEqualTo(TerminalEvent.Type.ATTRIBUTE);
        TerminalEvent.AttributeEvent ae = (TerminalEvent.AttributeEvent) event;
        assertThat(ae.row()).isEqualTo(3);
        assertThat(ae.col()).isEqualTo(5);
        assertThat(ae.length()).isEqualTo(10);
    }

    // ── Type enum ──

    @Test
    void testTypeEnumValues() {
        assertThat(TerminalEvent.Type.values()).hasSize(6);
        assertThat(TerminalEvent.Type.CHARACTER).isNotNull();
        assertThat(TerminalEvent.Type.CURSOR).isNotNull();
        assertThat(TerminalEvent.Type.LINE).isNotNull();
        assertThat(TerminalEvent.Type.SCROLL).isNotNull();
        assertThat(TerminalEvent.Type.CLEAR).isNotNull();
        assertThat(TerminalEvent.Type.ATTRIBUTE).isNotNull();
    }

    @Test
    void testEventTypeConsistency() {
        // Verify that each event's type() matches its class
        TerminalEvent[] events = {
            new TerminalEvent.CharacterEvent(1, 1, java.util.List.of('A')),
            new TerminalEvent.CursorEvent(1, 1, true),
            new TerminalEvent.LineEvent(1, 1, TerminalEvent.LineEvent.LineAction.INSERT),
            new TerminalEvent.ScrollEvent(1, 24, TerminalEvent.ScrollEvent.ScrollDirection.DOWN),
            new TerminalEvent.ClearEvent(TerminalEvent.ClearEvent.ClearScope.DISPLAY),
            new TerminalEvent.AttributeEvent(1, 1, 1)
        };

        TerminalEvent.Type[] expected = {
            TerminalEvent.Type.CHARACTER,
            TerminalEvent.Type.CURSOR,
            TerminalEvent.Type.LINE,
            TerminalEvent.Type.SCROLL,
            TerminalEvent.Type.CLEAR,
            TerminalEvent.Type.ATTRIBUTE
        };

        for (int i = 0; i < events.length; i++) {
            assertThat(events[i].type()).isEqualTo(expected[i]);
        }
    }
}
