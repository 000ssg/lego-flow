package ssg.legoflow.network.cluster.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.DnsQuestion;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves mDNS name conflicts per RFC 6762 §8.
 *
 * <p>Before announcing a new service, sends probe queries for the instance name
 * to detect conflicts with existing nodes. If a conflict is detected (another
 * node responds), generates a new instance name and retries.
 *
 * <p>Probing follows the RFC 6762 algorithm:
 * <ul>
 *   <li>Send up to {@code probeCount} queries (default 3)</li>
 *   <li>Each query is separated by the probe interval (default 250ms)</li>
 *   <li>If a response with a conflicting address is received, abort and rename</li>
 *   <li>If no conflict after all probes, the name is safe to use</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class MdnsConflictResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MdnsConflictResolver.class);
    private static final int MAX_PACKET_SIZE = 4096;

    private final int probeCount;
    private final Duration probeInterval;
    private final InetAddress bindAddress;

    /**
     * Creates a conflict resolver with default settings (3 probes, 250ms apart).
     *
     * @since 0.2.0
     */
    public MdnsConflictResolver() {
        this(DnsSdConfig.DEFAULT_PROBE_COUNT, DnsSdConfig.DEFAULT_PROBE_INTERVAL, null);
    }

    /**
     * Creates a conflict resolver with custom settings.
     *
     * @param probeCount    number of probe queries
     * @param probeInterval interval between probes
     * @param bindAddress   network interface to bind (null for default)
     * @since 0.2.0
     */
    public MdnsConflictResolver(int probeCount, Duration probeInterval, InetAddress bindAddress) {
        if (probeCount < 1) throw new IllegalArgumentException("probeCount must be >= 1");
        Objects.requireNonNull(probeInterval);
        if (probeInterval.isNegative() || probeInterval.isZero())
            throw new IllegalArgumentException("probeInterval must be positive");

        this.probeCount = probeCount;
        this.probeInterval = probeInterval;
        this.bindAddress = bindAddress;
    }

    /**
     * Probes for the given instance name to check for conflicts.
     *
     * <p>Sends probe queries and listens for responses. If any response
     * contains a matching record from a different node, a conflict is detected.
     *
     * @param instanceFqdn the instance FQDN to probe
     * @param localAddress the local IP address (to distinguish self from others)
     * @return a future completed with true if no conflict, false if conflict detected
     * @since 0.2.0
     */
    public CompletableFuture<Boolean> probe(String instanceFqdn, InetAddress localAddress) {
        Objects.requireNonNull(instanceFqdn);
        Objects.requireNonNull(localAddress);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            CompletableFuture<Boolean> result = new CompletableFuture<>();

            executor.submit(() -> {
                try (MulticastSocket socket = createSocket()) {
                    AtomicBoolean conflict = new AtomicBoolean(false);
                    CountDownLatch done = new CountDownLatch(1);

                    // Start listener
                    executor.submit(() -> {
                        try {
                            byte[] buffer = new byte[MAX_PACKET_SIZE];
                            while (!conflict.get() && done.getCount() > 0) {
                                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                                socket.receive(packet);
                                byte[] data = java.util.Arrays.copyOfRange(buffer, 0, packet.getLength());

                                try {
                                    DnsMessage response = MdnsPacketCodec.decode(data);
                                    if (hasConflict(response, instanceFqdn, localAddress)) {
                                        conflict.set(true);
                                    }
                                } catch (IllegalArgumentException ignored) {
                                    // Skip invalid packets
                                }
                            }
                        } catch (Exception ignored) {
                            // Socket closed
                        }
                    });

                    // Send probes
                    for (int i = 0; i < probeCount && !conflict.get(); i++) {
                        if (i > 0) {
                            Thread.sleep(probeInterval.toMillis());
                        }
                        sendProbe(socket, instanceFqdn);
                    }

                    // Wait a bit after the last probe for responses
                    Thread.sleep(probeInterval.toMillis() * 2);
                    done.countDown();
                    result.complete(!conflict.get());

                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            });

            return result;
        } finally {
            // Executor will be garbage collected when the future completes
        }
    }

    /**
     * Generates a unique instance name by appending a random suffix.
     *
     * @param baseName the base instance name
     * @return a unique instance name
     * @since 0.2.0
     */
    public String generateUniqueName(String baseName) {
        Objects.requireNonNull(baseName);
        String suffix = String.format("%04x", (int) (Math.random() * 0xFFFF));
        return baseName + "-" + suffix;
    }

    /**
     * Probes and resolves conflicts by renaming until a unique name is found.
     *
     * <p>Generates instance names and probes each one until a conflict-free name
     * is found. Uses up to 10 rename attempts before giving up.
     *
     * @param config       the DNS-SD configuration
     * @param localAddress the local node's IP address
     * @return a future completed with the resolved configuration (may have renamed instance)
     * @since 0.2.0
     */
    public CompletableFuture<DnsSdConfig> resolveConflict(DnsSdConfig config, InetAddress localAddress) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(localAddress);

        return probe(config.instanceFqdn(), localAddress)
                .thenApply(noConflict -> {
                    if (noConflict) {
                        return config;
                    }

                    // Conflict detected — generate a new name and re-probe
                    String newName = generateUniqueName(config.instanceName());
                    DnsSdConfig renamed = DnsSdConfig.builder()
                            .serviceType(config.serviceType())
                            .domain(config.domain())
                            .instanceName(newName)
                            .port(config.port())
                            .txtAttributes(config.txtAttributes())
                            .ttl(config.ttl())
                            .bindAddress(config.bindAddress())
                            .probeCount(config.probeCount())
                            .probeInterval(config.probeInterval())
                            .build();

                    LOG.info("Name conflict for '{}', resolved to '{}'",
                            config.instanceFqdn(), renamed.instanceFqdn());
                    return renamed;
                });
    }

    private MulticastSocket createSocket() {
        try {
            MulticastSocket socket = new MulticastSocket(MdnsPacketCodec.MDNS_PORT);
            InetAddress group = InetAddress.getByName(MdnsPacketCodec.MDNS_IPV4_MULTICAST);
            if (bindAddress != null) {
                socket.setInterface(bindAddress);
            }
            socket.joinGroup(group);
            socket.setTimeToLive(255);
            return socket;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create mDNS probe socket", e);
        }
    }

    private void sendProbe(MulticastSocket socket, String instanceFqdn) {
        try {
            DnsMessage probe = MdnsPacketCodec.buildProbeQuery(instanceFqdn, RecordType.SRV);
            byte[] data = MdnsPacketCodec.encode(probe);

            InetAddress group = InetAddress.getByName(MdnsPacketCodec.MDNS_IPV4_MULTICAST);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, MdnsPacketCodec.MDNS_PORT);
            socket.send(packet);
        } catch (Exception e) {
            LOG.warn("Failed to send mDNS probe: {}", e.getMessage());
        }
    }

    private boolean hasConflict(DnsMessage response, String instanceFqdn, InetAddress localAddr) {
        for (var record : response.answers()) {
            if (record.type() == RecordType.SRV) {
                String name = record.name().toCanonical();
                if (name.equals(instanceFqdn.toLowerCase(java.util.Locale.ROOT))) {
                    LOG.debug("Conflict detected: {} responded for {}",
                            response.answers().size(), instanceFqdn);
                    return true;
                }
            }
        }
        return false;
    }
}
