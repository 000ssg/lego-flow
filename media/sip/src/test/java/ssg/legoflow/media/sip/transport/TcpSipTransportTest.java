package ssg.legoflow.media.sip.transport;

import org.junit.jupiter.api.*;
import ssg.legoflow.media.sip.protocol.SipCodec;
import ssg.legoflow.media.sip.protocol.SipMessage;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TcpSipTransport}.
 */
class TcpSipTransportTest {

    private TcpSipTransport transport;

    @BeforeEach
    void setUp() throws IOException {
        transport = new TcpSipTransport(new InetSocketAddress(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (transport != null) {
            try { transport.close(); } catch (Exception ignored) {}
        }
        Thread.sleep(100);
    }

    @Test
    void testConstructorWithSpecificPort() throws IOException {
        var t = new TcpSipTransport(new InetSocketAddress(0));
        var addr = t.localAddress();
        assertThat(addr.getPort()).isGreaterThan(0).isLessThan(65536);
        t.close();
    }

    @Test
    void testConstructorWithDefaultNoArg() throws IOException {
        var t = new TcpSipTransport();
        assertThat(t.localAddress().getPort()).isGreaterThan(0);
        t.close();
    }

    @Test
    void testProtocol() throws IOException {
        assertThat(transport.protocol()).isEqualTo("TCP");
    }

    @Test
    void testIsReliable() throws IOException {
        assertThat(transport.isReliable()).isTrue();
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
        
        // Should still be closable after starting
        assertThatCode(transport::close).doesNotThrowAnyException();
    }

    @Test
    void testToStringContainsInfo() throws IOException {
        var str = transport.toString();
        assertThat(str).contains("TcpSipTransport");
    }

    @Test
    void testAutoCloseable() throws Exception {
        try (TcpSipTransport t = new TcpSipTransport()) {
            assertThat(t.localAddress()).isNotNull();
        }
    }

    @Test
    void testBindToSpecificPort() throws IOException {
        int port = 19080 + (int)(Math.random() * 500);
        var t = new TcpSipTransport(new InetSocketAddress("127.0.0.1", port));
        assertThat(t.localAddress().getPort()).isEqualTo(port);
        t.close();
    }
}
