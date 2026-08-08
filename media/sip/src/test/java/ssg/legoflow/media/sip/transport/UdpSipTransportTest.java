package ssg.legoflow.media.sip.transport;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link UdpSipTransport}.
 */
class UdpSipTransportTest {

    private UdpSipTransport transport;

    @BeforeEach
    void setUp() throws IOException {
        transport = new UdpSipTransport(new InetSocketAddress(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (transport != null) {
            try { transport.close(); } catch (Exception ignored) {}
        }
        Thread.sleep(100);
    }

    @Test
    void testConstructorWithDefaultNoArg() throws IOException {
        var t = new UdpSipTransport();
        assertThat(t.localAddress().getPort()).isGreaterThan(0);
        t.close();
    }

    @Test
    void testProtocol() throws IOException {
        assertThat(transport.protocol()).isEqualTo("UDP");
    }

    @Test
    void testIsReliable() throws IOException {
        assertThat(transport.isReliable()).isFalse();
    }

    @Test
    void testLocalAddress() throws IOException {
        var addr = transport.localAddress();
        assertThat(addr).isNotNull();
        assertThat(addr.getPort()).isGreaterThan(0);
    }

    @Test
    void testCloseWithoutStart() throws Exception {
        assertThatCode(transport::close).doesNotThrowAnyException();
    }

    @Test
    void testStartAndClose() throws Exception {
        transport.start((msg, source) -> {});
        Thread.sleep(200);
        
        assertThatCode(transport::close).doesNotThrowAnyException();
    }

    @Test
    void testToStringContainsInfo() throws IOException {
        var str = transport.toString();
        assertThat(str).contains("UdpSipTransport");
    }

    @Test
    void testAutoCloseable() throws Exception {
        try (UdpSipTransport t = new UdpSipTransport()) {
            assertThat(t.localAddress()).isNotNull();
        }
    }

    @Test
    void testBindToSpecificPort() throws IOException {
        int port = 19090 + (int)(Math.random() * 500);
        var t = new UdpSipTransport(new InetSocketAddress("127.0.0.1", port));
        assertThat(t.localAddress().getPort()).isEqualTo(port);
        t.close();
    }
}
