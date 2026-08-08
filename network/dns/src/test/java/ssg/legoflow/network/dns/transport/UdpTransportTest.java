package ssg.legoflow.network.dns.transport;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class UdpTransportTest {

    @Test
    void testCreateWithTimeout() throws Exception {
        try (UdpTransport transport = new UdpTransport(Duration.ofSeconds(5))) {
            // Verify creation works - channel is opened
        }
    }

    @Test
    void testNullTimeoutThrows() {
        assertThatThrownBy(() -> new UdpTransport(null))
                .isInstanceOf(NullPointerException.class);
    }
}
