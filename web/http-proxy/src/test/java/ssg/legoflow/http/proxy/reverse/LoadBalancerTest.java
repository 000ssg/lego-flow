package ssg.legoflow.http.proxy.reverse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoadBalancerTest {

    @Test
    void testRoundRobinDistribution() {
        var balancer = new RoundRobinBalancer();
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);
        var b3 = new BackendServer("b3", 8083);
        var backends = List.of(b1, b2, b3);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            var selected = balancer.select(backends);
            counts.merge(selected.getId(), 1, Integer::sum);
        }

        assertThat(counts.get("b1:8081")).isEqualTo(3);
        assertThat(counts.get("b2:8082")).isEqualTo(3);
        assertThat(counts.get("b3:8083")).isEqualTo(3);
    }

    @Test
    void testRoundRobinWithWeights() {
        var balancer = new RoundRobinBalancer();
        var b1 = new BackendServer("b1", 8081, 2);
        var b2 = new BackendServer("b2", 8082, 1);
        var backends = List.of(b1, b2);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            var selected = balancer.select(backends);
            counts.merge(selected.getId(), 1, Integer::sum);
        }

        // With weight 2:1, b1 should get about 2x b2
        assertThat(counts.get("b1:8081")).isGreaterThan(counts.get("b2:8082"));
    }

    @Test
    void testRoundRobinSkipsUnhealthy() {
        var balancer = new RoundRobinBalancer();
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);
        b2.setHealthy(false);
        var backends = List.of(b1, b2);

        for (int i = 0; i < 5; i++) {
            var selected = balancer.select(backends);
            assertThat(selected.getId()).isEqualTo("b1:8081");
        }
    }

    @Test
    void testRoundRobinAllUnhealthy() {
        var balancer = new RoundRobinBalancer();
        var b1 = new BackendServer("b1", 8081);
        b1.setHealthy(false);
        var backends = List.of(b1);

        assertThat(balancer.select(backends)).isNull();
    }

    @Test
    void testRoundRobinEmptyList() {
        var balancer = new RoundRobinBalancer();
        assertThat(balancer.select(List.of())).isNull();
    }

    @Test
    void testRoundRobinName() {
        var balancer = new RoundRobinBalancer();
        assertThat(balancer.getName()).isEqualTo("round-robin");
    }

    @Test
    void testRoundRobinReset() {
        var balancer = new RoundRobinBalancer();
        var b1 = new BackendServer("b1", 8081);
        balancer.select(List.of(b1));
        assertThat(balancer.getCounter()).isGreaterThan(0);
        balancer.reset();
        assertThat(balancer.getCounter()).isEqualTo(0);
    }

    @Test
    void testLeastConnectionsSelection() {
        var balancer = new LeastConnectionsBalancer();
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);

        b1.acquireConnection();
        b1.acquireConnection();
        b2.acquireConnection();

        var selected = balancer.select(List.of(b1, b2));
        assertThat(selected.getId()).isEqualTo("b2:8082");
    }

    @Test
    void testLeastConnectionsAllZero() {
        var balancer = new LeastConnectionsBalancer();
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);

        var selected = balancer.select(List.of(b1, b2));
        // Should select first with 0 connections
        assertThat(selected).isNotNull();
        assertThat(selected.getActiveConnections()).isEqualTo(0);
    }

    @Test
    void testLeastConnectionsSkipsUnhealthy() {
        var balancer = new LeastConnectionsBalancer();
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);
        b1.setHealthy(false);

        var selected = balancer.select(List.of(b1, b2));
        assertThat(selected.getId()).isEqualTo("b2:8082");
    }

    @Test
    void testLeastConnectionsAllUnhealthy() {
        var balancer = new LeastConnectionsBalancer();
        var b1 = new BackendServer("b1", 8081);
        b1.setHealthy(false);

        assertThat(balancer.select(List.of(b1))).isNull();
    }

    @Test
    void testLeastConnectionsName() {
        var balancer = new LeastConnectionsBalancer();
        assertThat(balancer.getName()).isEqualTo("least-connections");
    }

    @Test
    void testLeastConnectionsAfterRelease() {
        var balancer = new LeastConnectionsBalancer();
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);

        b1.acquireConnection();
        b1.acquireConnection();
        b2.acquireConnection();
        b1.releaseConnection();
        b1.releaseConnection();

        var selected = balancer.select(List.of(b1, b2));
        assertThat(selected.getId()).isEqualTo("b1:8081");
    }
}
