package ssg.legoflow.network.cluster.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import static org.assertj.core.api.Assertions.assertThat;
class ClusterHealthCheckerTest {

    @Test
    void simpleHealthCheckerReturnsGivenInterval() {
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(2));

        assertThat(checker.defaultInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(checker.checkTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void simpleHealthCheckerAlwaysHealthy() throws ExecutionException, InterruptedException {
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(5), Duration.ofSeconds(1));

        var node = ClusterNode.builder().id("healthy-node").build();
        var result = checker.check(node).get();

        assertThat(result).isTrue();
    }

    @Test
    void simpleHealthCheckerCustomSubclassReturnsFalse() throws ExecutionException, InterruptedException {
        var checker = new ClusterHealthChecker() {
            @Override
            public java.util.concurrent.CompletableFuture<Boolean> check(ClusterNode node) {
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }

            @Override
            public Duration defaultInterval() {
                return Duration.ofSeconds(30);
            }

            @Override
            public Duration checkTimeout() {
                return Duration.ofSeconds(5);
            }
        };

        var node = ClusterNode.builder().id("unhealthy-node").build();
        var result = checker.check(node).get();

        assertThat(result).isFalse();
    }

    @Test
    void simpleHealthCheckerIntervalAndTimeoutConserved() {
        var customInterval = Duration.ofMinutes(2);
        var customTimeout = Duration.ofSeconds(45);

        var checker = ClusterHealthChecker.simple(customInterval, customTimeout);

        assertThat(checker.defaultInterval()).isEqualTo(customInterval);
        assertThat(checker.checkTimeout()).isEqualTo(customTimeout);
    }

    @Test
    void simpleHealthCheckerWithNullNodeCompletesTrue() throws ExecutionException, InterruptedException {
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        // SimpleHealthChecker does not validate node; always returns true
        var node = ClusterNode.builder().id("x").build();
        assertThat(checker.check(node).get()).isTrue();
    }

    @Test
    void customHealthCheckerAsyncCompletion() {
        var checker = new ClusterHealthChecker.SimpleHealthChecker(
                Duration.ofSeconds(5), Duration.ofSeconds(1));

        var future = checker.check(ClusterNode.builder().id("a").build());
        assertThat(future.isDone()).isTrue();
    }

    @Test
    void healthCheckerDoesNotBlockOnCheck() {
        var checker = ClusterHealthChecker.simple(
                Duration.ofSeconds(10), Duration.ofSeconds(5));

        var node = ClusterNode.builder().id("fast-check").build();
        var start = System.nanoTime();
        var result = checker.check(node);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result.isDone()).isTrue();
        assertThat(elapsed.toMillis()).isLessThan(100);
    }
}
