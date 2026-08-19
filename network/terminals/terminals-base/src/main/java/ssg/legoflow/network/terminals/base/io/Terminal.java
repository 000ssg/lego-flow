package ssg.legoflow.network.terminals.base.io;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;

import java.util.List;

/**
 * Core contract for terminal emulator implementations.
 *
 * <p>All terminal type implementations (VT52, VT100, VT200, etc.) extend this
 * interface with type-specific capabilities while sharing the common I/O model.
 *
 * <p>The terminal processes input bytes through an escape sequence parser and
 * updates its internal display model. Rendering backends listen to
 * {@link TerminalEventListener} callbacks.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * TerminalConfig config = TerminalConfig.builder()
 *         .rows(24).cols(80).build();
 * Terminal terminal = VT100Terminal.create(config);
 * terminal.addEventListener(renderer);
 *
 * // Feed data from the network
 * terminal.feed(data);
 *
 * // Or query state directly
 * List<String> lines = terminal.render();
 * }</pre>
 *
 * @since 0.2.0
 */
public interface Terminal {

    /**
     * Feed input data to the terminal for processing.
     *
     * <p>This is the primary entry point. The terminal parses the input bytes,
     * interprets escape sequences, and updates its display model.
     *
     * @param data the input bytes
     */
    void feed(byte[] data);

    /**
     * Feed a single string to the terminal.
     *
     * @param text the input text
     */
    void feed(String text);

    /**
     * Feed a single character.
     *
     * @param ch the character
     */
    default void feedChar(char ch) {
        feed(String.valueOf(ch));
    }

    /**
     * Get the terminal display as a list of text lines.
     *
     * @return list of strings, one per visible row
     */
    List<String> render();

    /**
     * Get the current cursor position.
     */
    Cursor cursor();

    /**
     * Get the current text attributes at the cursor position.
     */
    TermAttr currentAttr();

    /**
     * Get the terminal configuration.
     */
    TerminalConfig config();

    /**
     * Get the display model (for advanced access).
     */
    DisplayModel displayModel();

    /**
     * Register an event listener for display updates.
     *
     * @param listener the listener
     */
    void addEventListener(TerminalEventListener listener);

    /**
     * Remove an event listener.
     *
     * @param listener the listener
     */
    void removeEventListener(TerminalEventListener listener);

    /**
     * Clear the display and reset terminal state.
     */
    void reset();

    /**
     * Get the terminal type identifier.
     *
     * @return a string like "vt100", "xterm", "vt52"
     */
    String type();

    /**
     * Get the terminal title.
     */
    String title();

    /**
     * Check if this terminal supports color.
     */
    boolean supportsColor();
}
