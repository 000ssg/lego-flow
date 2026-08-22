package ssg.legoflow.database.redis.client;

import ssg.legoflow.database.redis.cluster.ClusterInfo;
import ssg.legoflow.database.redis.cluster.HashSlot;
import ssg.legoflow.database.redis.protocol.RespType;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Cluster-aware Redis client with MOVED/ASK redirect handling.
 *
 * <p>Routes commands to the correct node based on hash slot calculation.
 * Automatically follows MOVED and ASK redirects, caching slot-to-node
 * mappings for efficiency.
 *
 * @since 0.1.0
 */
public final class ClusterClient implements AutoCloseable {

    private final Map<String, RedisClient> connections = new ConcurrentHashMap<>();
    private final Map<Integer, String> slotMap = new ConcurrentHashMap<>();
    private final String seedHost;
    private final int seedPort;
    private ClusterInfo clusterInfo;

    private static final int MAX_REDIRECTS = 5;

    /**
     * Creates a cluster client with the given seed node.
     *
     * @param host seed node host
     * @param port seed node port
     */
    public ClusterClient(String host, int port) {
        this.seedHost = Objects.requireNonNull(host);
        this.seedPort = port;
    }

    /**
     * Connects to the seed node and discovers the cluster topology.
     *
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        RedisClient seed = getOrCreateConnection(seedHost, seedPort);
        seed.execute("CLUSTER", "INFO"); // Verify cluster is available
        clusterInfo = ClusterInfo.singleNode(seedHost, seedPort);

        // Map all slots to the seed node
        String nodeKey = seedHost + ":" + seedPort;
        for (int slot = 0; slot < HashSlot.TOTAL_SLOTS; slot++) {
            slotMap.put(slot, nodeKey);
        }
    }

    /**
     * Executes a command, routing by key and handling redirects.
     *
     * @param args command and arguments (first key-bearing arg determines routing)
     * @return the response
     * @throws IOException if I/O fails
     */
    public RespType execute(String... args) throws IOException {
        if (args.length < 2) {
            // Commands without keys go to any node
            return getOrCreateConnection(seedHost, seedPort).execute(args);
        }

        // Calculate slot from the first key argument
        String key = args[1];
        int slot = HashSlot.slot(key);
        String nodeKey = slotMap.getOrDefault(slot, seedHost + ":" + seedPort);

        return executeWithRedirects(nodeKey, args, 0);
    }

    private RespType executeWithRedirects(String nodeKey, String[] args, int redirectCount)
            throws IOException {
        if (redirectCount >= MAX_REDIRECTS) {
            throw new IOException("Too many redirects");
        }

        String[] hostPort = nodeKey.split(":");
        RedisClient client = getOrCreateConnection(hostPort[0], Integer.parseInt(hostPort[1]));

        RespType response = client.execute(args);

        if (response instanceof RespType.Error err) {
            ClusterInfo.Redirect redirect = ClusterInfo.Redirect.parse(err.fullMessage());
            if (redirect != null) {
                String targetKey = redirect.host() + ":" + redirect.port();

                if ("MOVED".equals(redirect.type())) {
                    // Update slot mapping permanently
                    slotMap.put(redirect.slot(), targetKey);
                }

                if ("ASK".equals(redirect.type())) {
                    // Send ASKING before retry
                    RedisClient target = getOrCreateConnection(redirect.host(), redirect.port());
                    target.execute("ASKING");
                }

                return executeWithRedirects(targetKey, args, redirectCount + 1);
            }
        }

        return response;
    }

    private RedisClient getOrCreateConnection(String host, int port) throws IOException {
        String key = host + ":" + port;
        RedisClient client = connections.get(key);
        if (client == null || !client.isConnected()) {
            client = new RedisClient(host, port);
            client.connect();
            connections.put(key, client);
        }
        return client;
    }

    /**
     * Returns the hash slot for a key.
     *
     * @param key the key
     * @return hash slot (0-16383)
     */
    public int slotForKey(String key) {
        return HashSlot.slot(key);
    }

    /**
     * Returns the cluster info.
     *
     * @return cluster topology info
     */
    public ClusterInfo clusterInfo() {
        return clusterInfo;
    }

    @Override
    public void close() {
        for (RedisClient client : connections.values()) {
            client.close();
        }
        connections.clear();
    }
}
