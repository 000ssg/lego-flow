package ssg.legoflow.rpc.grpc.cluster;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancer.
 *
 * <p>Cycles through healthy backends in order, distributing requests evenly.
 *
 * @since 0.2.0
 */
public final class RoundRobinBalancer implements GrpcLoadBalancer {

    private final AtomicInteger position = new AtomicInteger(0);
    private List<ClusterSubchannel> channels = List.of();

    @Override
    public Optional<ClusterSubchannel> select(List<ClusterSubchannel> subchannels, String key) {
        List<ClusterSubchannel> healthy = channels.stream()
                .filter(ClusterSubchannel::isHealthy)
                .toList();

        if (healthy.isEmpty()) {
            return Optional.empty();
        }

        int idx = position.getAndIncrement() % healthy.size();
        if (idx < 0) idx += healthy.size();
        return Optional.of(healthy.get(idx));
    }

    @Override
    public void updateChannels(List<ClusterSubchannel> subchannels) {
        this.channels = List.copyOf(subchannels);
    }

    @Override
    public String name() {
        return "round_robin";
    }
}
