package ssg.legoflow.network.terminals.base.event;

import java.util.List;

/**
 * Events emitted by a terminal emulator in response to input processing.
 *
 * <p>These events drive rendering backends: a Swing component, a web HTerm
 * client, or a plain-text logger. Each event carries the minimal information
 * needed to update the display efficiently.
 *
 * @since 0.2.0
 */
public sealed interface TerminalEvent permits
        TerminalEvent.CharacterEvent,
        TerminalEvent.CursorEvent,
        TerminalEvent.ScrollEvent,
        TerminalEvent.ClearEvent,
        TerminalEvent.AttributeEvent,
        TerminalEvent.LineEvent {

    /** The type of this event. */
    Type type();

    /** Event types. */
    enum Type {
        /** Characters output to the screen. */
        CHARACTER,
        /** Cursor moved or visibility changed. */
        CURSOR,
        /** Lines inserted or deleted. */
        LINE,
        /** Scroll region changed or scroll occurred. */
        SCROLL,
        /** Display cleared. */
        CLEAR,
        /** Text attributes changed. */
        ATTRIBUTE
    }

    /** Characters written to the screen buffer. */
    record CharacterEvent(int row, int col, List<Character> chars) implements TerminalEvent {
        public Type type() { return Type.CHARACTER; }
    }

    /** Cursor position or visibility change. */
    record CursorEvent(int row, int col, boolean visible) implements TerminalEvent {
        public Type type() { return Type.CURSOR; }
    }

    /** Lines inserted or deleted. */
    record LineEvent(int row, int count, LineAction action) implements TerminalEvent {
        public Type type() { return Type.LINE; }

        /** Action performed. */
        public enum LineAction { INSERT, DELETE }
    }

    /** Scroll event (scroll up/down or scroll region change). */
    record ScrollEvent(int scrollTop, int scrollBottom, ScrollDirection direction) implements TerminalEvent {
        public Type type() { return Type.SCROLL; }

        /** Direction of scroll. */
        public enum ScrollDirection { UP, DOWN, REGION_CHANGE }
    }

    /** Display cleared. */
    record ClearEvent(ClearScope scope) implements TerminalEvent {
        public Type type() { return Type.CLEAR; }

        /** What was cleared. */
        public enum ClearScope { LINE, DISPLAY, ALL }
    }

    /** Text attribute change. */
    record AttributeEvent(int row, int col, int length) implements TerminalEvent {
        public Type type() { return Type.ATTRIBUTE; }
    }
}
