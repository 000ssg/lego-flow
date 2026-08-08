package ssg.legoflow.upnp.ssdp;

/**
 * Sealed interface representing SSDP discovery events.
 *
 * <p>Events are emitted by the {@link SsdpService} when devices are discovered,
 * lost, or respond to search queries on the local network.
 *
 * @since 0.1.0
 */
public sealed interface SsdpEvent {

    /**
     * Event emitted when a new UPnP device is discovered via NOTIFY ssdp:alive.
     *
     * @param message  the SSDP message that announced the device
     * @param usn      the unique service name of the discovered device
     * @param location the URL to the device description
     * @since 0.1.0
     */
    record DeviceDiscovered(SsdpMessage message, String usn, String location) implements SsdpEvent {
    }

    /**
     * Event emitted when a UPnP device departs via NOTIFY ssdp:byebye or cache expiry.
     *
     * @param usn      the unique service name of the lost device
     * @param location the last known location URL; may be {@code null} for cache-expired devices
     * @since 0.1.0
     */
    record DeviceLost(String usn, String location) implements SsdpEvent {
    }

    /**
     * Event emitted when a search response is received from a device.
     *
     * @param message the SSDP search response message
     * @since 0.1.0
     */
    record SearchResponse(SsdpMessage message) implements SsdpEvent {
    }
}
