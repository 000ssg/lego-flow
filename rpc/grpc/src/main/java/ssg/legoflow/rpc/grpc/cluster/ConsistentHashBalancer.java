package ssg.legoflow.rpc.grpc.cluster;

import ssg.legoflow.network.cluster.core.ClusterNode;
import ssg.legoflow.network.cluster.core.hashing.ConsistentHashRing;
import ssg.legoflow.network.cluster.core.hashing.MurmurHash3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consistent-hash load balancer.
 *
 * <p>Hashes the request key to a backend on the consistent hash ring,
 * ensuring that requests with the same key always route to the same backend
 * (unless that backend becomes unhealthy).
 *
 * <p>Uses the {@link ConsistentHashRing} from cluster-core to map keys to
 * {@code ClusterNode} identifiers, then resolves to the corresponding
 * {@code ClusterSubchannel} via a lookup map.
 *
 * @since 0.2.0
 */
public final class ConsistentHashBalancer implements GrpcLoadBalancer {

    private static final int DEFAULT_REPLICAS = 160;

    private final ConsistentHashRing ring = new ConsistentHashRing(DEFAULT_REPLICAS, MurmurHash3.INSTANCE);
    private final Map<String, ClusterSubchannel> channelMap = new ConcurrentHashMap<>();
    private List<ClusterSubchannel> channels = List.of();

    @Override
    public Optional<ClusterSubchannel> select(List<ClusterSubchannel> subchannels, String key) {
        if (key == null || key.isEmpty()) {
            return channels.stream()
                    .filter(ClusterSubchannel::isHealthy)
                    .findFirst();
        }

        ClusterNode node = ring.getNode(key);
        if (node == null) {
            return Optional.empty();
        }

        ClusterSubchannel ch = channelMap.get(node.id());
        if (ch != null && ch.isHealthy()) {
            return Optional.of(ch);
        }

        // Node from ring is unhealthy; fall back to first healthy backend
        return channels.stream()
                .filter(ClusterSubchannel::isHealthy)
                .findFirst()
                .map(Optional::of)
                .orElse(Optional.empty());
    }

    @Override
    public void updateChannels(List<ClusterSubchannel> subchannels) {
        this.channels = List.copyOf(subchannels);
        ring.clear();
        channelMap.clear();

        for (ClusterSubchannel ch : subchannels) {
            if (ch.isHealthy()) {
                ring.add(ch.node());
                channelMap.put(ch.node().id(), ch);
            }
        }
    }

    @Override
    public String name() {
        return "consistent_hashing";
    }

    @Override
    public void onCompleted(ClusterSubchannel subchannel) {
        // Consistent hashing does not need request completion tracking
    }
}
