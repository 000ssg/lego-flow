package ssg.legoflow.messaging.mqtt.broker;

import org.junit.jupiter.api.*;
import ssg.legoflow.acl.cert.CertificateFactory;
import ssg.legoflow.acl.cert.DomainCerts;
import ssg.legoflow.acl.model.CertificateEntry;
import ssg.legoflow.acl.ssl.SslContexts;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.ConnectReturnCode;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/**
 * TLS integration tests for MQTT using in-memory certificates from the acl module.
 *
 * <p>Validates that {@link MqttTlsConfig} works with pre-built {@link SSLContext}
 * (no file-based keystores required) by performing a real TLS handshake between
 * a broker-side SSLEngine and a client-side SSLEngine in memory.
 *
 * @since 0.1.0
 */
class MqttTlsIntegrationTest {

    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    private static DomainCerts certs;
    private static SSLContext serverContext;
    private static SSLContext clientContext;

    @BeforeAll
    static void setUpCerts() {
        // Generate CA-signed certs for broker and client
        certs = CertificateFactory.generateDomainCerts(
                "mqtt-test", 2048, 10,
                "mqtt-broker", "mqtt-client");
        // Trust CA + all domain certs for mutual trust
        Collection<CertificateEntry> trustCerts = List.of(certs.ca(), certs.signedCerts().get(0), certs.signedCerts().get(1));
        // Server (broker) context
        serverContext = SslContexts.serverContext(certs.signedCerts().get(0), trustCerts, STORE_PASSWORD);
        // Client context
        clientContext = SslContexts.clientContext(certs.signedCerts().get(1), trustCerts, STORE_PASSWORD);
    }

    @Test
    void testSslContextsCreated() {
        assertThat(serverContext).isNotNull();
        assertThat(clientContext).isNotNull();
        // Verify engine creation
        SSLEngine serverEngine = SslContexts.serverEngine(serverContext, "localhost", 8883);
        assertThat(serverEngine.getUseClientMode()).isFalse();
        SSLEngine clientEngine = SslContexts.clientEngine(clientContext, "localhost", 8883);
        assertThat(clientEngine.getUseClientMode()).isTrue();
    }

    @Test
    void testTlsConfigWithSslContext() throws Exception {
        var config = MqttTlsConfig.builder()
                .sslContext(serverContext)
                .protocols(List.of("TLSv1.2"))
                .build();
        assertThat(config.sslContext()).isSameAs(serverContext);
        assertThat(config.protocols()).containsExactly("TLSv1.2");
        assertThat(config.createSslContext()).isSameAs(serverContext);
    }

    @Test
    void testTlsConfigRequiresKeystoreOrSslContext() {
        assertThatThrownBy(() -> MqttTlsConfig.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keystore path and password, or pre-built SSLContext");
    }

    @Test
    void testTlsHandshakeInMemory() throws Exception {
        // Build engines using MqttTlsConfig (the path used by broker/client)
        var serverTlsConfig = MqttTlsConfig.builder()
                .sslContext(serverContext)
                .protocols(List.of("TLSv1.2"))
                .build();
        var clientTlsConfig = MqttTlsConfig.builder()
                .sslContext(clientContext)
                .protocols(List.of("TLSv1.2"))
                .build();

        // Server engine
        SSLEngine serverEngine = serverTlsConfig.createServerEngine(serverContext);
        // Client engine
        SSLEngine clientEngine = clientTlsConfig.createClientEngine(clientContext, "localhost", 1883);

        // Perform handshake in memory (no network, pure SSLEngine cycle)
        handshake(serverEngine, clientEngine);

        // Verify handshake completed
        assertThat(serverEngine.getHandshakeStatus()).isEqualTo(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
        assertThat(clientEngine.getHandshakeStatus()).isEqualTo(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);

        // Verify TLSv1.2 was negotiated
        assertThat(serverEngine.getSession().getProtocol()).isEqualTo("TLSv1.2");
        assertThat(clientEngine.getSession().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    void testTlsDataExchange() throws Exception {
        // Build engines
        SSLEngine serverEngine = SslContexts.serverEngine(serverContext, "localhost", 1883);
        SSLEngine clientEngine = SslContexts.clientEngine(clientContext, "localhost", 1883);

        // Configure TLSv1.2
        serverEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
        clientEngine.setEnabledProtocols(new String[]{"TLSv1.2"});

        // Handshake
        handshake(serverEngine, clientEngine);

        // Client sends data through TLS
        String plainText = "Hello MQTT over TLS!";
        ByteBuffer clientAppOut = ByteBuffer.wrap(plainText.getBytes());
        ByteBuffer clientNetOut = ByteBuffer.allocate(clientEngine.getSession().getPacketBufferSize());
        clientEngine.wrap(clientAppOut, clientNetOut);
        clientNetOut.flip();

        // Server reads encrypted data and unwraps
        ByteBuffer serverNetIn = clientNetOut; // direct pipe
        ByteBuffer serverAppIn = ByteBuffer.allocate(serverEngine.getSession().getApplicationBufferSize());
        SSLEngineResult res = serverEngine.unwrap(serverNetIn, serverAppIn);
        assertThat(res.getStatus()).isEqualTo(SSLEngineResult.Status.OK);
        serverAppIn.flip();
        byte[] received = new byte[serverAppIn.remaining()];
        serverAppIn.get(received);
        assertThat(new String(received)).isEqualTo(plainText);
    }

    @Test
    void testUntrustedClientFailsHandshake() throws Exception {
        // Create a different CA + client cert that broker doesn't trust
        var untrustedCerts = CertificateFactory.generateDomainCerts("untrusted", 2048, 10, "untrusted-client");
        SSLContext untrustedClientCtx = SslContexts.clientContext(
                untrustedCerts.signedCerts().get(0),
                List.of(untrustedCerts.ca()),
                STORE_PASSWORD);

        SSLEngine serverEngine = SslContexts.serverEngine(serverContext, "localhost", 1883);
        serverEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
        SSLEngine clientEngine = SslContexts.clientEngine(untrustedClientCtx, "localhost", 1883);
        clientEngine.setEnabledProtocols(new String[]{"TLSv1.2"});

        // Handshake should fail
        assertThatThrownBy(() -> handshake(serverEngine, clientEngine))
                .isInstanceOf(Exception.class);
    }

    /**
     * Performs a TLS handshake between two SSLEngines in memory.
     * Both engines exchange encrypted bytes until handshake completes or fails.
     */
    private static void handshake(SSLEngine server, SSLEngine client) throws Exception {
        server.beginHandshake();
        client.beginHandshake();

        int netSize = Math.max(server.getSession().getPacketBufferSize(),
                client.getSession().getPacketBufferSize());
        ByteBuffer s2c = ByteBuffer.allocate(netSize); // server -> client
        ByteBuffer c2s = ByteBuffer.allocate(netSize); // client -> server
        ByteBuffer serverApp = ByteBuffer.allocate(server.getSession().getApplicationBufferSize());
        ByteBuffer clientApp = ByteBuffer.allocate(client.getSession().getApplicationBufferSize());

        for (int i = 0; i < 100; i++) {
            // Run delegated tasks
            Runnable task;
            while ((task = server.getDelegatedTask()) != null) task.run();
            while ((task = client.getDelegatedTask()) != null) task.run();

            // Server wraps -> client unwraps
            s2c.clear();
            SSLEngineResult sWrap = server.wrap(serverApp, s2c);
            s2c.flip();
            if (s2c.hasRemaining()) {
                client.unwrap(s2c, clientApp);
                s2c.compact();
            }

            // Client wraps -> server unwraps
            c2s.clear();
            SSLEngineResult cWrap = client.wrap(clientApp, c2s);
            c2s.flip();
            if (c2s.hasRemaining()) {
                server.unwrap(c2s, serverApp);
                c2s.compact();
            }

            // Check if both finished
            if (server.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                    && client.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                return;
            }
        }
        throw new AssertionError("TLS handshake did not complete after 100 iterations: "
                + "server=" + server.getHandshakeStatus()
                + ", client=" + client.getHandshakeStatus());
    }
}
