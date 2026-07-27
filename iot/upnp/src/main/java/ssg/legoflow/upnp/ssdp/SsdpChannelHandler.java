package ssg.legoflow.upnp.ssdp;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.DatagramHandler;
import ssg.legoflow.service.channel.DatagramPacketInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A {@link DatagramHandler} that bridges {@link ssg.legoflow.service.manager.ServiceGroup}
 * pipeline events to {@link SsdpService#processMessage(SsdpMessage)}.
 *
 * <p>When a datagram arrives via the ServiceGroup event loop, this handler decodes
 * the UTF-8 payload, parses it as an {@link SsdpMessage}, and delegates to the
 * SsdpService's message processing logic. This enables SsdpService to operate
 * through a ServiceGroup's NIO selector infrastructure instead of its own
 * blocking receive loop.
 *
 * @since 1.0.0
 */
public class SsdpChannelHandler implements DatagramHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SsdpChannelHandler.class);

    private final SsdpService ssdpService;

    /**
     * Creates a new handler that delegates to the given SSDP service.
     *
     * @param ssdpService the SSDP service to forward messages to
     * @throws NullPointerException if {@code ssdpService} is {@code null}
     * @since 1.0.0
     */
    public SsdpChannelHandler(SsdpService ssdpService) {
        this.ssdpService = Objects.requireNonNull(ssdpService, "ssdpService must not be null");
    }

    /**
     * Called when a datagram is received. Parses the datagram as an SSDP message
     * and delegates to {@link SsdpService#processMessage(SsdpMessage)}.
     *
     * @param channel the data channel that received the datagram
     * @param packet  the datagram packet with sender address and payload
     * @since 1.0.0
     */
    @Override
    public void onDatagram(DataChannel channel, DatagramPacketInfo packet) {
        try {
            var buf = packet.data().duplicate();
            var text = StandardCharsets.UTF_8.decode(buf).toString();
            var message = SsdpMessage.parse(text, packet.sender());
            ssdpService.processMessage(message);
        } catch (Exception e) {
            LOG.warn("Failed to process SSDP datagram from {}", packet.sender(), e);
        }
    }

    @Override
    public void onSendComplete(DataChannel channel, java.net.SocketAddress target) {
        LOG.trace("SSDP datagram sent to {}", target);
    }

    @Override
    public void onError(DataChannel channel, Throwable cause) {
        LOG.error("SSDP channel error", cause);
    }
}
