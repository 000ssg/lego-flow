package ssg.legoflow.service.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Functional test that exercises all ServiceGroup demo scenarios.
 *
 * @since 1.0.0
 */
class DemoServiceAllTest {

    @Test
    void testRunAll() throws Exception {
        // When: all demos run
        var results = DemoServiceAll.runAll();

        // Then: all scenarios pass
        assertThat(results.serviceGroupLifecycle()).isTrue();
        assertThat(results.serviceGroupUdpEcho()).isGreaterThanOrEqualTo(1);
        assertThat(results.serviceGroupStatistics()).isTrue();
        assertThat(results.serviceGroupMultiSelector()).isTrue();
        assertThat(results.channelManagerBasics()).isTrue();
        assertThat(results.serviceLifecycle()).isTrue();
    }

    @Test
    void testServiceGroupLifecycle() throws Exception {
        assertThat(DemoServiceAll.demoServiceGroupLifecycle()).isTrue();
    }

    @Test
    void testServiceGroupUdpEcho() throws Exception {
        int received = DemoServiceAll.demoServiceGroupUdpEcho();
        assertThat(received).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testServiceGroupStatistics() throws Exception {
        assertThat(DemoServiceAll.demoServiceGroupStatistics()).isTrue();
    }

    @Test
    void testServiceGroupMultiSelector() throws Exception {
        assertThat(DemoServiceAll.demoServiceGroupMultiSelector()).isTrue();
    }

    @Test
    void testChannelManagerBasics() throws Exception {
        assertThat(DemoServiceAll.demoChannelManagerBasics()).isTrue();
    }

    @Test
    void testServiceLifecycle() throws Exception {
        assertThat(DemoServiceAll.demoServiceLifecycle()).isTrue();
    }
}
