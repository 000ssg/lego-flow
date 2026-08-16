package ssg.legoflow.rpc.grpc.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Health checker for gRPC backends.
 *
 * <p>Periodically probes each backend and updates its health status.
 * A backend is marked NOT_SERVING after consecutive failures.
 *
 * @since 0.2.0
 */
public final class GrpcHealthChecker implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcHealthChecker.class);

    private final Map<String, HealthStatus> healthMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Duration checkInterval;
    private final int consecutiveFailuresThreshold;
    private final Function<String, Boolean> probeFunction;
    private final IntConsumer statusChangedListener;
    private volatile boolean running = false;
    private ScheduledFuture<?> checkTask;

    /**
     * Creates a health checker.
     *
     * @param checkInterval              how often to check each backend
     * @param consecutiveFailuresThreshold failures before marking NOT_SERVING
     * @param probeFunction              function that probes a backend (true = healthy)
     * @param statusChangedListener      callback when status changes
     */
    public GrpcHealthChecker(Duration checkInterval,
                              int consecutiveFailuresThreshold,
                              Function<String, Boolean> probeFunction,
                              IntConsumer statusChangedListener) {
        this.checkInterval = Objects.requireNonNull(checkInterval);
        this.consecutiveFailuresThreshold = consecutiveFailuresThreshold;
        this.probeFunction = Objects.requireNonNull(probeFunction);
        this.statusChangedListener = statusChangedListener != null ? statusChangedListener : i -> {};
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "grpc-health-checker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts periodic health checks.
     */
    public void start() {
        if (running) return;
        running = true;

        this.checkTask = scheduler.scheduleAtFixedRate(() -> {
            for (String nodeId : healthMap.keySet()) {
                check(nodeId);
            }
        }, checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Registers a backend for health checking.
     *
     * @param nodeId the backend node ID
     */
    public void register(String nodeId) {
        healthMap.putIfAbsent(nodeId, HealthStatus.SERVING);
        failureCount.putIfAbsent(nodeId, 0);
    }

    /**
     * Removes a backend from health checking.
     *
     * @param nodeId the backend node ID
     */
    public void unregister(String nodeId) {
        healthMap.remove(nodeId);
        failureCount.remove(nodeId);
    }

    private void check(String nodeId) {
        try {
            boolean healthy = probeFunction.apply(nodeId);
            if (healthy) {
                updateStatus(nodeId, HealthStatus.SERVING, 0);
            } else {
                int count = failureCount.getOrDefault(nodeId, 0) + 1;
                if (count >= consecutiveFailuresThreshold) {
                    updateStatus(nodeId, HealthStatus.NOT_SERVING, count);
                } else {
                    failureCount.put(nodeId, count);
                }
            }
        } catch (Exception e) {
            int count = failureCount.getOrDefault(nodeId, 0) + 1;
            LOG.warn("Health check failed for {}: {}", nodeId, e.getMessage());
            if (count >= consecutiveFailuresThreshold) {
                updateStatus(nodeId, HealthStatus.UNREACHABLE, count);
            } else {
                failureCount.put(nodeId, count);
            }
        }
    }

    private void updateStatus(String nodeId, HealthStatus status, int failures) {
        HealthStatus old = healthMap.put(nodeId, status);
        failureCount.put(nodeId, failures);
        if (old != status) {
            LOG.info("Backend {} health changed: {} -> {}", nodeId, old, status);
            statusChangedListener.accept(failures);
        }
    }

    /**
     * Returns the health status of a backend.
     *
     * @param nodeId the backend node ID
     * @return the health status, or SERVING if unknown
     */
    public HealthStatus status(String nodeId) {
        return healthMap.getOrDefault(nodeId, HealthStatus.SERVING);
    }

    @Override
    public void close() {
        running = false;
        if (checkTask != null) {
            checkTask.cancel(false);
        }
        scheduler.shutdownNow();
    }
}
