package ssg.legoflow.service.cluster.coordination.raft;

import ssg.legoflow.service.cluster.coordination.EtcdClient;
import ssg.legoflow.service.cluster.coordination.EtcdElection;
import ssg.legoflow.service.cluster.coordination.EtcdKVStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Raft terminology wrapper for etcd-backed leader election.
 *
 * <p>Provides Raft-consensus terminology over the etcd v3 election API:
 * <ul>
 *   <li>{@link #campaign()} → EtcdElection.campaign()</li>
 *   <li>{@link #resign()} → EtcdElection.resign()</li>
 *   <li>{@link #term()} → etcd lease term concept</li>
 * </ul>
 *
 * <p>The Raft protocol requires:
 * <ul>
 *   <li>A monotonically increasing term number</li>
 *   <li>At most one leader per term</li>
 *   <li>Election timeout to trigger campaigns</li>
 *   <li>Vote counting (simplified in etcd via CAS)</li>
 * </ul>
 *
 * <p>etcd's CAS-on-leased-key provides equivalent guarantees:
 * <ul>
 *   <li>Term = lease ID (monotonically increasing)</li>
 *   <li>Leader = node that wins CAS</li>
 *   <li>Election timeout = lease TTL</li>
 *   <li>Vote counting = implicit (CAS is the vote)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class RaftLeaderElection implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RaftLeaderElection.class);

    private final EtcdClient client;
    private final EtcdKVStore store;
    private final String electionGroup;
    private final String nodeId;
    private final int electionTtlSeconds;
    private final List<Consumer<RaftLeaderElection>> leaderChangedListeners =
            new CopyOnWriteArrayList<>();
    private EtcdElection election;
    private volatile long currentTerm = 0;
    private volatile boolean amLeader = false;

    /**
     * Creates a Raft-style leader election participant.
     *
     * @param client           the etcd client
     * @param store            the etcd KV store
     * @param electionGroup    the election group name
     * @param nodeId           the ID of this node
     * @param electionTtlSeconds lease TTL in seconds for the leader
     * @since 0.2.0
     */
    public RaftLeaderElection(EtcdClient client, EtcdKVStore store, String electionGroup,
                               String nodeId, int electionTtlSeconds) {
        this.client = Objects.requireNonNull(client);
        this.store = Objects.requireNonNull(store);
        this.electionGroup = Objects.requireNonNull(electionGroup);
        this.nodeId = Objects.requireNonNull(nodeId);
        this.electionTtlSeconds = electionTtlSeconds;
    }

    /**
     * Campaigns for leadership in the current term.
     *
     * <p>Increments the term and attempts to become leader.
     *
     * @return a future completed with true if this node won leadership
     * @since 0.2.0
     */
    public CompletableFuture<Boolean> campaign() {
        if (election != null) {
            election.close();
        }

        currentTerm++;
        long term = currentTerm;

        LOG.info("Node {} campaigning in term {} for group {}", nodeId, term, electionGroup);

        election = new EtcdElection(client, store, electionGroup, nodeId);
        election.onLeaderChanged(leader -> {
            amLeader = leader.nodeId().equals(nodeId);
            if (amLeader) {
                LOG.info("Node {} elected leader in term {}", nodeId, term);
            }
            for (Consumer<RaftLeaderElection> listener : leaderChangedListeners) {
                listener.accept(this);
            }
        });

        return election.campaign(electionTtlSeconds).thenApply(success -> {
            amLeader = success;
            return success;
        });
    }

    /**
     * Resigns from leadership, triggering a new election.
     *
     * @return a future completed when resignation is done
     * @since 0.2.0
     */
    public CompletableFuture<Void> resign() {
        if (election == null) return CompletableFuture.completedFuture(null);

        LOG.info("Node {} resigning from term {}", nodeId, currentTerm);
        amLeader = false;

        return election.resign().whenComplete((v, err) -> {
            election.close();
            election = null;
        });
    }

    /**
     * Observes the current leader.
     *
     * @return a future completed with the leader record
     * @since 0.2.0
     */
    public CompletableFuture<EtcdElection.Leader> observeLeader() {
        if (election == null) {
            election = new EtcdElection(client, store, electionGroup, nodeId);
        }
        return election.observe();
    }

    /**
     * Returns the current Raft term.
     *
     * @since 0.2.0
     */
    public long term() {
        return currentTerm;
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
     * Returns the node ID.
     *
     * @since 0.2.0
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * Returns the election group name.
     *
     * @since 0.2.0
     */
    public String electionGroup() {
        return electionGroup;
    }

    /**
     * Registers a callback for leader changes.
     *
     * @param listener the callback
     * @since 0.2.0
     */
    public void onLeaderChanged(Consumer<RaftLeaderElection> listener) {
        leaderChangedListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void close() {
        if (amLeader) {
            resign().join();
        }
        if (election != null) {
            election.close();
            election = null;
        }
    }

    @Override
    public String toString() {
        return "RaftLeaderElection{group='" + electionGroup + "', nodeId='" + nodeId
                + "', term=" + currentTerm + ", leader=" + amLeader + '}';
    }
}
