package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterStatusTest {

    @Test
    void ofWithMembersOnly() {
        var node1 = ClusterNode.builder().id("a").build();
        var node2 = ClusterNode.builder().id("b").build();

        var status = ClusterStatus.of(List.of(node1, node2));

        assertThat(status.memberCount()).isEqualTo(2);
        assertThat(status.members()).containsExactlyInAnyOrder(node1, node2);
        assertThat(status.leader()).isNull();
        assertThat(status.hasLeader()).isFalse();
    }

    @Test
    void ofWithLeader() {
        var node1 = ClusterNode.builder().id("a").build();
        var node2 = ClusterNode.builder().id("b").build();

        var status = ClusterStatus.of(List.of(node1, node2), node1);

        assertThat(status.leader()).isEqualTo(node1);
        assertThat(status.hasLeader()).isTrue();
    }

    @Test
    void filtersNonActiveMembers() {
        var active = ClusterNode.builder().id("a").build();
        var failed = ClusterNode.builder()
                .id("b")
                .status(ClusterNodeStatus.FAILED)
                .build();
        var suspect = ClusterNode.builder()
                .id("c")
                .status(ClusterNodeStatus.SUSPECT)
                .build();

        var status = ClusterStatus.of(List.of(active, failed, suspect));

        assertThat(status.memberCount()).isEqualTo(1);
        assertThat(status.members()).containsExactly(active);
    }

    @Test
    void suspectAndFailedCounts() {
        var allNodes = List.of(
                ClusterNode.builder().id("a").build(),
                ClusterNode.builder().id("b").status(ClusterNodeStatus.SUSPECT).build(),
                ClusterNode.builder().id("c").status(ClusterNodeStatus.FAILED).build(),
                ClusterNode.builder().id("d").status(ClusterNodeStatus.SUSPECT).build()
        );

        var status = ClusterStatus.of(allNodes);

        assertThat(status.suspectCount(allNodes)).isEqualTo(2);
        assertThat(status.failedCount(allNodes)).isEqualTo(1);
    }

    @Test
    void isHealthyWhenNoSuspectOrFailed() {
        var allNodes = List.of(
                ClusterNode.builder().id("a").build(),
                ClusterNode.builder().id("b").build()
        );

        var status = ClusterStatus.of(allNodes);
        assertThat(status.isHealthy(allNodes)).isTrue();
    }

    @Test
    void isNotHealthyWhenSuspectExists() {
        var allNodes = List.of(
                ClusterNode.builder().id("a").build(),
                ClusterNode.builder().id("b").status(ClusterNodeStatus.SUSPECT).build()
        );

        var status = ClusterStatus.of(allNodes);
        assertThat(status.isHealthy(allNodes)).isFalse();
    }

    @Test
    void isNotHealthyWhenFailedExists() {
        var allNodes = List.of(
                ClusterNode.builder().id("a").build(),
                ClusterNode.builder().id("b").status(ClusterNodeStatus.FAILED).build()
        );

        var status = ClusterStatus.of(allNodes);
        assertThat(status.isHealthy(allNodes)).isFalse();
    }

    @Test
    void equalityAndHashCode() {
        var members = List.of(ClusterNode.builder().id("a").build());
        var leader = ClusterNode.builder().id("a").build();

        var s1 = ClusterStatus.of(members, leader);
        var s2 = ClusterStatus.of(members, leader);

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    void emptyMemberList() {
        var status = ClusterStatus.of(List.of());

        assertThat(status.memberCount()).isZero();
        assertThat(status.members()).isEmpty();
        assertThat(status.leader()).isNull();
    }
}
