package ssg.legoflow.upnp.ssdp;

/**
 * Listener for SSDP discovery events.
 *
 * <p>Implementations receive notifications when UPnP devices are discovered,
 * lost, or respond to search requests on the local network.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface SsdpListener {

    /**
     * Called when an SSDP event occurs.
     *
     * @param event the SSDP event containing discovery information
     * @since 0.1.0
     */
    void onSsdpEvent(SsdpEvent event);
}
