package ssg.legoflow.rpc.grpc.cluster;

import ssg.legoflow.network.cluster.core.ClusterNode;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Client-side load balancing policy for gRPC backends.
 *
 * <p>Receives a list of subchannels (one per backend address) and selects
 * the target for each outgoing RPC. Subclasses implement different
 * balancing strategies.
 *
 * @since 0.2.0
 */
public sealed interface GrpcLoadBalancer permits
        RoundRobinBalancer,
        LeastRequestBalancer,
        ConsistentHashBalancer {

    /**
     * Selects the target subchannel for the next request.
     *
     * @param subchannels the current list of subchannels
     * @param key         optional request key for hashing-based policies
     * @return the selected subchannel, or empty if no healthy backends
     */
    Optional<ClusterSubchannel> select(List<ClusterSubchannel> subchannels, String key);

    /**
     * Updates the list of available subchannels.
     *
     * <p>Called when the resolver discovers new or removed backends.
     *
     * @param subchannels the new list of subchannels
     */
    void updateChannels(List<ClusterSubchannel> subchannels);

    /**
     * Notifies the balancer that a request completed on the given subchannel.
     *
     * @param subchannel the subchannel used
     */
    default void onCompleted(ClusterSubchannel subchannel) {
    }

    /**
     * Returns the name of this load balancing policy.
     *
     * @return the policy name
     */
    String name();

    /**
     * Creates a round-robin load balancer.
     *
     * @return a new RoundRobinBalancer
     */
    static GrpcLoadBalancer roundRobin() {
        return new RoundRobinBalancer();
    }

    /**
     * Creates a least-request load balancer.
     *
     * @return a new LeastRequestBalancer
     */
    static GrpcLoadBalancer leastRequest() {
        return new LeastRequestBalancer();
    }

    /**
     * Creates a consistent-hash load balancer.
     *
     * @return a new ConsistentHashBalancer
     */
    static GrpcLoadBalancer consistentHash() {
        return new ConsistentHashBalancer();
    }
}
