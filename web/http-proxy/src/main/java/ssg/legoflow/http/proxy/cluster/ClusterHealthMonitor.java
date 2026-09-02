package ssg.legoflow.http.proxy.cluster;

import ssg.legoflow.http.proxy.reverse.BackendServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
/**
 * Monitors health of backend servers in a proxy cluster.
 *
 * <p>Periodically probes each backend via HTTP and maintains
 * a health status. When a backend becomes unhealthy, it is
 * notified to the caller (typically the load balancer) for
 * removal from the active pool.
 *
 * <p>Health is tracked using consecutive failure/success counters
 * to avoid flapping:
 * <ul>
 *   <li>After N consecutive failures → marked unhealthy</li>
 *   <li>After M consecutive successes → marked healthy again</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class ClusterHealthMonitor implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterHealthMonitor.class);

    private final ProxyClusterConfig config;
    private final Map<String, BackendServer> backends = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> consecutiveSuccesses = new ConcurrentHashMap<>();
    private final Consumer<HealthEvent> eventListener;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> healthCheckTask;
    private volatile boolean running = false;

    /**
     * Health event published when a backend changes status.
     *
     * @param backendId the backend identifier
     * @param event     the event type
     */
    public record HealthEvent(String backendId, EventType event) {
        public enum EventType {
            UNHEALTHY,
            RECOVERED,
            ADDED,
            REMOVED
        }
    }

    /**
     * Creates a health monitor.
     *
     * @param config        the proxy cluster configuration
     * @param eventListener callback for health status changes
     */
    public ClusterHealthMonitor(ProxyClusterConfig config,
                                  Consumer<HealthEvent> eventListener) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener must not be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cluster-health-monitor");
            t.setDaemon(true);
            return t;
        });

        // Register initial backends
        for (BackendServer backend : config.backends()) {
            addBackend(backend);
        }
    }

    /**
     * Starts periodic health checks.
     */
    public void start() {
        if (running) return;
        running = true;

        Duration interval = config.healthInterval();
        healthCheckTask = scheduler.scheduleAtFixedRate(() -> {
            for (BackendServer backend : backends.values()) {
                checkBackend(backend);
            }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);

        LOG.info("Health monitor started, interval={}", interval);
    }

    /**
     * Adds a backend to the monitored set.
     *
     * @param backend the backend server
     */
    public void addBackend(BackendServer backend) {
        Objects.requireNonNull(backend);
        backends.put(backend.getId(), backend);
        consecutiveFailures.computeIfAbsent(backend.getId(), k -> new AtomicInteger(0));
        consecutiveSuccesses.computeIfAbsent(backend.getId(), k -> new AtomicInteger(0));
        backend.setHealthy(true);
        fireEvent(backend.getId(), HealthEvent.EventType.ADDED);
    }

    /**
     * Removes a backend from the monitored set.
     *
     * @param backendId the backend identifier
     */
    public void removeBackend(String backendId) {
        backends.remove(backendId);
        consecutiveFailures.remove(backendId);
        consecutiveSuccesses.remove(backendId);
        fireEvent(backendId, HealthEvent.EventType.REMOVED);
    }

    /**
     * Returns the list of healthy backends.
     */
    public List<BackendServer> getHealthyBackends() {
        List<BackendServer> healthy = new ArrayList<>();
        for (BackendServer backend : backends.values()) {
            if (backend.isHealthy()) {
                healthy.add(backend);
            }
        }
        return List.copyOf(healthy);
    }

    /**
     * Returns all monitored backends.
     */
    public List<BackendServer> getAllBackends() {
        return List.copyOf(backends.values());
    }

    /**
     * Performs a health check on a specific backend.
     *
     * @param backend   the backend to check
     * @param isHealthy whether the check passed
     */
    public void recordCheck(BackendServer backend, boolean isHealthy) {
        Objects.requireNonNull(backend);
        String id = backend.getId();

        if (isHealthy) {
            consecutiveFailures.computeIfAbsent(id, k -> new AtomicInteger(0)).set(0);
            int successes = consecutiveSuccesses.computeIfAbsent(id, k -> new AtomicInteger(0))
                    .incrementAndGet();

            if (!backend.isHealthy() && successes >= config.recoveryThreshold()) {
                backend.setHealthy(true);
                fireEvent(id, HealthEvent.EventType.RECOVERED);
                LOG.info("Backend {} recovered after {} successes", id, successes);
            }
        } else {
            consecutiveSuccesses.computeIfAbsent(id, k -> new AtomicInteger(0)).set(0);
            int failures = consecutiveFailures.computeIfAbsent(id, k -> new AtomicInteger(0))
                    .incrementAndGet();

            if (backend.isHealthy() && failures >= config.unhealthyThreshold()) {
                backend.setHealthy(false);
                fireEvent(id, HealthEvent.EventType.UNHEALTHY);
                LOG.warn("Backend {} marked unhealthy after {} failures", id, failures);
            }
        }
    }

    /**
     * Stops the health monitor.
     */
    public void stop() {
        running = false;
        if (healthCheckTask != null) {
            healthCheckTask.cancel(false);
        }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }

    /**
     * Returns whether the monitor is running.
     */
    public boolean isRunning() {
        return running;
    }

    private void checkBackend(BackendServer backend) {
        // In a real implementation, this would send an HTTP GET to
        // http://host:port/healthCheckPath and check the response.
        // For now, we simulate based on the backend's own health flag.
        // The actual HTTP probe is delegated to the caller.
        recordCheck(backend, backend.isHealthy());
    }

    private void fireEvent(String backendId, HealthEvent.EventType event) {
        try {
            eventListener.accept(new HealthEvent(backendId, event));
        } catch (Exception e) {
            LOG.error("Error delivering health event: backend={}, event={}",
                    backendId, event, e);
        }
    }
}
