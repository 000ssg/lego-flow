package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple KafkaBroker instances in-process for multi-broker simulation.
 *
 * <p>Coordinates partition leadership, ISR tracking, reassignments,
 * and controlled shutdown across a cluster of brokers.
 *
 * @since 0.1.0
 */
public final class BrokerCluster implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BrokerCluster.class);

    private final List<KafkaBroker> brokers;
    private final Map<TopicPartition, Integer> leaderBroker = new ConcurrentHashMap<>();
    private final Map<TopicPartition, List<Integer>> isrSets = new ConcurrentHashMap<>();
    private final Map<TopicPartition, List<Integer>> targetReassignments = new ConcurrentHashMap<>();

    /**
     * Creates and starts a cluster of N brokers.
     *
     * @param numBrokers the number of brokers to create
     * @param host       the bind host for all brokers
     * @throws IOException if any broker fails to start
     */
    public BrokerCluster(int numBrokers, String host) throws IOException {
        if (numBrokers < 1) throw new IllegalArgumentException("numBrokers must be >= 1");
        brokers = new ArrayList<>(numBrokers);
        try {
            for (int i = 0; i < numBrokers; i++) {
                KafkaBroker broker = new KafkaBroker(host, 0, i, 1);
                broker.start();
                brokers.add(broker);
            }
            LOG.info("Broker cluster started with {} brokers", numBrokers);
        } catch (IOException e) {
            // Clean up any brokers that started successfully
            for (KafkaBroker broker : brokers) {
                broker.close();
            }
            throw e;
        }
    }

    /**
     * Returns the broker with the given ID.
     *
     * @param brokerId the broker ID
     * @return the broker
     * @throws IllegalArgumentException if broker ID is invalid
     */
    public KafkaBroker getBroker(int brokerId) {
        if (brokerId < 0 || brokerId >= brokers.size()) {
            throw new IllegalArgumentException("Invalid broker ID: " + brokerId);
        }
        return brokers.get(brokerId);
    }

    /**
     * Returns the number of brokers in the cluster.
     *
     * @return the broker count
     */
    public int size() {
        return brokers.size();
    }

    /**
     * Returns the leader broker ID for a partition.
     *
     * @param tp the topic-partition
     * @return the leader broker ID, or -1 if no leader assigned
     */
    public int leaderFor(TopicPartition tp) {
        return leaderBroker.getOrDefault(tp, -1);
    }

    /**
     * Returns the ISR for a partition.
     *
     * @param tp the topic-partition
     * @return the ISR list, or empty list if not tracked
     */
    public List<Integer> isrFor(TopicPartition tp) {
        return isrSets.getOrDefault(tp, List.of());
    }

    /**
     * Elects a new leader for a partition and updates all brokers' replica managers.
     *
     * @param tp          the topic-partition
     * @param newLeaderId the new leader broker ID
     */
    public void electLeader(TopicPartition tp, int newLeaderId) {
        if (newLeaderId < 0 || newLeaderId >= brokers.size()) {
            throw new IllegalArgumentException("Invalid leader broker ID: " + newLeaderId);
        }
        leaderBroker.put(tp, newLeaderId);

        // Update ISR to include the new leader
        List<Integer> currentIsr = isrSets.getOrDefault(tp, new ArrayList<>());
        if (!currentIsr.contains(newLeaderId)) {
            currentIsr = new ArrayList<>(currentIsr);
            currentIsr.add(newLeaderId);
        }
        isrSets.put(tp, currentIsr);

        // Compute the leader epoch as the number of elections
        int epoch = 1;
        for (KafkaBroker broker : brokers) {
            ReplicaManager.ReplicaState existing = broker.replicaManager().getReplicaState(tp);
            if (existing != null && existing.leaderEpoch() >= epoch) {
                epoch = existing.leaderEpoch() + 1;
            }
        }

        // Update all brokers' replica managers
        for (KafkaBroker broker : brokers) {
            broker.replicaManager().updateLeaderAndIsr(tp, newLeaderId, epoch, currentIsr);
        }

        LOG.info("Elected broker {} as leader for {} (epoch={})", newLeaderId, tp, epoch);
    }

    /**
     * Initiates a partition reassignment to a new replica set.
     *
     * @param tp          the topic-partition
     * @param newReplicas the target replica set
     */
    public void reassignPartition(TopicPartition tp, List<Integer> newReplicas) {
        targetReassignments.put(tp, List.copyOf(newReplicas));
        LOG.info("Partition {} reassignment started: target replicas {}", tp, newReplicas);
    }

    /**
     * Returns all ongoing partition reassignments.
     *
     * @return a map of topic-partition to target replica sets
     */
    public Map<TopicPartition, List<Integer>> listReassignments() {
        return Collections.unmodifiableMap(targetReassignments);
    }

    /**
     * Performs a controlled shutdown of a broker, migrating leadership away from it.
     *
     * @param brokerId the broker ID to shut down
     */
    public void controlledShutdown(int brokerId) {
        if (brokerId < 0 || brokerId >= brokers.size()) {
            throw new IllegalArgumentException("Invalid broker ID: " + brokerId);
        }

        // Find all partitions led by this broker and migrate leadership
        List<TopicPartition> toMigrate = new ArrayList<>();
        for (var entry : leaderBroker.entrySet()) {
            if (entry.getValue() == brokerId) {
                toMigrate.add(entry.getKey());
            }
        }

        for (TopicPartition tp : toMigrate) {
            List<Integer> isr = isrSets.getOrDefault(tp, List.of());
            // Find another broker in ISR to be the new leader
            int newLeader = -1;
            for (int replica : isr) {
                if (replica != brokerId) {
                    newLeader = replica;
                    break;
                }
            }
            if (newLeader >= 0) {
                electLeader(tp, newLeader);
            } else {
                // No other replica in ISR — try any other broker
                for (int i = 0; i < brokers.size(); i++) {
                    if (i != brokerId) {
                        electLeader(tp, i);
                        break;
                    }
                }
            }
        }

        // Remove broker from all ISR sets
        for (var entry : isrSets.entrySet()) {
            List<Integer> isr = new ArrayList<>(entry.getValue());
            isr.remove(Integer.valueOf(brokerId));
            isrSets.put(entry.getKey(), isr);
        }

        LOG.info("Controlled shutdown of broker {} complete, migrated {} partitions", brokerId, toMigrate.size());
    }

    @Override
    public void close() {
        for (KafkaBroker broker : brokers) {
            broker.close();
        }
        LOG.info("Broker cluster stopped");
    }
}
