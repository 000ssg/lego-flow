package ssg.legoflow.service.channel;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Objects;

/**
 * Immutable configuration record for multicast group membership.
 *
 * <p>Encapsulates the network interface, multicast group address, TTL (time-to-live),
 * and loopback settings required to join a multicast group.
 *
 * @param networkInterface the network interface for multicast communication
 * @param group            the multicast group address
 * @param ttl              the time-to-live for multicast packets (1-255)
 * @param loopback         whether multicast packets should be looped back to the sender
 * @since 1.0.0
 */
public record MulticastConfig(NetworkInterface networkInterface, InetAddress group, int ttl, boolean loopback) {

    /**
     * Creates a new {@code MulticastConfig} with validation.
     *
     * @param networkInterface the network interface; must not be {@code null}
     * @param group            the multicast group address; must not be {@code null} and must be a multicast address
     * @param ttl              the time-to-live; must be between 1 and 255
     * @param loopback         whether loopback is enabled
     * @throws NullPointerException     if {@code networkInterface} or {@code group} is {@code null}
     * @throws IllegalArgumentException if {@code group} is not a multicast address or {@code ttl} is out of range
     */
    public MulticastConfig {
        Objects.requireNonNull(networkInterface, "networkInterface must not be null");
        Objects.requireNonNull(group, "group must not be null");
        if (!group.isMulticastAddress()) {
            throw new IllegalArgumentException("Address is not a multicast address: " + group);
        }
        if (ttl < 1 || ttl > 255) {
            throw new IllegalArgumentException("TTL must be between 1 and 255: " + ttl);
        }
    }

    /**
     * Creates a {@code MulticastConfig} for the given group address using the default
     * network interface, TTL of 1, and loopback enabled.
     *
     * @param group the multicast group address
     * @return a new {@code MulticastConfig}
     * @throws NullPointerException     if {@code group} is {@code null}
     * @throws IllegalArgumentException if {@code group} is not a multicast address
     * @throws SocketException          if the default network interface cannot be determined
     * @since 1.0.0
     */
    public static MulticastConfig of(InetAddress group) throws SocketException {
        return new MulticastConfig(
                NetworkInterface.getByIndex(1),
                group,
                1,
                true
        );
    }

    /**
     * Creates a {@code MulticastConfig} for the given group address and network interface
     * with TTL of 1 and loopback enabled.
     *
     * @param group            the multicast group address
     * @param networkInterface the network interface
     * @return a new {@code MulticastConfig}
     * @throws NullPointerException     if {@code group} or {@code networkInterface} is {@code null}
     * @throws IllegalArgumentException if {@code group} is not a multicast address
     * @since 1.0.0
     */
    public static MulticastConfig of(InetAddress group, NetworkInterface networkInterface) {
        return new MulticastConfig(networkInterface, group, 1, true);
    }
}
