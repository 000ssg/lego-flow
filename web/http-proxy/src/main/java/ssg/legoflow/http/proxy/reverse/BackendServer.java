package ssg.legoflow.http.proxy.reverse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a backend server that the reverse proxy can forward requests to.
 *
 * <p>Tracks health status, active connections, and request statistics.</p>
 *
 * @since 0.1.0
 */
public class BackendServer {

    private final String host;
    private final int port;
    private final int weight;
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final String id;

    /**
     * Creates a new backend server with default weight of 1.
     *
     * @param host the backend hostname
     * @param port the backend port
     * @since 0.1.0
     */
    public BackendServer(String host, int port) {
        this(host, port, 1);
    }

    /**
     * Creates a new backend server with the specified weight.
     *
     * @param host the backend hostname
     * @param port the backend port
     * @param weight the load balancing weight (higher values receive more traffic)
     * @since 0.1.0
     */
    public BackendServer(String host, int port, int weight) {
        this.host = host;
        this.port = port;
        this.weight = weight;
        this.id = host + ":" + port;
    }

    /**
     * Returns the backend hostname.
     *
     * @return the hostname
     * @since 0.1.0
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the backend port.
     *
     * @return the port
     * @since 0.1.0
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the load balancing weight.
     *
     * @return the weight
     * @since 0.1.0
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Returns whether the backend is currently healthy.
     *
     * @return true if healthy
     * @since 0.1.0
     */
    public boolean isHealthy() {
        return healthy.get();
    }

    /**
     * Sets the health status.
     *
     * @param healthy true if healthy
     * @since 0.1.0
     */
    public void setHealthy(boolean healthy) {
        this.healthy.set(healthy);
    }

    /**
     * Returns the current number of active connections.
     *
     * @return active connection count
     * @since 0.1.0
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * Increments the active connection count and records a request.
     *
     * @since 0.1.0
     */
    public void acquireConnection() {
        activeConnections.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    /**
     * Decrements the active connection count.
     *
     * @since 0.1.0
     */
    public void releaseConnection() {
        activeConnections.decrementAndGet();
    }

    /**
     * Records a failed request.
     *
     * @since 0.1.0
     */
    public void recordFailure() {
        failedRequests.incrementAndGet();
    }

    /**
     * Returns the total number of requests sent to this backend.
     *
     * @return total request count
     * @since 0.1.0
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * Returns the total number of failed requests.
     *
     * @return failed request count
     * @since 0.1.0
     */
    public long getFailedRequests() {
        return failedRequests.get();
    }

    /**
     * Returns the unique identifier for this backend (host:port).
     *
     * @return the backend ID
     * @since 0.1.0
     */
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "BackendServer{" + id + ", weight=" + weight
                + ", healthy=" + healthy.get()
                + ", active=" + activeConnections.get() + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BackendServer other)) return false;
        return host.equals(other.host) && port == other.port;
    }

    @Override
    public int hashCode() {
        return 31 * host.hashCode() + port;
    }
}
