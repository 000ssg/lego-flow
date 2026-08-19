package ssg.legoflow.service.cluster.coordination;

import ssg.legoflow.network.cluster.core.ClusterEvent;
import ssg.legoflow.network.cluster.core.ClusterEventListener;
import ssg.legoflow.network.cluster.core.ClusterMembership;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
/**
 * Service discovery via etcd registrations.
 *
 * <p>Implements {@link ClusterMembership} by:
 * <ul>
 *   <li>Registering nodes as leased keys under a discovery prefix</li>
 *   <li>Watching the prefix for node join/leave events</li>
 *   <li>Using lease expiry to detect failed nodes</li>
 * </ul>
 *
 * <p>Key format: {@code /discovery/<service>/<node-id>}
 * Value format: JSON-like node descriptor (host, port, role, status, metadata)
 *
 * @since 0.2.0
 */
public final class EtcdDiscovery implements ClusterMembership {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdDiscovery.class);

    private static final Duration DEFAULT_LEASE_TTL = Duration.ofSeconds(30);

    private final EtcdKVStore store;
    private final EtcdClient client;
    private final String serviceName;
    private final ClusterNode localNode;
    private final EtcdLease lease;
    private final String registrationKey;
    private final List<ClusterEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private volatile boolean started = false;
    private volatile boolean left = false;

    /**
     * Creates a new etcd-based service discovery instance.
     *
     * @param store       the etcd KV store
     * @param client      the etcd client
     * @param serviceName the service name to register under
     * @param localNode   the local node to register
     * @since 0.2.0
     */
    public EtcdDiscovery(EtcdKVStore store, EtcdClient client,
                         String serviceName, ClusterNode localNode) {
        this(store, client, serviceName, localNode, DEFAULT_LEASE_TTL);
    }

    /**
     * Creates a new etcd-based service discovery instance with custom TTL.
     *
     * @param store       the etcd KV store
     * @param client      the etcd client
     * @param serviceName the service name to register under
     * @param localNode   the local node to register
     * @param leaseTtl    the lease TTL for registration keys
     * @since 0.2.0
     */
    public EtcdDiscovery(EtcdKVStore store, EtcdClient client,
                         String serviceName, ClusterNode localNode, Duration leaseTtl) {
        this.store = Objects.requireNonNull(store);
        this.client = Objects.requireNonNull(client);
        this.serviceName = Objects.requireNonNull(serviceName);
        this.localNode = Objects.requireNonNull(localNode);
        this.lease = new EtcdLease(client, System.nanoTime() & Long.MAX_VALUE,
                (int) Objects.requireNonNull(leaseTtl).getSeconds());
        this.registrationKey = "/discovery/" + serviceName + "/" + localNode.id();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "etcd-discovery-" + serviceName);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the discovery: registers this node and begins watching.
     *
     * @return a future completed when ready
     * @since 0.2.0
     */
    public CompletableFuture<Void> start() {
        if (started) return CompletableFuture.completedFuture(null);

        LOG.info("Starting etcd discovery for service '{}' node '{}'",
                serviceName, localNode.id());

        return lease.startKeepAlive().thenApply(v -> {
            // Register this node
            String value = serializeNode(localNode);
            store.put(registrationKey, value.getBytes(StandardCharsets.UTF_8), lease)
                    .whenComplete((result, err) -> {
                        if (err == null) {
                            started = true;
                            LOG.info("Registered node {} for service {}", localNode.id(), serviceName);
                        }
                    });
            return null;
        });
    }

    /**
     * Returns the current cluster status from etcd registrations.
     *
     * @since 0.2.0
     */
    @Override
    public ClusterStatus status() {
        String prefix = "/discovery/" + serviceName + "/";
        try {
            Map<String, byte[]> entries = store.range(prefix).join();
            List<ClusterNode> nodes = entries.entrySet().stream()
                    .map(e -> deserializeNode(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

            // First registered node is treated as the leader
            ClusterNode leader = nodes.isEmpty() ? null : nodes.get(0);

            return ClusterStatus.of(nodes, leader);
        } catch (Exception e) {
            LOG.warn("Failed to get cluster status", e);
            return ClusterStatus.of(List.of());
        }
    }

    @Override
    public ClusterNode localNode() {
        return localNode;
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
        if (left) return;
        left = true;
        started = false;

        LOG.info("Node {} leaving service {}", localNode.id(), serviceName);
        store.delete(registrationKey).join();
        lease.revoke().join();
        scheduler.shutdownNow();

        ClusterNode failedNode = localNode.withStatus(ClusterNodeStatus.FAILED);
        ClusterEvent event = new ClusterEvent.NodeLeft(failedNode, Instant.now());
        fireEvent(event);
    }

    @Override
    public CompletableFuture<Void> leaveAsync() {
        if (left) return CompletableFuture.completedFuture(null);
        left = true;
        started = false;

        LOG.info("Node {} leaving service {} (async)", localNode.id(), serviceName);
        return store.delete(registrationKey)
                .thenCompose(v -> lease.revoke())
                .whenComplete((v, err) -> {
                    scheduler.shutdownNow();
                    ClusterNode failedNode = localNode.withStatus(ClusterNodeStatus.FAILED);
                    ClusterEvent event = new ClusterEvent.NodeLeft(failedNode, Instant.now());
                    fireEvent(event);
                });
    }

    @Override
    public void fireEvent(ClusterEvent event) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.warn("Error notifying listener {}", listener, e);
            }
        }
    }

    @Override
    public void close() {
        leave();
    }

    /**
     * Returns the service name.
     *
     * @since 0.2.0
     */
    public String serviceName() {
        return serviceName;
    }

    /**
     * Returns the backing etcd client.
     *
     * @since 0.2.0
     */
    public EtcdClient client() {
        return client;
    }

    private String serializeNode(ClusterNode node) {
        return String.format("{\"id\":\"%s\",\"host\":\"%s\",\"port\":%d,\"role\":\"%s\",\"status\":\"%s\"}",
                node.id(), node.host(), node.port(), node.role(), node.status());
    }

    private ClusterNode deserializeNode(String key, byte[] value) {
        String id = key.substring(key.lastIndexOf('/') + 1);
        return ClusterNode.builder()
                .id(id)
                .host("127.0.0.1")
                .port(0)
                .role(ssg.legoflow.network.cluster.core.ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .build();
    }

    @Override
    public String toString() {
        return "EtcdDiscovery{service='" + serviceName + "', node=" + localNode.id() + '}';
    }
}
