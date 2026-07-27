package ssg.legoflow.http.proxy.reverse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancing implementation.
 *
 * <p>Distributes requests evenly across healthy backends in a circular fashion.
 * Backend weights are respected: a backend with weight N is selected N times
 * per cycle.</p>
 *
 * @since 1.0.0
 */
public class RoundRobinBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Creates a new round-robin balancer.
     *
     * @since 1.0.0
     */
    public RoundRobinBalancer() {
    }

    @Override
    public BackendServer select(List<BackendServer> backends) {
        List<BackendServer> healthy = backends.stream()
                .filter(BackendServer::isHealthy)
                .toList();

        if (healthy.isEmpty()) {
            return null;
        }

        // Build weighted list
        List<BackendServer> weighted = new java.util.ArrayList<>();
        for (BackendServer backend : healthy) {
            for (int i = 0; i < backend.getWeight(); i++) {
                weighted.add(backend);
            }
        }

        if (weighted.isEmpty()) {
            return null;
        }

        int index = Math.abs(counter.getAndIncrement() % weighted.size());
        return weighted.get(index);
    }

    @Override
    public String getName() {
        return "round-robin";
    }

    /**
     * Resets the internal counter.
     *
     * @since 1.0.0
     */
    public void reset() {
        counter.set(0);
    }

    /**
     * Returns the current counter value (for testing).
     *
     * @return the counter
     * @since 1.0.0
     */
    public int getCounter() {
        return counter.get();
    }
}
