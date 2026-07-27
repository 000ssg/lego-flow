package ssg.legoflow.http.proxy.reverse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckerTest {

    private HealthChecker checker;

    @BeforeEach
    void setUp() {
        checker = new HealthChecker(Duration.ofMillis(100), Duration.ofSeconds(1), 3, 2);
    }

    @AfterEach
    void tearDown() {
        checker.close();
    }

    @Test
    void testAddAndRemoveBackend() {
        var backend = new BackendServer("b1", 8080);
        checker.addBackend(backend);
        assertThat(checker.getBackends()).hasSize(1);
        checker.removeBackend(backend);
        assertThat(checker.getBackends()).isEmpty();
    }

    @Test
    void testRunChecksMarksHealthy() {
        var backend = new BackendServer("b1", 8080);
        checker.addBackend(backend);
        checker.setHealthCheckFunction(_ -> true);
        checker.runChecks();
        assertThat(backend.isHealthy()).isTrue();
        assertThat(checker.getCheckCount()).isEqualTo(1);
    }

    @Test
    void testRunChecksMarksUnhealthy() {
        var backend = new BackendServer("b1", 8080);
        checker.addBackend(backend);
        checker.setHealthCheckFunction(_ -> false);
        checker.runChecks();
        assertThat(backend.isHealthy()).isFalse();
    }

    @Test
    void testRunChecksHandlesException() {
        var backend = new BackendServer("b1", 8080);
        checker.addBackend(backend);
        checker.setHealthCheckFunction(_ -> { throw new RuntimeException("fail"); });
        checker.runChecks();
        assertThat(backend.isHealthy()).isFalse();
    }

    @Test
    void testStartAndStop() throws InterruptedException {
        var backend = new BackendServer("b1", 8080);
        checker.addBackend(backend);
        checker.setHealthCheckFunction(_ -> true);

        checker.start();
        assertThat(checker.isRunning()).isTrue();
        Thread.sleep(300);
        assertThat(checker.getCheckCount()).isGreaterThan(0);

        checker.stop();
        assertThat(checker.isRunning()).isFalse();
    }

    @Test
    void testDoubleStartIgnored() {
        checker.start();
        checker.start(); // should not throw
        assertThat(checker.isRunning()).isTrue();
        checker.stop();
    }

    @Test
    void testDoubleStopIgnored() {
        checker.start();
        checker.stop();
        checker.stop(); // should not throw
        assertThat(checker.isRunning()).isFalse();
    }

    @Test
    void testCheckInterval() {
        assertThat(checker.getCheckInterval()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void testThresholds() {
        assertThat(checker.getUnhealthyThreshold()).isEqualTo(3);
        assertThat(checker.getHealthyThreshold()).isEqualTo(2);
    }

    @Test
    void testDefaultHealthCheckAlwaysHealthy() {
        var defaultChecker = new HealthChecker();
        var backend = new BackendServer("b1", 8080);
        defaultChecker.addBackend(backend);
        defaultChecker.runChecks();
        assertThat(backend.isHealthy()).isTrue();
        defaultChecker.close();
    }

    @Test
    void testMultipleBackends() {
        var b1 = new BackendServer("b1", 8081);
        var b2 = new BackendServer("b2", 8082);
        checker.addBackend(b1);
        checker.addBackend(b2);
        checker.setHealthCheckFunction(backend -> backend.getHost().equals("b1"));
        checker.runChecks();
        assertThat(b1.isHealthy()).isTrue();
        assertThat(b2.isHealthy()).isFalse();
    }

    @Test
    void testCloseStopsChecker() {
        checker.start();
        checker.close();
        assertThat(checker.isRunning()).isFalse();
    }
}
