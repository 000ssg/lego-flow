package ssg.legoflow.network.terminals.base.event;

/**
 * Callback interface for terminal display events.
 *
 * <p>Implementations receive events as the terminal processes input,
 * allowing rendering backends to update incrementally rather than
 * redrawing the entire display.
 *
 * @since 0.2.0
 */
@FunctionalInterface
public interface TerminalEventListener {

    /**
     * Called when the terminal emits an event.
     *
     * @param event the event
     */
    void onEvent(TerminalEvent event);

    /**
     * Called when the terminal title changes.
     *
     * @param title the new title
     */
    default void onTitleChange(String title) {}

    /**
     * Called when the terminal icon title changes.
     *
     * @param iconTitle the new icon title
     */
    default void onIconTitleChange(String iconTitle) {}
}
