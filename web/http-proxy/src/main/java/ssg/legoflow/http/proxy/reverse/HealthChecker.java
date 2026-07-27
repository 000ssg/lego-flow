package ssg.legoflow.http.proxy.reverse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Periodic backend health checker.
 *
 * <p>Runs health checks against registered backend servers at configurable intervals.
 * Backends that fail the health check are marked as unhealthy and excluded from
 * load balancing until they pass again.</p>
 *
 * @since 1.0.0
 */
public class HealthChecker implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HealthChecker.class);

    private final Duration checkInterval;
    private final Duration timeout;
    private final int unhealthyThreshold;
    private final int healthyThreshold;
    private final List<BackendServer> backends = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong checkCount = new AtomicLong(0);
    private Function<BackendServer, Boolean> healthCheckFunction;
    private ScheduledExecutorService scheduler;

    /**
     * Creates a new health checker with default settings.
     *
     * @since 1.0.0
     */
    public HealthChecker() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(5), 3, 2);
    }

    /**
     * Creates a new health checker with the specified configuration.
     *
     * @param checkInterval the interval between health checks
     * @param timeout the timeout for each health check
     * @param unhealthyThreshold consecutive failures before marking unhealthy
     * @param healthyThreshold consecutive successes before marking healthy
     * @since 1.0.0
     */
    public HealthChecker(Duration checkInterval, Duration timeout,
                         int unhealthyThreshold, int healthyThreshold) {
        this.checkInterval = checkInterval;
        this.timeout = timeout;
        this.unhealthyThreshold = unhealthyThreshold;
        this.healthyThreshold = healthyThreshold;
        this.healthCheckFunction = _ -> true; // default: always healthy
    }

    /**
     * Sets the health check function. The function receives a backend server
     * and returns true if the backend is healthy.
     *
     * @param healthCheckFunction the check function
     * @since 1.0.0
     */
    public void setHealthCheckFunction(Function<BackendServer, Boolean> healthCheckFunction) {
        this.healthCheckFunction = healthCheckFunction;
    }

    /**
     * Registers a backend server for health checking.
     *
     * @param backend the backend to monitor
     * @since 1.0.0
     */
    public void addBackend(BackendServer backend) {
        backends.add(backend);
    }

    /**
     * Removes a backend server from health checking.
     *
     * @param backend the backend to stop monitoring
     * @since 1.0.0
     */
    public void removeBackend(BackendServer backend) {
        backends.remove(backend);
    }

    /**
     * Starts the periodic health checking.
     *
     * @since 1.0.0
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "health-checker");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::runChecks,
                    checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
            LOG.info("Health checker started with interval {}", checkInterval);
        }
    }

    /**
     * Stops the periodic health checking.
     *
     * @since 1.0.0
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            LOG.info("Health checker stopped after {} checks", checkCount.get());
        }
    }

    /**
     * Runs a single round of health checks against all backends.
     * This is public to allow manual triggering in tests.
     *
     * @since 1.0.0
     */
    public void runChecks() {
        checkCount.incrementAndGet();
        for (BackendServer backend : backends) {
            try {
                boolean healthy = healthCheckFunction.apply(backend);
                backend.setHealthy(healthy);
                LOG.debug("Health check for {}: {}", backend.getId(), healthy ? "UP" : "DOWN");
            } catch (Exception e) {
                backend.setHealthy(false);
                LOG.warn("Health check failed for {}: {}", backend.getId(), e.getMessage());
            }
        }
    }

    /**
     * Returns whether the health checker is currently running.
     *
     * @return true if running
     * @since 1.0.0
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the total number of health check rounds performed.
     *
     * @return the check count
     * @since 1.0.0
     */
    public long getCheckCount() {
        return checkCount.get();
    }

    /**
     * Returns the registered backends.
     *
     * @return the backend list
     * @since 1.0.0
     */
    public List<BackendServer> getBackends() {
        return List.copyOf(backends);
    }

    /**
     * Returns the check interval.
     *
     * @return the check interval
     * @since 1.0.0
     */
    public Duration getCheckInterval() {
        return checkInterval;
    }

    /**
     * Returns the unhealthy threshold.
     *
     * @return the unhealthy threshold
     * @since 1.0.0
     */
    public int getUnhealthyThreshold() {
        return unhealthyThreshold;
    }

    /**
     * Returns the healthy threshold.
     *
     * @return the healthy threshold
     * @since 1.0.0
     */
    public int getHealthyThreshold() {
        return healthyThreshold;
    }

    @Override
    public void close() {
        stop();
    }
}
