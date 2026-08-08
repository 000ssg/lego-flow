package ssg.legoflow.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.Selector;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link UdpDataChannel} with multicast group management.
 *
 * <p>Extends UDP support with the ability to join and leave multicast groups,
 * track active group memberships, and configure multicast-specific socket options
 * such as TTL and loopback.
 *
 * <p>This class is thread-safe. Group membership operations are synchronized
 * via a concurrent map.
 *
 * @since 0.1.0
 */
public class MulticastDataChannel extends UdpDataChannel {

    private static final Logger LOG = LoggerFactory.getLogger(MulticastDataChannel.class);

    private final Map<MulticastConfig, MembershipKey> memberships = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code MulticastDataChannel} wrapping the given datagram channel
     * and registering it with the specified selector.
     *
     * @param datagramChannel the underlying NIO datagram channel; must support multicast
     * @param selector        the NIO selector for event registration
     * @throws IOException          if registration with the selector fails
     * @throws NullPointerException if {@code datagramChannel} or {@code selector} is {@code null}
     * @since 0.1.0
     */
    /**
     * Creates a new {@code MulticastDataChannel} with deferred selector registration.
     *
     * <p>The channel is configured for non-blocking I/O but is not registered with
     * any selector. Call {@link #registerWith(Selector)} to complete registration.
     *
     * @param datagramChannel the underlying NIO datagram channel; must support multicast
     * @throws IOException          if configuring non-blocking mode fails
     * @throws NullPointerException if {@code datagramChannel} is {@code null}
     * @since 0.1.0
     */
    public MulticastDataChannel(DatagramChannel datagramChannel) throws IOException {
        super(datagramChannel);
    }

    public MulticastDataChannel(DatagramChannel datagramChannel, Selector selector) throws IOException {
        super(datagramChannel, selector);
    }

    /**
     * Creates a new multicast-capable {@link DatagramChannel} with {@code SO_REUSEADDR} enabled.
     *
     * @param family the protocol family ({@link StandardProtocolFamily#INET} or
     *               {@link StandardProtocolFamily#INET6})
     * @return a new datagram channel configured for multicast
     * @throws IOException if the channel cannot be opened
     * @since 0.1.0
     */
    public static DatagramChannel openMulticastChannel(StandardProtocolFamily family) throws IOException {
        var channel = DatagramChannel.open(family);
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        return channel;
    }

    /**
     * Joins a multicast group as specified by the given configuration.
     *
     * <p>Configures the channel's multicast TTL and loopback settings from the config,
     * then joins the group on the specified network interface.
     *
     * @param config the multicast configuration specifying group, interface, TTL, and loopback
     * @return the {@link MembershipKey} representing the group membership
     * @throws IOException              if joining the group fails
     * @throws NullPointerException     if {@code config} is {@code null}
     * @throws IllegalStateException    if already a member of the specified group/interface
     * @since 0.1.0
     */
    public MembershipKey joinGroup(MulticastConfig config) throws IOException {
        Objects.requireNonNull(config, "config must not be null");
        if (memberships.containsKey(config)) {
            throw new IllegalStateException("Already a member of group: " + config.group()
                    + " on interface: " + config.networkInterface());
        }

        var channel = getDatagramChannel();
        channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, config.ttl());
        channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, config.loopback());

        var key = channel.join(config.group(), config.networkInterface());
        memberships.put(config, key);
        LOG.debug("Joined multicast group {} on interface {}", config.group(), config.networkInterface());
        return key;
    }

    /**
     * Leaves a multicast group previously joined with the given configuration.
     *
     * @param config the multicast configuration identifying the group membership to leave
     * @throws NullPointerException  if {@code config} is {@code null}
     * @throws IllegalStateException if not a member of the specified group/interface
     * @since 0.1.0
     */
    public void leaveGroup(MulticastConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        var key = memberships.remove(config);
        if (key == null) {
            throw new IllegalStateException("Not a member of group: " + config.group()
                    + " on interface: " + config.networkInterface());
        }
        key.drop();
        LOG.debug("Left multicast group {} on interface {}", config.group(), config.networkInterface());
    }

    /**
     * Returns the set of multicast configurations for all currently joined groups.
     *
     * @return an unmodifiable set of active multicast configurations
     * @since 0.1.0
     */
    public Set<MulticastConfig> getGroups() {
        return Set.copyOf(memberships.keySet());
    }

    /**
     * Closes this channel, dropping all multicast group memberships first.
     *
     * @throws IOException if an I/O error occurs
     * @since 0.1.0
     */
    @Override
    public void close() throws IOException {
        memberships.values().forEach(MembershipKey::drop);
        memberships.clear();
        super.close();
        LOG.debug("Multicast channel closed, all groups left");
    }
}
