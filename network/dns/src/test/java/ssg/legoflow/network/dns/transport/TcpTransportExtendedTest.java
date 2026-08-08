package ssg.legoflow.network.dns.transport;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import ssg.legoflow.network.dns.protocol.DnsMessage;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Extended TCP transport tests for additional coverage.
 */
@Timeout(10)
class TcpTransportExtendedTest {

    @Test void testConstructorWithDefaultTimeout() throws IOException {
        try (var transport = new TcpTransport(Duration.ofSeconds(5))) {
            assertThat(transport).isNotNull();
        }
    }

    @Test void testCloseDoesNotThrow() throws Exception {
        try (var transport = new TcpTransport(Duration.ofSeconds(1))) {
            assertThatCode(() -> transport.close()).doesNotThrowAnyException();
        }
    }

    @Test void testSendToUnreachableServerThrows() throws IOException {
        try (var transport = new TcpTransport(Duration.ofMillis(10))) {
            InetSocketAddress addr = new InetSocketAddress("192.0.2.1", 53);
            
            assertThatThrownBy(() -> transport.send(DnsMessage.query("test.com.", RecordType.A), addr))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test void testConstructorWithShortTimeout() throws IOException {
        try (var transport = new TcpTransport(Duration.ofMillis(100))) {
            assertThat(transport).isNotNull();
        }
    }

    @Test void testSendToLoopbackThrowsConnectionRefused() throws IOException {
        try (var transport = new TcpTransport(Duration.ofMillis(50))) {
            // Connect to a port where nothing is listening
            InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 59999);
            
            var ex = catchThrowable(() -> transport.send(DnsMessage.query("test.com.", RecordType.A), addr));
            assertThat(ex).isInstanceOf(Exception.class);
        }
    }

}
