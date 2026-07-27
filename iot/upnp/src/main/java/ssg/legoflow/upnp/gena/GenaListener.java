package ssg.legoflow.upnp.gena;

/**
 * Listener for GENA event notifications.
 *
 * <p>Implementations receive callbacks when state variables change
 * on subscribed UPnP services.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface GenaListener {

    /**
     * Called when an event notification is received from a subscribed service.
     *
     * @param event the event message containing changed state variables
     * @since 1.0.0
     */
    void onEvent(EventMessage event);
}
