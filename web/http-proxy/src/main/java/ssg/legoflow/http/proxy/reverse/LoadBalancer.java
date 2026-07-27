package ssg.legoflow.http.proxy.reverse;

import java.util.List;

/**
 * Interface for load balancing strategies used by the reverse proxy.
 *
 * <p>Implementations select a backend server from a list of available
 * backends for each incoming request.</p>
 *
 * @since 1.0.0
 */
public interface LoadBalancer {

    /**
     * Selects the next backend server from the list of available backends.
     * Only healthy backends should be considered.
     *
     * @param backends the list of backend servers
     * @return the selected backend, or null if no healthy backend is available
     * @since 1.0.0
     */
    BackendServer select(List<BackendServer> backends);

    /**
     * Returns the name of this load balancing strategy.
     *
     * @return the strategy name
     * @since 1.0.0
     */
    String getName();
}
