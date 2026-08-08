package ssg.legoflow.upnp.ssdp;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Constants for the SSDP (Simple Service Discovery Protocol) used in UPnP.
 *
 * <p>Defines the multicast address, port, standard search targets, and
 * default timing values specified by the UPnP Device Architecture.
 *
 * @since 0.1.0
 */
public final class SsdpConstants {

    /**
     * The SSDP multicast IPv4 address: {@code 239.255.255.250}.
     *
     * @since 0.1.0
     */
    public static final String MULTICAST_ADDRESS = "239.255.255.250";

    /**
     * The SSDP multicast port: {@code 1900}.
     *
     * @since 0.1.0
     */
    public static final int MULTICAST_PORT = 1900;

    /**
     * The default MX (maximum wait) value in seconds for M-SEARCH requests.
     *
     * @since 0.1.0
     */
    public static final int DEFAULT_MX = 3;

    /**
     * Search target for all UPnP devices and services.
     *
     * @since 0.1.0
     */
    public static final String ST_ALL = "ssdp:all";

    /**
     * Search target for UPnP root devices only.
     *
     * @since 0.1.0
     */
    public static final String ST_ROOT_DEVICE = "upnp:rootdevice";

    /**
     * The HOST header value for SSDP multicast messages.
     *
     * @since 0.1.0
     */
    public static final String MULTICAST_HOST = MULTICAST_ADDRESS + ":" + MULTICAST_PORT;

    /**
     * NTS value for device alive notifications.
     *
     * @since 0.1.0
     */
    public static final String NTS_ALIVE = "ssdp:alive";

    /**
     * NTS value for device byebye notifications.
     *
     * @since 0.1.0
     */
    public static final String NTS_BYEBYE = "ssdp:byebye";

    /**
     * NTS value for device update notifications.
     *
     * @since 0.1.0
     */
    public static final String NTS_UPDATE = "ssdp:update";

    /**
     * The MAN header value for M-SEARCH requests.
     *
     * @since 0.1.0
     */
    public static final String MAN_DISCOVER = "\"ssdp:discover\"";

    /**
     * Default cache max-age in seconds for device advertisements.
     *
     * @since 0.1.0
     */
    public static final int DEFAULT_MAX_AGE = 1800;

    // SSDP header names
    /** HOST header name. @since 0.1.0 */
    public static final String HEADER_HOST = "HOST";
    /** CACHE-CONTROL header name. @since 0.1.0 */
    public static final String HEADER_CACHE_CONTROL = "CACHE-CONTROL";
    /** LOCATION header name. @since 0.1.0 */
    public static final String HEADER_LOCATION = "LOCATION";
    /** NT (notification type) header name. @since 0.1.0 */
    public static final String HEADER_NT = "NT";
    /** NTS (notification sub-type) header name. @since 0.1.0 */
    public static final String HEADER_NTS = "NTS";
    /** SERVER header name. @since 0.1.0 */
    public static final String HEADER_SERVER = "SERVER";
    /** USN (unique service name) header name. @since 0.1.0 */
    public static final String HEADER_USN = "USN";
    /** MAN header name. @since 0.1.0 */
    public static final String HEADER_MAN = "MAN";
    /** MX (maximum wait) header name. @since 0.1.0 */
    public static final String HEADER_MX = "MX";
    /** ST (search target) header name. @since 0.1.0 */
    public static final String HEADER_ST = "ST";
    /** EXT header name (required in M-SEARCH responses). @since 0.1.0 */
    public static final String HEADER_EXT = "EXT";
    /** DATE header name. @since 0.1.0 */
    public static final String HEADER_DATE = "DATE";

    private SsdpConstants() {
        // Utility class
    }

    /**
     * Returns the SSDP multicast {@link InetAddress}.
     *
     * @return the multicast address for SSDP
     * @since 0.1.0
     */
    public static InetAddress getMulticastAddress() {
        try {
            return InetAddress.getByName(MULTICAST_ADDRESS);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Failed to resolve SSDP multicast address", e);
        }
    }

    /**
     * Returns the SSDP multicast {@link InetSocketAddress}.
     *
     * @return the multicast socket address for SSDP
     * @since 0.1.0
     */
    public static InetSocketAddress getMulticastSocketAddress() {
        return new InetSocketAddress(getMulticastAddress(), MULTICAST_PORT);
    }
}
