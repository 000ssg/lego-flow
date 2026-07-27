package ssg.legoflow.http.connection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionManager {

    private final ConnectionConfig config;
    private final Map<String, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    public ConnectionManager() {
        this(new ConnectionConfig());
    }

    public ConnectionManager(ConnectionConfig config) {
        this.config = config;
    }

    public boolean canAcceptConnection(String host) {
        var count = activeConnections.computeIfAbsent(host, _ -> new AtomicInteger(0));
        return count.get() < config.getMaxConnections();
    }

    public boolean acquireConnection(String host) {
        var count = activeConnections.computeIfAbsent(host, _ -> new AtomicInteger(0));
        if (count.get() >= config.getMaxConnections()) return false;
        count.incrementAndGet();
        return true;
    }

    public void releaseConnection(String host) {
        var count = activeConnections.get(host);
        if (count != null) count.decrementAndGet();
    }

    public int getActiveCount(String host) {
        var count = activeConnections.get(host);
        return count != null ? count.get() : 0;
    }

    public boolean isKeepAlive(String connectionHeader) {
        if (connectionHeader == null) return true;
        return !"close".equalsIgnoreCase(connectionHeader.trim());
    }

    public ConnectionConfig getConfig() { return config; }
}
