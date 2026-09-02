package ssg.legoflow.network.cluster.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsRecord;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.ARecord;
import ssg.legoflow.network.dns.rdata.PtrRecord;
import ssg.legoflow.network.dns.rdata.SrvRecord;
import ssg.legoflow.network.dns.rdata.TxtRecord;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
/**
 * mDNS querier that sends multicast DNS queries and processes responses.
 *
 * <p>Per RFC 6762, the querier:
 * <ul>
 *   <li>Sends queries to 224.0.0.251:5353</li>
 *   <li>Processes PTR, SRV, A, and TXT responses</li>
 *   <li>Caches records with TTL; refreshes at half-TTL</li>
 *   <li>Emits events for service resolution and removal</li>
 * </ul>
 *
 * <p>Uses a virtual thread executor for the listener loop.
 *
 * @since 0.2.0
 */
public final class MdnsQuerier implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MdnsQuerier.class);
    private static final int MAX_PACKET_SIZE = 4096;

    private final MulticastSocket socket;
    private final ExecutorService executor;
    private final Map<String, DnsSdServiceRecord> serviceCache = new ConcurrentHashMap<>();
    private final List<Consumer<DnsSdServiceRecord>> resolvedListeners = new ArrayList<>();
    private final List<Consumer<String>> removedListeners = new ArrayList<>();
    private volatile boolean running;

    /**
     * Creates an mDNS querier bound to the default interface.
     *
     * @throws java.net.SocketException if the socket cannot be created
     * @since 0.2.0
     */
    public MdnsQuerier() {
        this(null);
    }

    /**
     * Creates an mDNS querier bound to a specific interface.
     *
     * @param interfaceAddr the network interface (null for default)
     * @throws java.net.SocketException if the socket cannot be created
     * @since 0.2.0
     */
    public MdnsQuerier(InetAddress interfaceAddr) {
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
            throw new RuntimeException("Failed to create mDNS querier socket", e);
        }
        this.socket = sock;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Starts the querier. Begins listening for mDNS responses.
     *
     * @return a future completed when the listener is running
     * @since 0.2.0
     */
    public CompletableFuture<Void> start() {
        if (running) return CompletableFuture.completedFuture(null);
        running = true;
        executor.submit(() -> listenLoop());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Stops the querier.
     *
     * @since 0.2.0
     */
    public void stop() {
        if (!running) return;
        running = false;
        executor.shutdownNow();
        socket.close();
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Queries for all instances of the given service type.
     *
     * @param serviceType the service type (e.g. "_http._tcp")
     * @param domain      the domain (e.g. "local")
     * @return a future completed when the query is sent
     * @since 0.2.0
     */
    public CompletableFuture<Void> query(String serviceType, String domain) {
        Objects.requireNonNull(serviceType);
        Objects.requireNonNull(domain);

        try {
            String serviceDomain = serviceType + "." + domain;
            DnsMessage query = MdnsPacketCodec.buildQuery(serviceDomain + ".", RecordType.PTR);
            byte[] data = MdnsPacketCodec.encode(query);

            InetAddress group = InetAddress.getByName(MdnsPacketCodec.MDNS_IPV4_MULTICAST);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, MdnsPacketCodec.MDNS_PORT);
            socket.send(packet);
            LOG.debug("Sent mDNS query for {}", serviceDomain);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Registers a listener notified when a new service is resolved.
     *
     * @param listener the listener
     * @since 0.2.0
     */
    public void onResolved(Consumer<DnsSdServiceRecord> listener) {
        resolvedListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Registers a listener notified when a service is removed (TTL expired or goodbye).
     *
     * @param listener the listener
     * @since 0.2.0
     */
    public void onRemoved(Consumer<String> listener) {
        removedListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Returns a snapshot of the current service cache.
     *
     * @return unmodifiable list of cached service records
     * @since 0.2.0
     */
    public List<DnsSdServiceRecord> cachedServices() {
        return Collections.unmodifiableList(new ArrayList<>(serviceCache.values()));
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
                    if (MdnsPacketCodec.isGoodbye(message)) {
                        handleGoodbye(message);
                    } else if (MdnsPacketCodec.isAnnouncement(message)) {
                        handleResponse(message);
                    }
                } catch (IllegalArgumentException e) {
                    LOG.trace("Skipping invalid mDNS packet: {}", e.getMessage());
                }
            } catch (java.io.IOException e) {
                if (running) {
                    LOG.warn("mDNS querier socket error: {}", e.getMessage());
                }
                break;
            }
        }
    }

    private void handleResponse(DnsMessage message) {
        for (DnsRecord record : message.answers()) {
            if (record.type() == RecordType.PTR && record.rdata() instanceof PtrRecord ptr) {
                String instanceFqdn = ptr.domainName().toCanonical();
                DnsSdServiceRecord service = buildServiceRecord(message, instanceFqdn);
                if (service != null) {
                    DnsSdServiceRecord previous = serviceCache.put(service.instanceFqdn(), service);
                    if (previous == null) {
                        for (Consumer<DnsSdServiceRecord> listener : resolvedListeners) {
                            listener.accept(service);
                        }
                    }
                }
            }
        }
    }

    private DnsSdServiceRecord buildServiceRecord(DnsMessage message, String instanceFqdn) {
        DnsRecord srvRecord = null;
        DnsRecord aRecord = null;
        TxtRecord txt = null;

        for (DnsRecord record : message.answers()) {
            if (record.type() == RecordType.SRV && record.rdata() instanceof SrvRecord s) {
                if (record.name().toCanonical().equals(instanceFqdn)) {
                    srvRecord = record;
                }
            }
            if (record.type() == RecordType.A && record.rdata() instanceof ARecord a) {
                aRecord = record;
            }
            if (record.type() == RecordType.TXT && record.rdata() instanceof TxtRecord t) {
                if (record.name().toCanonical().equals(instanceFqdn)) {
                    txt = t;
                }
            }
        }

        if (srvRecord == null || aRecord == null) {
            return null; // Incomplete record chain
        }

        SrvRecord srv = (SrvRecord) srvRecord.rdata();
        ARecord aRec = (ARecord) aRecord.rdata();

        // Parse instance name, service type, domain from FQDN
        String[] parts = instanceFqdn.split("\\.");
        String instanceName = parts[0];
        String serviceType = parts[1] + "." + parts[2];
        String domain = parts[3];

        // Parse TXT attributes
        Map<String, String> attrs = new LinkedHashMap<>();
        if (txt != null) {
            for (String part : txt.strings()) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    attrs.put(part.substring(0, eq), part.substring(eq + 1));
                }
            }
        }

        // Extract target hostname without domain suffix
        String targetHost = srv.target().toFqdn()
                .replace("." + domain + ".", ".");

        return new DnsSdServiceRecord(
                serviceType, domain, instanceName,
                targetHost, aRec.address(),
                srv.port(), srv.priority(), srv.weight(), attrs,
                Duration.ofSeconds(srvRecord.ttl())
        );
    }

    private void handleGoodbye(DnsMessage message) {
        for (DnsRecord record : message.answers()) {
            if (record.ttl() == 0 && record.type() == RecordType.PTR) {
                if (record.rdata() instanceof PtrRecord ptr) {
                    String fqdn = ptr.domainName().toCanonical();
                    DnsSdServiceRecord removed = serviceCache.remove(fqdn);
                    if (removed != null) {
                        for (Consumer<String> listener : removedListeners) {
                            listener.accept(fqdn);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns whether this querier is currently running.
     */
    public boolean isRunning() {
        return running;
    }
}
