package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterEventTest {

    private static final Instant NOW = Instant.now();

    @Test
    void nodeJoined() {
        var node = ClusterNode.builder().id("new-node").build();
        var event = new ClusterEvent.NodeJoined(node, NOW);

        assertThat(event.sourceNode()).isEqualTo(node);
        assertThat(event.node()).isEqualTo(node);
        assertThat(event.timestamp()).isEqualTo(NOW);
    }

    @Test
    void nodeLeft() {
        var node = ClusterNode.builder().id("left-node").build();
        var event = new ClusterEvent.NodeLeft(node, NOW);

        assertThat(event.sourceNode()).isEqualTo(node);
        assertThat(event.node()).isEqualTo(node);
    }

    @Test
    void nodeFailed() {
        var node = ClusterNode.builder().id("failed-node").build();
        var event = new ClusterEvent.NodeFailed(node, NOW, "timeout");

        assertThat(event.sourceNode()).isEqualTo(node);
        assertThat(event.reason()).isEqualTo("timeout");
    }

    @Test
    void nodeRecovered() {
        var node = ClusterNode.builder().id("recovered-node").build();
        var event = new ClusterEvent.NodeRecovered(node, NOW);

        assertThat(event.sourceNode()).isEqualTo(node);
    }

    @Test
    void leaderChanged() {
        var oldLeader = ClusterNode.builder().id("old").build();
        var newLeader = ClusterNode.builder().id("new").build();
        var event = new ClusterEvent.LeaderChanged(oldLeader, newLeader, NOW);

        assertThat(event.sourceNode()).isEqualTo(newLeader);
        assertThat(event.previousLeader()).isEqualTo(oldLeader);
        assertThat(event.newLeader()).isEqualTo(newLeader);
    }

    @Test
    void nullNodeRejectedInNodeJoined() {
        assertThatThrownBy(() -> new ClusterEvent.NodeJoined(null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTimestampRejectedInNodeJoined() {
        var node = ClusterNode.builder().id("x").build();
        assertThatThrownBy(() -> new ClusterEvent.NodeJoined(node, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullPreviousLeaderRejected() {
        var newLeader = ClusterNode.builder().id("new").build();
        assertThatThrownBy(() -> new ClusterEvent.LeaderChanged(null, newLeader, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void patternMatchingOnSealedInterface() {
        var node = ClusterNode.builder().id("test").build();
        ClusterEvent event = new ClusterEvent.NodeJoined(node, NOW);

        String result = switch (event) {
            case ClusterEvent.NodeJoined j -> "joined:" + j.node().id();
            case ClusterEvent.NodeLeft l -> "left:" + l.node().id();
            case ClusterEvent.NodeFailed f -> "failed:" + f.node().id();
            case ClusterEvent.NodeRecovered r -> "recovered:" + r.node().id();
            case ClusterEvent.LeaderChanged c -> "leader:" + c.newLeader().id();
        };

        assertThat(result).isEqualTo("joined:test");
    }
}
