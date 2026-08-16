package ssg.legoflow.messaging.nats.cluster;

import ssg.legoflow.messaging.nats.client.NatsClient;
import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.ClusterNodeStatus;
import ssg.legoflow.network.cluster.core.ClusterRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Health check bus using NATS for cluster-wide heartbeat exchange.
 *
 * <p>Each node periodically publishes a heartbeat to a well-known subject.
 * Other nodes receive heartbeats and can detect when a peer stops sending.
 *
 * <p>Heartbeat messages are JSON-encoded:
 * {@code {"nodeId":"...", "host":"...", "port":..., "timestamp":..., "status":"..."}}
 *
 * @since 0.2.0
 */
public final class NatsClusterHealthBus implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NatsClusterHealthBus.class);

    private final NatsClusterBus bus;
    private final Duration heartbeatInterval;
    private final ClusterNode self;
    private final AtomicReference<Consumer<ClusterNode>> healthListener = new AtomicReference<>();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private volatile boolean running = false;
    private volatile boolean closed = false;

    /**
     * Creates a health bus.
     *
     * @param bus the NATS cluster bus
     * @param heartbeatInterval interval between heartbeats
     * @param host this node's host
     * @param port this node's port
     */
    public NatsClusterHealthBus(NatsClusterBus bus, Duration heartbeatInterval,
                                  String host, int port) {
        this.bus = bus;
        this.heartbeatInterval = heartbeatInterval;
        this.self = ClusterNode.builder()
                .id(bus.nodeId())
                .host(host)
                .port(port)
                .role(ClusterRole.BOTH)
                .status(ClusterNodeStatus.ACTIVE)
                .build();
        this.scheduler = createScheduler(bus.nodeId());

        // Subscribe to heartbeats from other nodes
        bus.subscribe("heartbeat.>", msg -> {
            String payload = new String(msg.payload(), StandardCharsets.UTF_8);
            HeartbeatData data = parseHeartbeat(payload);
            if (data != null && !data.nodeId().equals(bus.nodeId())) {
                ClusterNode peer = ClusterNode.builder()
                        .id(data.nodeId())
                        .host(data.host())
                        .port(data.port())
                        .role(ClusterRole.BOTH)
                        .status(ClusterNodeStatus.ACTIVE)
                        .build();
                Consumer<ClusterNode> listener = healthListener.get();
                if (listener != null) {
                    try {
                        listener.accept(peer);
                    } catch (Exception e) {
                        LOG.warn("Health listener error for peer {}", peer.id(), e);
                    }
                }
            }
        });
    }

    private ScheduledExecutorService createScheduler(String nodeId) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nats-health-bus-" + nodeId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the heartbeat loop.
     */
    public void start() {
        if (running) return;

        // Recreate scheduler if it was shut down (e.g., after close)
        if (closed || scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = createScheduler(bus.nodeId());
            closed = false;
        }

        running = true;

        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                LOG.warn("Error sending heartbeat", e);
            }
        }, 0, heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the heartbeat loop.
     */
    public void stop() {
        running = false;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
    }

    /**
     * Sets the health listener called when peer heartbeats are received.
     */
    public void setHealthListener(Consumer<ClusterNode> listener) {
        healthListener.set(listener);
    }

    /**
     * Returns this node.
     */
    public ClusterNode self() {
        return self;
    }

    /**
     * Returns whether the heartbeat loop is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sends a manual heartbeat (useful for testing and demos).
     */
    public void sendHeartbeat() {
        HeartbeatData data = new HeartbeatData(
                self.id(), self.host(), self.port(),
                Instant.now().toEpochMilli(), "active");
        bus.publish("heartbeat." + self.id(), encodeHeartbeat(data));
    }

    private String encodeHeartbeat(HeartbeatData data) {
        return String.format("{\"nodeId\":\"%s\",\"host\":\"%s\",\"port\":%d,\"timestamp\":%d,\"status\":\"%s\"}",
                data.nodeId(), data.host(), data.port(), data.timestamp(), data.status());
    }

    private HeartbeatData parseHeartbeat(String json) {
        try {
            String nodeId = extractString(json, "nodeId");
            String host = extractString(json, "host");
            String portStr = extractRaw(json, "port");
            String tsStr = extractRaw(json, "timestamp");
            String status = extractString(json, "status");
            if (nodeId == null || host == null || portStr == null || tsStr == null) {
                return null;
            }
            return new HeartbeatData(
                    nodeId, host, Integer.parseInt(portStr),
                    Long.parseLong(tsStr), status != null ? status : "active");
        } catch (Exception e) {
            return null;
        }
    }

    private String extractString(String json, String key) {
        String prefix = "\"" + key + "\":\"";
        int start = json.indexOf(prefix);
        if (start == -1) return null;
        start += prefix.length();
        int end = json.indexOf('"', start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private String extractRaw(String json, String key) {
        String prefix = "\"" + key + "\":";
        int start = json.indexOf(prefix);
        if (start == -1) return null;
        start += prefix.length();
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char c = json.charAt(start);
        if (c == '"') {
            // String value — handled by extractString
            return extractString(json, key);
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }

    @Override
    public void close() {
        running = false;
        closed = true;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Heartbeat data structure.
     */
    private record HeartbeatData(String nodeId, String host, int port,
                                   long timestamp, String status) {}
}
