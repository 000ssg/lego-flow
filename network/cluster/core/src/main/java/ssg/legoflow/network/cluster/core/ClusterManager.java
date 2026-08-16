package ssg.legoflow.network.cluster.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import ssg.legoflow.blocks.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.blocks.DefaultContext;

/**
 * Default cluster manager that coordinates discovery, state, messaging,
 * and health checking for a single node.
 *
 * The manager maintains a membership table, runs periodic heartbeats,
 * detects failures via heartbeat misses, and broadcasts membership events.
 */
public class ClusterManager implements ClusterMembership, AutoCloseable {

    private final ClusterConfig config;
    private final ClusterNode localNode;
    private final ClusterTransport transport;
    private final ClusterHealthChecker healthChecker;
    private final Map<String, ClusterNode> members = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    private final List<ClusterEventListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<ClusterNode> leader = new AtomicReference<>();
    private final Context context = new DefaultContext();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile ScheduledFuture<?> healthTask;
    private volatile boolean running = false;
    private final Map<String, Integer> missCounters = new ConcurrentHashMap<>();

    /**
     * Creates a new ClusterManager.
     *
     * @param localNode  the local node descriptor
     * @param config     cluster configuration
     * @param transport  transport for sending messages
     * @param healthChecker health checker for probing nodes
     */
    public ClusterManager(ClusterNode localNode, ClusterConfig config,
                          ClusterTransport transport, ClusterHealthChecker healthChecker) {
        this.localNode = Objects.requireNonNull(localNode);
        this.config = Objects.requireNonNull(config);
        this.transport = Objects.requireNonNull(transport);
        this.healthChecker = Objects.requireNonNull(healthChecker);
    }

    /**
     * Starts the cluster manager: registers the local node and begins
     * heartbeat and health check cycles.
     */
    public void start() {
        if (running) return;
        running = true;

        members.put(localNode.id(), localNode);
        lastHeartbeat.put(localNode.id(), Instant.now());
        missCounters.remove(localNode.id());

        // Start heartbeat loop
        heartbeatTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                config.heartbeatInterval().toMillis(),
                config.heartbeatInterval().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS);

        // Start health check loop
        healthTask = scheduler.scheduleAtFixedRate(
                this::checkHealth,
                healthChecker.defaultInterval().toMillis(),
                healthChecker.defaultInterval().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the cluster manager: sends goodbye, cancels scheduled tasks.
     */
    @Override
    public void close() {
        if (!running) return;
        running = false;

        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (healthTask != null) healthTask.cancel(false);
        scheduler.shutdown();

        leave();

        try {
            if (transport != null) transport.close();
        } catch (Exception ignored) {}
    }

    @Override
    public ClusterNode localNode() {
        return localNode;
    }

    @Override
    public ClusterStatus status() {
        return ClusterStatus.of(members.values(), leader.get());
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
        ClusterNode leavingNode = localNode.withStatus(ClusterNodeStatus.LEAVING);
        members.put(localNode.id(), leavingNode);

        // Broadcast goodbye
        byte[] goodbye = ("BYE:" + localNode.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        transport.broadcast(localNode, goodbye);

        members.remove(localNode.id());
        lastHeartbeat.remove(localNode.id());
        missCounters.remove(localNode.id());

        fireEvent(new ClusterEvent.NodeLeft(localNode, Instant.now()));
    }

    @Override
    public void fireEvent(ClusterEvent event) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                context.getLogger().error("ClusterEventListener failed", e);
            }
        }
    }

    /**
     * Processes an incoming heartbeat from a remote node.
     *
     * @param node the node sending the heartbeat
     */
    public void processHeartbeat(ClusterNode node) {
        if (node.id().equals(localNode.id())) return; // skip self

        ClusterNode previous = members.get(node.id());
        if (previous == null) {
            // New node joining
            ClusterNode activeNode = ClusterNode.builder()
                    .id(node.id())
                    .host(node.host())
                    .port(node.port())
                    .role(node.role())
                    .status(ClusterNodeStatus.ACTIVE)
                    .build();
            members.put(node.id(), activeNode);
            lastHeartbeat.put(node.id(), Instant.now());
            missCounters.remove(node.id());
            fireEvent(new ClusterEvent.NodeJoined(activeNode, Instant.now()));
        } else if (previous.status() != ClusterNodeStatus.ACTIVE) {
            // Node recovered
            ClusterNode recovered = previous.withStatus(ClusterNodeStatus.ACTIVE);
            members.put(node.id(), recovered);
            lastHeartbeat.put(node.id(), Instant.now());
            missCounters.remove(node.id());
            fireEvent(new ClusterEvent.NodeRecovered(recovered, Instant.now()));
        }

        lastHeartbeat.put(node.id(), Instant.now());
        missCounters.remove(node.id());
    }

    /**
     * Processes a goodbye message from a remote node.
     *
     * @param nodeId the ID of the leaving node
     */
    public void processGoodbye(String nodeId) {
        ClusterNode node = members.remove(nodeId);
        if (node != null) {
            lastHeartbeat.remove(nodeId);
            missCounters.remove(nodeId);
            fireEvent(new ClusterEvent.NodeLeft(node, Instant.now()));
        }
    }

    /**
     * Simulates a node failure for testing purposes.
     * Marks the node as FAILED and fires a NodeFailed event.
     *
     * @param nodeId the ID of the node to fail
     */
    public void simulateFailure(String nodeId) {
        ClusterNode current = members.get(nodeId);
        if (current != null) {
            ClusterNode failed = current.withStatus(ClusterNodeStatus.FAILED);
            members.put(nodeId, failed);
            fireEvent(new ClusterEvent.NodeFailed(failed, Instant.now(), "Simulated failure"));
        }
    }

    private void sendHeartbeat() {
        if (!running) return;
        byte[] heartbeat = ("HB:" + localNode.id() + ":" + localNode.port())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        transport.broadcast(localNode, heartbeat);

        // Check for missed heartbeats
        detectFailures();
    }

    private void detectFailures() {
        Instant now = Instant.now();
        for (String nodeId : new java.util.ArrayList<>(members.keySet())) {
            if (nodeId.equals(localNode.id())) continue;

            ClusterNode node = members.get(nodeId);
            if (node == null) continue;
            if (node.status() != ClusterNodeStatus.ACTIVE) continue;

            Instant last = lastHeartbeat.get(nodeId);
            if (last == null) continue;

            Duration elapsed = Duration.between(last, now);
            int threshold = config.heartbeatFailureThreshold();
            Duration maxMiss = config.heartbeatInterval().multipliedBy(threshold);

            if (elapsed.compareTo(maxMiss) >= 0) {
                // Move to SUSPECT first, then FAILED
                int misses = missCounters.getOrDefault(nodeId, 0) + 1;
                missCounters.put(nodeId, misses);

                if (misses == 1) {
                    ClusterNode suspect = node.withStatus(ClusterNodeStatus.SUSPECT);
                    members.put(nodeId, suspect);
                } else if (misses >= threshold) {
                    ClusterNode failed = members.get(nodeId);
                    if (failed != null && failed.status() != ClusterNodeStatus.FAILED) {
                        ClusterNode failedNode = failed.withStatus(ClusterNodeStatus.FAILED);
                        members.put(nodeId, failedNode);
                        fireEvent(new ClusterEvent.NodeFailed(failedNode, now,
                                "Heartbeat timeout after " + elapsed + "s"));
                    }
                }
            }
        }
    }

    private void checkHealth() {
        if (!running) return;
        for (ClusterNode node : new ArrayList<>(members.values())) {
            if (node.id().equals(localNode.id())) continue;
            healthChecker.check(node).thenAccept(healthy -> {
                if (!healthy && node.status() == ClusterNodeStatus.ACTIVE) {
                    ClusterNode failed = node.withStatus(ClusterNodeStatus.FAILED);
                    members.put(node.id(), failed);
                    fireEvent(new ClusterEvent.NodeFailed(failed, Instant.now(), "Health check failed"));
                }
            });
        }
    }

    /**
     * Sets the current leader. Used by leader election protocols.
     *
     * @param newLeader the new leader node
     */
    public void setLeader(ClusterNode newLeader) {
        ClusterNode previous = leader.getAndSet(newLeader);
        if (previous != null && !previous.equals(newLeader)) {
            fireEvent(new ClusterEvent.LeaderChanged(previous, newLeader, Instant.now()));
        }
    }

    /**
     * Returns the current leader, or null if none elected.
     */
    public ClusterNode getLeader() {
        return leader.get();
    }

    /**
     * Returns whether the manager is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the members map (read-only view).
     */
    public Map<String, ClusterNode> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    /**
     * Returns the event listeners list (read-only view).
     */
    public List<ClusterEventListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }
}
