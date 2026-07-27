package ssg.legoflow.upnp.ssdp;

/**
 * Listener for SSDP discovery events.
 *
 * <p>Implementations receive notifications when UPnP devices are discovered,
 * lost, or respond to search requests on the local network.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface SsdpListener {

    /**
     * Called when an SSDP event occurs.
     *
     * @param event the SSDP event containing discovery information
     * @since 1.0.0
     */
    void onSsdpEvent(SsdpEvent event);
}
