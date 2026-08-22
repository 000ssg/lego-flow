package ssg.legoflow.network.dns.transport;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class DnsTransportExtendedTest {

    @Test void tcpTransportConstructor() {
        var transport = new TcpTransport(Duration.ofSeconds(5));
        assertThat(transport).isNotNull();
    }

    @Test void tcpTransportClose() throws Exception {
        var transport = new TcpTransport(Duration.ofSeconds(2));
        transport.close();
    }
}
