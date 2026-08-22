package ssg.legoflow.network.cluster.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.network.cluster.core.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
/**
 * DNS-SD based cluster membership implementation per RFC 6762/8305.
 *
 * <p>Nodes announce themselves via multicast DNS and discover peers
 * without any external infrastructure. Implements the {@link ClusterMembership}
 * SPI by mapping DNS-SD service records to cluster nodes.
 *
 * @since 0.2.0
 */
public final class DnsSdDiscovery implements ClusterMembership {

    private static final Logger LOG = LoggerFactory.getLogger(DnsSdDiscovery.class);

    private final DnsSdConfig config;
    private final Map<String, ClusterNode> members = new ConcurrentHashMap<>();
    private final List<ClusterEventListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<ClusterNode> leader = new AtomicReference<>();
    private final AtomicReference<ClusterNode> localNodeRef = new AtomicReference<>();

    private MdnsResponder responder;
    private DnsSdBrowser browser;
    private volatile ClusterNodeStatus status = ClusterNodeStatus.ACTIVE;

    /**
     * Creates a DNS-SD discovery instance from the given configuration.
     *
     * @param config the DNS-SD configuration
     * @throws IllegalArgumentException if configuration is invalid
     * @since 0.2.0
     */
    public DnsSdDiscovery(DnsSdConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.config = config;

        // Build the initial local node descriptor
        this.localNodeRef.set(ClusterNode.builder()
                .id(config.instanceName())
                .host("127.0.0.1")
                .port(config.port())
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .addMetadata("dns-sd-service", config.serviceDomain())
                .addMetadata("dns-sd-instance", config.instanceName())
                .build());
    }

    /**
     * Starts the discovery: announces the service and starts browsing.
     *
     * @since 0.2.0
     */
    public void start() {
        try {
            InetAddress bindAddr = config.bindAddress();

            // Build the service record from config
            DnsSdServiceRecord serviceRecord = buildServiceRecord(config, bindAddr);

            // Start the responder (announcement + query response)
            this.responder = new MdnsResponder(serviceRecord, bindAddr);
            responder.start();

            // Start browsing for peers
            this.browser = new DnsSdBrowser(
                    config.serviceType(), config.domain(), bindAddr);

            // Map discovered services to cluster nodes
            browser.onEvent(event -> {
                if (event.type() == DnsSdBrowser.DnsSdBrowserEvent.Type.ADDED) {
                    DnsSdServiceRecord rec = event.record();
                    // Skip self
                    if (!rec.instanceFqdn().equals(config.instanceFqdn())) {
                        ClusterNode node = toClusterNode(rec);
                        members.put(node.id(), node);
                        fireEvent(new ClusterEvent.NodeJoined(node, Instant.now()));
                    }
                } else if (event.type() == DnsSdBrowser.DnsSdBrowserEvent.Type.REMOVED) {
                    DnsSdServiceRecord rec = event.record();
                    ClusterNode node = members.remove(rec.instanceName());
                    if (node != null) {
                        fireEvent(new ClusterEvent.NodeLeft(node, Instant.now()));
                    }
                }
            });

            browser.start();

            LOG.info("DNS-SD discovery started: {} (service: {})",
                    localNode().id(), config.serviceDomain());

        } catch (Exception e) {
            LOG.error("Failed to start DNS-SD discovery: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start DNS-SD discovery", e);
        }
    }

    @Override
    public ClusterNode localNode() {
        return localNodeRef.get();
    }

    @Override
    public ClusterStatus status() {
        Set<ClusterNode> all = new java.util.HashSet<>(members.values());
        ClusterNode local = localNodeRef.get();
        if (local != null) all.add(local);
        return ClusterStatus.of(all, leader.get());
    }

    @Override
    public void addListener(ClusterEventListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeListener(ClusterEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void leave() {
        if (status == ClusterNodeStatus.LEAVING) {
            return;
        }

        this.status = ClusterNodeStatus.LEAVING;
        LOG.info("DNS-SD node leaving: {}", localNode().id());

        if (browser != null) browser.stop();
        if (responder != null) responder.stop();

        fireEvent(new ClusterEvent.NodeLeft(localNode(), Instant.now()));
    }

    @Override
    public void close() {
        leave();
    }

    @Override
    public void fireEvent(ClusterEvent event) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.warn("Cluster event listener error: {}", e.getMessage());
            }
        }
    }

    private DnsSdServiceRecord buildServiceRecord(DnsSdConfig cfg, InetAddress bindAddr) {
        InetAddress targetAddr = bindAddr;
        if (targetAddr == null) {
            try {
                targetAddr = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                throw new RuntimeException("Cannot resolve localhost", e);
            }
        }

        return DnsSdServiceRecord.builder()
                .serviceType(cfg.serviceType())
                .domain(cfg.domain())
                .instanceName(cfg.instanceName())
                .targetHostname(targetAddr.getHostAddress())
                .targetAddress(targetAddr)
                .port(cfg.port())
                .txtAttributes(cfg.txtAttributes())
                .ttl(cfg.ttl())
                .build();
    }

    private ClusterNode toClusterNode(DnsSdServiceRecord rec) {
        Map<String, String> meta = new LinkedHashMap<>(rec.txtAttributes());
        meta.put("host", rec.targetHostname());
        meta.put("port", String.valueOf(rec.port()));

        return ClusterNode.builder()
                .id(rec.instanceName())
                .host(rec.targetHostname())
                .port(rec.port())
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .metadata(meta)
                .build();
    }

    /**
     * Returns the DNS-SD configuration.
     */
    public DnsSdConfig config() {
        return config;
    }

    /**
     * Returns the current member set (including self).
     */
    public Set<ClusterNode> members() {
        Set<ClusterNode> all = ConcurrentHashMap.newKeySet();
        all.add(localNode());
        all.addAll(members.values());
        return Collections.unmodifiableSet(all);
    }
}
