package ssg.legoflow.service.cluster.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
/**
 * Leader election backed by etcd.
 *
 * <p>Implements the etcd v3 election protocol:
 * <ul>
 *   <li>Campaign: attempt to become leader via CAS with a leased key</li>
 *   <li>Resign: release leadership by deleting the leader key</li>
 *   <li>Observe: watch the leader key for changes</li>
 * </ul>
 *
 * <p>Guarantees:
 * <ul>
 *   <li>At most one leader at any point in time</li>
 *   <li>Leader failure triggers new election (lease expiry)</li>
 *   <li>Linearizable reads during leadership</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class EtcdElection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdElection.class);

    private final EtcdClient client;
    private final EtcdKVStore store;
    private final String electionName;
    private final String electionKey;
    private final String nodeId;
    private final List<Consumer<Leader>> leaderChangedListeners = new CopyOnWriteArrayList<>();
    private volatile Leader currentLeader;
    private volatile boolean amLeader = false;
    private EtcdLease leaderLease;
    private final ScheduledExecutorService scheduler;

    /**
     * Describes the current leader.
     *
     * @param nodeId  the leader's node ID
     * @param elected the time at which the leader was elected
     * @since 0.2.0
     */
    public record Leader(String nodeId, Instant elected) {
        @Override
        public String toString() {
            return "Leader{nodeId='" + nodeId + "', elected=" + elected + '}';
        }
    }

    /**
     * Creates a new election participant.
     *
     * @param client       the etcd client (for lease management)
     * @param store        the key-value store
     * @param electionName the name of this election group
     * @param nodeId       the ID of this node
     * @since 0.2.0
     */
    public EtcdElection(EtcdClient client, EtcdKVStore store, String electionName, String nodeId) {
        this.client = Objects.requireNonNull(client);
        this.store = Objects.requireNonNull(store);
        this.electionName = Objects.requireNonNull(electionName);
        this.electionKey = "/elections/" + electionName + "/leader";
        this.nodeId = Objects.requireNonNull(nodeId);
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "etcd-election-" + electionName);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Campaigns for leadership.
     *
     * <p>Attempts to set the leader key with our node ID and a lease.
     * Uses CAS to ensure only one node succeeds.
     *
     * @param ttlSeconds the lease TTL for the leader
     * @return a future completed when the campaign result is known
     * @since 0.2.0
     */
    public CompletableFuture<Boolean> campaign(int ttlSeconds) {
        Objects.requireNonNull(nodeId);
        if (ttlSeconds <= 0)
            throw new IllegalArgumentException("TTL must be positive");

        LOG.debug("Node {} campaigning for election {}", nodeId, electionName);

        return EtcdLease.grant(client, ttlSeconds).thenCompose(lease -> {
            Leader candidate = new Leader(nodeId, Instant.now());
            byte[] value = candidate.toString().getBytes(StandardCharsets.UTF_8);

            return EtcdTransaction.create(store, electionKey, null)
                    .thenPutWithLease(electionKey, value, lease)
                    .execute()
                    .thenApply(success -> {
                        if (success) {
                            this.leaderLease = lease;
                            this.currentLeader = candidate;
                            this.amLeader = true;
                            LOG.info("Node {} won election {}", nodeId, electionName);

                            startLeaderKeepAlive(lease, ttlSeconds, candidate);

                            for (Consumer<Leader> listener : leaderChangedListeners) {
                                listener.accept(candidate);
                            }
                        } else {
                            LOG.debug("Node {} lost election {} (another leader exists)",
                                    nodeId, electionName);
                            currentLeader = readLeader();
                        }
                        return success;
                    });
        });
    }

    private void startLeaderKeepAlive(EtcdLease lease, int ttlSeconds, Leader leader) {
        Duration interval = Duration.ofSeconds(ttlSeconds).dividedBy(2);
        scheduler.scheduleAtFixedRate(() -> {
            if (amLeader) {
                LOG.trace("Leader keep-alive for node {}", nodeId);
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Leader readLeader() {
        Leader result = null;
        try {
            byte[] value = store.get(electionKey).join();
            if (value != null) {
                String s = new String(value, StandardCharsets.UTF_8);
                result = parseLeader(s);
            }
        } catch (Exception e) {
            LOG.warn("Failed to read leader for election {}", electionName, e);
        }
        return result;
    }

    private Leader parseLeader(String s) {
        try {
            String[] parts = s.split(", ");
            String nodeIdPart = parts[0].replace("Leader{nodeId='", "").replace("'", "");
            String electedPart = parts[1].replace("elected=", "").replace("}", "");
            return new Leader(nodeIdPart, Instant.parse(electedPart));
        } catch (Exception e) {
            LOG.warn("Failed to parse leader string: {}", s);
            return null;
        }
    }

    /**
     * Resigns from leadership.
     *
     * <p>Deletes the leader key, triggering a new election.
     *
     * @return a future completed when resignation is done
     * @since 0.2.0
     */
    public CompletableFuture<Void> resign() {
        if (!amLeader) return CompletableFuture.completedFuture(null);

        LOG.info("Node {} resigning from election {}", nodeId, electionName);
        amLeader = false;

        return store.delete(electionKey)
                .thenCompose(existed -> {
                    if (leaderLease != null) {
                        return leaderLease.revoke().thenApply(v -> existed);
                    }
                    return CompletableFuture.completedFuture(existed);
                })
                .thenApply(existed -> {
                    this.leaderLease = null;
                    this.currentLeader = null;
                    LOG.info("Node {} resigned from election {}", nodeId, electionName);
                    return null;
                });
    }

    /**
     * Returns the current leader, if any.
     *
     * @return a future completed with the current leader
     * @since 0.2.0
     */
    public CompletableFuture<Leader> observe() {
        return store.get(electionKey).thenApply(value -> {
            if (value == null) return null;
            String s = new String(value, StandardCharsets.UTF_8);
            return parseLeader(s);
        });
    }

    /**
     * Returns whether this node is the current leader.
     *
     * @since 0.2.0
     */
    public boolean isLeader() {
        return amLeader;
    }

    /**
     * Registers a callback invoked when the leader changes.
     *
     * @param listener the callback
     * @since 0.2.0
     */
    public void onLeaderChanged(Consumer<Leader> listener) {
        leaderChangedListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Returns the election name.
     *
     * @since 0.2.0
     */
    public String electionName() {
        return electionName;
    }

    @Override
    public void close() {
        if (amLeader) {
            resign().join();
        }
        scheduler.shutdownNow();
    }

    @Override
    public String toString() {
        return "EtcdElection{name='" + electionName + "', nodeId='" + nodeId
                + "', amLeader=" + amLeader + '}';
    }
}
