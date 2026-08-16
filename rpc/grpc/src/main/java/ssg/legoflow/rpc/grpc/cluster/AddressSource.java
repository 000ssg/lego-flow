package ssg.legoflow.rpc.grpc.cluster;

import ssg.legoflow.network.cluster.core.ClusterNode;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Consumer;

/**
 * Source of backend addresses for gRPC cluster resolution.
 *
 * <p>Implementations provide the current list of backend nodes
 * for a given service name and notify listeners when the list changes.
 *
 * @since 0.2.0
 */
public interface AddressSource {

    /**
     * Returns the current list of backend addresses for the given service.
     *
     * @param serviceName the service to resolve
     * @return the current list of backend nodes
     */
    List<ClusterNode> resolve(String serviceName);

    /**
     * Registers a listener notified when the address list changes.
     *
     * @param serviceName the service to watch
     * @param listener    the callback
     */
    void onAddressesChanged(String serviceName, Consumer<List<ClusterNode>> listener);

    /**
     * Creates a static address source from a fixed list.
     *
     * @param nodes the fixed list of nodes
     * @return a static address source
     */
    static AddressSource staticSource(List<ClusterNode> nodes) {
        return new StaticAddressSource(nodes);
    }

    /**
     * Returns an address for a single backend.
     *
     * @param node the node
     * @return the socket address
     */
    static InetSocketAddress addressOf(ClusterNode node) {
        return new InetSocketAddress(node.host(), node.port());
    }

    /**
     * A static (fixed) address source.
     */
    final class StaticAddressSource implements AddressSource {
        private final List<ClusterNode> nodes;

        StaticAddressSource(List<ClusterNode> nodes) {
            this.nodes = List.copyOf(nodes);
        }

        @Override
        public List<ClusterNode> resolve(String serviceName) {
            return nodes;
        }

        @Override
        public void onAddressesChanged(String serviceName, Consumer<List<ClusterNode>> listener) {
            // Static source never changes; deliver initial list immediately
            listener.accept(nodes);
        }
    }
}
