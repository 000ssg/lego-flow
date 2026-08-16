package ssg.legoflow.network.cluster.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Manages network interfaces suitable for mDNS multicast communication.
 *
 * <p>Per RFC 6762 §5, mDNS should only be sent on appropriate interfaces:
 * <ul>
 *   <li>Loopback is allowed for testing (single-node clusters)</li>
 *   <li>Point-to-point interfaces are excluded</li>
 *   <li>Interfaces that don't support multicast are excluded</li>
 *   <li>Interfaces that are down are excluded</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class MdnsInterfaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(MdnsInterfaceManager.class);

    private MdnsInterfaceManager() {}

    /**
     * Discovers all network interfaces suitable for mDNS.
     *
     * @return list of qualifying interfaces
     * @throws SocketException if network interfaces cannot be enumerated
     * @since 0.2.0
     */
    public static List<NetworkInterface> discoverMdnsInterfaces() throws SocketException {
        return discoverMdnsInterfaces(null);
    }

    /**
     * Discovers mDNS-capable interfaces filtered by the given predicate.
     *
     * @param filter optional filter (null for all suitable interfaces)
     * @return list of qualifying interfaces
     * @throws SocketException if network interfaces cannot be enumerated
     * @since 0.2.0
     */
    public static List<NetworkInterface> discoverMdnsInterfaces(Predicate<NetworkInterface> filter)
            throws SocketException {
        List<NetworkInterface> result = new ArrayList<>();

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();

            // Skip if interface is down
            if (!iface.isUp()) {
                LOG.trace("Skipping down interface: {}", iface.getDisplayName());
                continue;
            }

            // Skip loopback unless explicitly allowed
            if (iface.isLoopback()) {
                LOG.trace("Skipping loopback interface: {}", iface.getDisplayName());
                continue;
            }

            // Skip point-to-point interfaces
            if (iface.isPointToPoint()) {
                LOG.trace("Skipping point-to-point interface: {}", iface.getDisplayName());
                continue;
            }

            // Skip if multicast is not supported
            if (!iface.supportsMulticast()) {
                LOG.trace("Skipping non-multicast interface: {}", iface.getDisplayName());
                continue;
            }

            // Skip virtual interfaces (bridges, tunnels)
            if (isVirtualInterface(iface)) {
                LOG.trace("Skipping virtual interface: {}", iface.getDisplayName());
                continue;
            }

            // Apply custom filter
            if (filter != null && !filter.test(iface)) {
                continue;
            }

            result.add(iface);
            LOG.debug("mDNS-capable interface: {}", iface.getDisplayName());
        }

        return result;
    }

    /**
     * Discovers all loopback interfaces (for testing).
     *
     * @return list of loopback interfaces
     * @throws SocketException if network interfaces cannot be enumerated
     * @since 0.2.0
     */
    public static List<NetworkInterface> discoverLoopbackInterfaces() throws SocketException {
        List<NetworkInterface> result = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() && iface.isUp()) {
                result.add(iface);
            }
        }
        return result;
    }

    /**
     * Returns the first suitable mDNS interface, or null if none available.
     *
     * @return the primary mDNS interface
     * @throws SocketException if network interfaces cannot be enumerated
     * @since 0.2.0
     */
    public static NetworkInterface primaryMdnsInterface() throws SocketException {
        List<NetworkInterface> interfaces = discoverMdnsInterfaces();
        if (interfaces.isEmpty()) {
            // Fall back to loopback for local development
            List<NetworkInterface> loopback = discoverLoopbackInterfaces();
            if (!loopback.isEmpty()) {
                LOG.info("No non-loopback mDNS interfaces found; using loopback: {}",
                        loopback.get(0).getDisplayName());
                return loopback.get(0);
            }
            return null;
        }
        return interfaces.get(0);
    }

    /**
     * Returns the first IPv4 address from the given interface.
     *
     * @param iface the network interface
     * @return the first IPv4 address, or null if none
     * @since 0.2.0
     */
    public static InetAddress firstIpv4Address(NetworkInterface iface) {
        Objects.requireNonNull(iface);
        for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
            InetAddress address = addr.getAddress();
            if (address != null && address.getAddress().length == 4) {
                return address;
            }
        }
        return null;
    }

    private static boolean isVirtualInterface(NetworkInterface iface) {
        String name = iface.getDisplayName();
        return name.startsWith("veth")
                || name.startsWith("br-")
                || name.startsWith("docker")
                || name.startsWith("virbr")
                || name.startsWith("tun")
                || name.startsWith("tap")
                || name.contains("@"); // alias interfaces (e.g. eth0:1)
    }
}
