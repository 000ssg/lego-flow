package ssg.legoflow.http3.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ZeroRttDemoTest {

    @Test
    void testInitialConnect() {
        // Given
        var demo = new ZeroRttDemo();

        // When
        var connection = demo.initialConnect();

        // Then
        assertThat(connection).isNotNull();
        assertThat(connection.isConnected()).isTrue();
        assertThat(demo.firstConnection()).isSameAs(connection);
    }

    @Test
    void testResumeConnection() {
        // Given
        var demo = new ZeroRttDemo();
        demo.initialConnect();

        // When: resume (simulated 0-RTT)
        var resumed = demo.resumeConnection();

        // Then
        assertThat(resumed).isNotNull();
        assertThat(resumed.isConnected()).isTrue();
        assertThat(resumed).isNotSameAs(demo.firstConnection());
    }

    @Test
    void testResumeWithoutInitialThrows() {
        // Given
        var demo = new ZeroRttDemo();

        // When/Then
        assertThatThrownBy(demo::resumeConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("initialConnect");
    }

    @Test
    void testConfigHas0RttEnabled() {
        // Given/When
        var demo = new ZeroRttDemo();

        // Then
        assertThat(demo.config().enable0Rtt()).isTrue();
    }
}
