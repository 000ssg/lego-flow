package ssg.legoflow.rpc.grpc.cluster;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Least-request load balancer.
 *
 * <p>Selects the healthy backend with the fewest in-flight requests.
 *
 * @since 0.2.0
 */
public final class LeastRequestBalancer implements GrpcLoadBalancer {

    private List<ClusterSubchannel> channels = List.of();

    @Override
    public Optional<ClusterSubchannel> select(List<ClusterSubchannel> subchannels, String key) {
        return channels.stream()
                .filter(ClusterSubchannel::isHealthy)
                .min(Comparator.comparingInt(ClusterSubchannel::inFlight));
    }

    @Override
    public void updateChannels(List<ClusterSubchannel> subchannels) {
        this.channels = List.copyOf(subchannels);
    }

    @Override
    public String name() {
        return "least_request";
    }
}
