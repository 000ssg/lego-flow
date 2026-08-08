package ssg.legoflow.service.passthrough;

/**
 * Listener for pass-through connection lifecycle and data transfer events.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface PassThroughListener {

    /**
     * Called when a pass-through event occurs.
     *
     * @param event the event that occurred, never null
     */
    void onEvent(PassThroughEvent event);
}
