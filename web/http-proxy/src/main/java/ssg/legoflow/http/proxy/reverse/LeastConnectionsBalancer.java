package ssg.legoflow.http.proxy.reverse;

import java.util.List;

/**
 * Least-connections load balancing implementation.
 *
 * <p>Selects the healthy backend with the fewest active connections.
 * When multiple backends have the same connection count, the first one
 * encountered is selected.</p>
 *
 * @since 1.0.0
 */
public class LeastConnectionsBalancer implements LoadBalancer {

    /**
     * Creates a new least-connections balancer.
     *
     * @since 1.0.0
     */
    public LeastConnectionsBalancer() {
    }

    @Override
    public BackendServer select(List<BackendServer> backends) {
        BackendServer selected = null;
        int minConnections = Integer.MAX_VALUE;

        for (BackendServer backend : backends) {
            if (!backend.isHealthy()) {
                continue;
            }
            int connections = backend.getActiveConnections();
            if (connections < minConnections) {
                minConnections = connections;
                selected = backend;
            }
        }

        return selected;
    }

    @Override
    public String getName() {
        return "least-connections";
    }
}
