package ssg.legoflow.upnp.ssdp;

/**
 * Enumeration of SSDP message types used in UPnP discovery.
 *
 * <p>SSDP (Simple Service Discovery Protocol) defines several message types
 * for device advertisement and search. This enum represents each distinct
 * message type that can appear in the UPnP discovery process.
 *
 * @since 1.0.0
 */
public enum SsdpMessageType {

    /**
     * A NOTIFY message with NTS: ssdp:alive, advertising device availability.
     *
     * @since 1.0.0
     */
    NOTIFY_ALIVE,

    /**
     * A NOTIFY message with NTS: ssdp:byebye, advertising device departure.
     *
     * @since 1.0.0
     */
    NOTIFY_BYEBYE,

    /**
     * A NOTIFY message with NTS: ssdp:update, advertising device description change.
     *
     * @since 1.0.0
     */
    NOTIFY_UPDATE,

    /**
     * An M-SEARCH request message for discovering devices on the network.
     *
     * @since 1.0.0
     */
    M_SEARCH,

    /**
     * A response to an M-SEARCH request, providing device/service information.
     *
     * @since 1.0.0
     */
    M_SEARCH_RESPONSE
}
