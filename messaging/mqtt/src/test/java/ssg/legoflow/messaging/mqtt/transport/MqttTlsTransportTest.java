package ssg.legoflow.messaging.mqtt.transport;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.acl.cert.CertificateFactory;
import ssg.legoflow.acl.ssl.SslContexts;
import ssg.legoflow.messaging.mqtt.codec.InMemoryMqttTransport;
import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests for MqttTlsTransport.
 *
 * <p>Follows the ACL module pattern exactly:
 * - Uses SslContexts.handshake() for the TLS handshake
 * - Creates transports with pre-handshaked engines
 * - Data flow is push-based: onRead(channel, data) pushes peer bytes to the TLS layer
 * - send() wraps and pushes to inner transport
 * - receiveWithTimeout() reads from the ring buffer (filled by onRead)
 */
class MqttTlsTransportTest {

    private static final char[] PASSWORD = "changeit".toCharArray();

    /**
     * Handshake two engines using the ACL module's single-threaded alternating pattern.
     * Based on SslContextsTest.handshake().
     */
    private SSLEngine[] handshake(SSLEngine server, SSLEngine client) throws Exception {
        server.beginHandshake();
        client.beginHandshake();

        int maxBuf = Math.max(client.getSession().getPacketBufferSize() + 32,
                              server.getSession().getPacketBufferSize() + 32);
        var empty = ByteBuffer.allocate(0);
        var c2s = new ArrayList<ByteBuffer>();
        var s2c = new ArrayList<ByteBuffer>();

        while (!(done(server) && done(client))) {
            process(client, c2s, s2c, maxBuf, empty);
            process(server, s2c, c2s, maxBuf, empty);
        }
        return new SSLEngine[]{server, client};
    }

    private boolean done(SSLEngine engine) {
        return engine.getHandshakeStatus() == javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    private void process(SSLEngine engine, List<ByteBuffer> outQ, List<ByteBuffer> inQ,
                          int maxBuf, ByteBuffer empty) throws Exception {
        switch (engine.getHandshakeStatus()) {
            case NEED_WRAP: {
                var bb = ByteBuffer.allocate(maxBuf);
                var r = engine.wrap(empty, bb);
                if (r.bytesProduced() > 0) outQ.add((ByteBuffer) bb.flip());
                break;
            }
            case NEED_UNWRAP:
                if (!inQ.isEmpty()) engine.unwrap(inQ.remove(0), ByteBuffer.allocate(maxBuf));
                break;
            case NEED_TASK:
                runTasks(engine);
                if (engine.getHandshakeStatus() == javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    var bb = ByteBuffer.allocate(maxBuf);
                    var r = engine.wrap(empty, bb);
                    if (r.bytesProduced() > 0) outQ.add((ByteBuffer) bb.flip());
                }
                break;
        }
    }

    private void runTasks(SSLEngine engine) {
        var t = engine.getDelegatedTask();
        while (t != null) { t.run(); t = engine.getDelegatedTask(); }
    }

    /**
     * Create server + client SSLEngines, then handshake them.
     * Uses a dedicated domain so cert CN matches the hostname used in the engine.
     */
    private SSLEngine[] createHandshakedEngines() throws Exception {
        var certs = CertificateFactory.generateDomainCerts("localhost", 2048, 10,
                "localhost", "server", "client");
        var allCerts = certs.all();

        var serverCert = certs.signedCerts().get(0);
        var clientCert = certs.signedCerts().get(1);

        var serverContext = SslContexts.serverContext(serverCert, allCerts, PASSWORD);
        var serverEngine = SslContexts.serverEngine(serverContext, "localhost", 0);

        var clientContext = SslContexts.clientContext(clientCert, allCerts, PASSWORD);
        var clientEngine = SslContexts.clientEngine(clientContext, "localhost", 0);

        // Force TLS 1.2 (same as ACL SslContextsTest.encryptedDataTransfer)
        serverEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
        clientEngine.setEnabledProtocols(new String[]{"TLSv1.2"});

        return handshake(serverEngine, clientEngine);
    }

    /**
     * Returns [serverTls, clientTls, serverInner, clientInner].
     */
    private Object[] createTlsPair() throws Exception {
        var engines = createHandshakedEngines();
        var pair = InMemoryMqttTransport.createPair();
        var serverTls = new MqttTlsTransport(pair[0], engines[0]);
        var clientTls = new MqttTlsTransport(pair[1], engines[1]);
        return new Object[]{serverTls, clientTls, pair[0], pair[1]};
    }

    @Test void transportIsOpenOnCreation() throws Exception {
        var res = createTlsPair();
        assertThat(((MqttTransport) res[0]).isOpen()).isTrue();
        assertThat(((MqttTransport) res[1]).isOpen()).isTrue();
    }

    @Test void handshakeCompleteOnCreation() throws Exception {
        var res = createTlsPair();
        assertThat(((MqttTlsTransport) res[0]).isHandshakeComplete()).isTrue();
        assertThat(((MqttTlsTransport) res[1]).isHandshakeComplete()).isTrue();
    }

    @Test void tlsWrapUnwrap() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        var clientTls = (MqttTlsTransport) res[1];
        var serverInner = (InMemoryMqttTransport) res[2];

        // Client sends app data -> wraps -> goes to serverInner -> server reads via onRead -> unwraps
        clientTls.send(ByteBuffer.wrap("Hello".getBytes()));

        // Server reads encrypted bytes from inner transport
        var netBuf = ByteBuffer.allocate(serverTls.engine().getSession().getPacketBufferSize());
        int n = serverInner.receiveWithTimeout(netBuf, 2, TimeUnit.SECONDS);
        assertThat(n).as("server should receive encrypted data from inner").isGreaterThan(0);
        netBuf.flip();

        // Push encrypted bytes through server's TLS layer
        serverTls.onRead(null, netBuf);

        // Server reads decrypted data
        var appBuf = ByteBuffer.allocate(256);
        int received = serverTls.receiveWithTimeout(appBuf, 2, TimeUnit.SECONDS);
        assertThat(received).isEqualTo(5);
        appBuf.flip();
        assertThat(new String(appBuf.array(), 0, received)).isEqualTo("Hello");
    }

    @Test void twoWayCommunication() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        var clientTls = (MqttTlsTransport) res[1];
        var serverInner = (InMemoryMqttTransport) res[2];
        var clientInner = (InMemoryMqttTransport) res[3];

        // Client -> Server
        clientTls.send(ByteBuffer.wrap("Hello".getBytes()));
        var netBuf = ByteBuffer.allocate(serverTls.engine().getSession().getPacketBufferSize());
        int n = serverInner.receiveWithTimeout(netBuf, 2, TimeUnit.SECONDS);
        assertThat(n).as("server inner should receive client data").isGreaterThan(0);
        netBuf.flip();
        serverTls.onRead(null, netBuf);
        var buf = ByteBuffer.allocate(256);
        int received = serverTls.receiveWithTimeout(buf, 2, TimeUnit.SECONDS);
        assertThat(received).isEqualTo(5);
        buf.flip();
        assertThat(new String(buf.array(), 0, received)).isEqualTo("Hello");

        // Server -> Client
        serverTls.send(ByteBuffer.wrap("World".getBytes()));
        netBuf.clear();
        n = clientInner.receiveWithTimeout(netBuf, 2, TimeUnit.SECONDS);
        assertThat(n).as("client inner should receive server data").isGreaterThan(0);
        netBuf.flip();
        clientTls.onRead(null, netBuf);
        buf.clear();
        received = clientTls.receiveWithTimeout(buf, 2, TimeUnit.SECONDS);
        assertThat(received).isEqualTo(5);
        buf.flip();
        assertThat(new String(buf.array(), 0, received)).isEqualTo("World");
    }

    @Test void closeClosesInner() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        var inner = (InMemoryMqttTransport) res[2];
        serverTls.close();
        assertThat(serverTls.isOpen()).isFalse();
        assertThat(inner.isOpen()).isFalse();
    }

    @Test void getInnerTransportReturnsWrappedTransport() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        var inner = (InMemoryMqttTransport) res[2];
        assertThat(serverTls.getInnerTransport()).isSameAs(inner);
    }

    @Test void sendWhenClosedIsIgnored() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        serverTls.close();
        // Should not throw
        serverTls.send(ByteBuffer.wrap("ignored".getBytes()));
    }

    @Test void receiveReturnsMinusOneWhenClosed() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        serverTls.close();
        var buf = ByteBuffer.allocate(256);
        assertThat(serverTls.receiveWithTimeout(buf, 100, TimeUnit.MILLISECONDS)).isEqualTo(-1);
    }

    @Test void receiveTimeoutReturnsMinusOne() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        var buf = ByteBuffer.allocate(256);
        // No data pushed — should timeout
        assertThat(serverTls.receiveWithTimeout(buf, 100, TimeUnit.MILLISECONDS)).isEqualTo(-1);
    }

    @Test void multipleMessagesInRingBuffer() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        var serverInner = (InMemoryMqttTransport) res[2];
        var clientTls = (MqttTlsTransport) res[1];

        // Client sends 3 messages
        clientTls.send(ByteBuffer.wrap("AAA".getBytes()));
        clientTls.send(ByteBuffer.wrap("BBB".getBytes()));
        clientTls.send(ByteBuffer.wrap("CCC".getBytes()));

        // Server reads all 3
        var netBuf = ByteBuffer.allocate(serverTls.engine().getSession().getPacketBufferSize());
        for (int i = 0; i < 3; i++) {
            netBuf.clear();
            int n = serverInner.receiveWithTimeout(netBuf, 2, TimeUnit.SECONDS);
            assertThat(n).as("msg " + i).isGreaterThan(0);
            netBuf.flip();
            serverTls.onRead(null, netBuf);
        }

        // TLS does not preserve message boundaries — all 9 bytes may arrive together
        var buf = ByteBuffer.allocate(256);
        int n = serverTls.receiveWithTimeout(buf, 2, TimeUnit.SECONDS);
        assertThat(n).isEqualTo(9); // All three 3-byte messages accumulated
        buf.flip();
        assertThat(buf.remaining()).isEqualTo(9);
    }

    /** Cover send when closed and receive timeout paths. */
    @Test void closedTransportRejectsSendAndReceive() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        var inner = (InMemoryMqttTransport) res[2];

        serverTls.close();
        assertThat(serverTls.isOpen()).isFalse();

        // Send on closed transport should be a no-op
        serverTls.send(ByteBuffer.wrap("test".getBytes()));

        // Receive on closed transport returns -1 immediately
        var buf = ByteBuffer.allocate(256);
        assertThat(serverTls.receiveWithTimeout(buf, 100, TimeUnit.MILLISECONDS)).isEqualTo(-1);
    }

    @Test void getChannelReturnsInnerChannel() throws Exception {
        var res = createTlsPair();
        var serverTls = (MqttTlsTransport) res[0];
        assertThat(serverTls.getChannel()).isNull();
    }
}
