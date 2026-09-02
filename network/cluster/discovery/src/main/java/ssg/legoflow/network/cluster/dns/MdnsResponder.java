package ssg.legoflow.network.cluster.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsQuestion;
import ssg.legoflow.network.dns.protocol.RecordType;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * mDNS responder that listens for multicast DNS queries and responds
 * to requests for this node's service records.
 *
 * <p>Per RFC 6762, the responder:
 * <ul>
 *   <li>Listens on 224.0.0.251:5353 for queries</li>
 *   <li>Responds to queries for its own instance (SRV, A, PTR)</li>
 *   <li>Sends initial announcements on startup</li>
 *   <li>Sends goodbye messages on shutdown</li>
 * </ul>
 *
 * <p>Uses a virtual thread executor for the listener loop.
 *
 * @since 0.2.0
 */
public final class MdnsResponder implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MdnsResponder.class);
    private static final int MAX_PACKET_SIZE = 4096;

    private final DnsSdServiceRecord serviceRecord;
    private final MulticastSocket socket;
    private final ExecutorService executor;
    private volatile boolean running;

    /**
     * Creates a responder bound to the mDNS multicast address.
     *
     * @param serviceRecord the service to advertise
     * @throws java.net.SocketException if the socket cannot be created
     * @since 0.2.0
     */
    public MdnsResponder(DnsSdServiceRecord serviceRecord) {
        this(serviceRecord, null);
    }

    /**
     * Creates a responder bound to a specific interface.
     *
     * @param serviceRecord the service to advertise
     * @param interfaceAddr the network interface to bind to (null for default)
     * @throws java.net.SocketException if the socket cannot be created
     * @since 0.2.0
     */
    public MdnsResponder(DnsSdServiceRecord serviceRecord, InetAddress interfaceAddr) {
        Objects.requireNonNull(serviceRecord, "serviceRecord must not be null");

        this.serviceRecord = serviceRecord;
        MulticastSocket sock = null;
        try {
            sock = new MulticastSocket(MdnsPacketCodec.MDNS_PORT);
            InetAddress group = InetAddress.getByName(MdnsPacketCodec.MDNS_IPV4_MULTICAST);
            if (interfaceAddr != null) {
                sock.setInterface(interfaceAddr);
            }
            sock.joinGroup(group);
            sock.setTimeToLive(255);
        } catch (java.io.IOException e) {
            try {
                if (sock != null) sock.close();
            } catch (Exception ignored) {}
            throw new RuntimeException("Failed to create mDNS responder socket", e);
        }
        this.socket = sock;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Starts the responder. Sends initial announcements and begins listening.
     *
     * @return a future completed when the initial announcement is sent
     * @since 0.2.0
     */
    public CompletableFuture<Void> start() {
        if (running) return CompletableFuture.completedFuture(null);
        running = true;

        // Send initial announcements (RFC 6762: 2 announcements 250ms apart)
        CompletableFuture<Void> announcement = sendAnnouncement();

        // Start the listener loop
        executor.submit(() -> listenLoop());

        return announcement;
    }

    /**
     * Stops the responder. Sends a goodbye message and closes the socket.
     *
     * @since 0.2.0
     */
    public void stop() {
        if (!running) return;
        running = false;

        // Send goodbye (zero-TTL records)
        try {
            sendGoodbye();
        } catch (Exception e) {
            LOG.warn("Failed to send mDNS goodbye: {}", e.getMessage());
        }

        executor.shutdownNow();
        socket.close();
    }

    @Override
    public void close() {
        stop();
    }

    private CompletableFuture<Void> sendAnnouncement() {
        try {
            DnsMessage announcement = MdnsPacketCodec.buildAnnouncement(serviceRecord);
            byte[] data = MdnsPacketCodec.encode(announcement);

            InetAddress group = InetAddress.getByName(MdnsPacketCodec.MDNS_IPV4_MULTICAST);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, MdnsPacketCodec.MDNS_PORT);
            socket.send(packet);
            LOG.debug("Sent mDNS announcement for {}", serviceRecord.instanceFqdn());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            LOG.error("Failed to send mDNS announcement: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private void sendGoodbye() {
        try {
            DnsMessage goodbye = MdnsPacketCodec.buildGoodbye(serviceRecord);
            byte[] data = MdnsPacketCodec.encode(goodbye);

            InetAddress group = InetAddress.getByName(MdnsPacketCodec.MDNS_IPV4_MULTICAST);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, MdnsPacketCodec.MDNS_PORT);
            socket.send(packet);
            LOG.debug("Sent mDNS goodbye for {}", serviceRecord.instanceFqdn());
        } catch (Exception e) {
            LOG.warn("Failed to send mDNS goodbye: {}", e.getMessage());
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byte[] data = java.util.Arrays.copyOfRange(buffer, 0, packet.getLength());

                try {
                    DnsMessage message = MdnsPacketCodec.decode(data);
                    if (!message.header().qr()) {
                        handleQuery(message, packet.getAddress());
                    }
                } catch (IllegalArgumentException e) {
                    LOG.trace("Skipping invalid mDNS packet: {}", e.getMessage());
                }
            } catch (java.io.IOException e) {
                if (running) {
                    LOG.warn("mDNS responder socket error: {}", e.getMessage());
                }
                break;
            }
        }
    }

    private void handleQuery(DnsMessage query, InetAddress sender) {
        for (DnsQuestion question : query.questions()) {
            String name = question.name().toCanonical();
            RecordType type = question.type();

            boolean matches = false;

            // Check if query is for our instance name (SRV, TXT)
            if (name.equals(serviceRecord.instanceFqdn().toLowerCase(java.util.Locale.ROOT))) {
                if (type == RecordType.SRV || type == RecordType.TXT || type == RecordType.ANY) {
                    matches = true;
                }
            }

            // Check if query is for our service domain (PTR)
            String serviceDomainCanonical = serviceRecord.serviceDomain().toLowerCase(java.util.Locale.ROOT);
            if (name.equals(serviceDomainCanonical)) {
                if (type == RecordType.PTR || type == RecordType.ANY) {
                    matches = true;
                }
            }

            // Check if query is for our hostname (A record)
            String hostname = (serviceRecord.targetHostname() + "." + serviceRecord.domain() + ".")
                    .toLowerCase(java.util.Locale.ROOT);
            if (name.equals(hostname) && (type == RecordType.A || type == RecordType.ANY)) {
                matches = true;
            }

            if (matches) {
                respondToQuery(query.header().id(), sender);
                break;
            }
        }
    }

    private void respondToQuery(int transactionId, InetAddress sender) {
        try {
            DnsMessage response = DnsSdRecordBuilder.buildResponse(serviceRecord, transactionId);
            byte[] data = MdnsPacketCodec.encode(response);

            DatagramPacket packet = new DatagramPacket(data, data.length, sender, MdnsPacketCodec.MDNS_PORT);
            socket.send(packet);
        } catch (Exception e) {
            LOG.warn("Failed to respond to mDNS query: {}", e.getMessage());
        }
    }

    /**
     * Returns the service record being advertised.
     */
    public DnsSdServiceRecord serviceRecord() {
        return serviceRecord;
    }

    /**
     * Returns whether this responder is currently running.
     */
    public boolean isRunning() {
        return running;
    }
}
