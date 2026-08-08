package ssg.legoflow.network.dns.transport;
import org.junit.jupiter.api.Timeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class TcpTransportTest {

    @Test
    void testCreateWithTimeout() throws Exception {
        try (TcpTransport transport = new TcpTransport(Duration.ofSeconds(5))) {
            // Verify creation works
        }
    }

    @Test
    void testClose() throws Exception {
        TcpTransport transport = new TcpTransport(Duration.ofSeconds(1));
        transport.close();
    }
}
