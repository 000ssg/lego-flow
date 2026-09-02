package ssg.legoflow.acl.ssl;

import org.junit.jupiter.api.*;
import ssg.legoflow.acl.cert.CertificateFactory;
import ssg.legoflow.acl.cert.DomainCerts;
import ssg.legoflow.acl.model.CertificateEntry;

import javax.net.ssl.*;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

class SslContextsTest {

    private DomainCerts certs;
    private char[] password = "changeit".toCharArray();

    @BeforeEach void setUp() {
        certs = CertificateFactory.generateDomainCerts("TestSSL", 2048, 10,
                "server", "client");
    }

    @Test void createServerContext() {
        var serverCert = certs.signedCerts().get(0);
        var ctx = SslContexts.serverContext(serverCert, certs.all(), password);
        assertThat(ctx).isNotNull();
        assertThat(ctx.getProtocol()).isEqualTo("TLS");
    }

    @Test void createClientContext() {
        var clientCert = certs.signedCerts().get(1);
        var ctx = SslContexts.clientContext(clientCert, certs.all(), password);
        assertThat(ctx).isNotNull();
    }

    @Test void trustOnlyContext() {
        var ctx = SslContexts.trustOnlyContext(certs.all(), password);
        assertThat(ctx).isNotNull();
    }

    @Test void serverSslEngine() {
        var serverCert = certs.signedCerts().get(0);
        var ctx = SslContexts.serverContext(serverCert, certs.all(), password);
        var engine = SslContexts.serverEngine(ctx, "localhost", 443);
        assertThat(engine.getUseClientMode()).isFalse();
        assertThat(engine.getEnabledProtocols()).contains("TLSv1.3");
    }

    @Test void clientSslEngine() {
        var clientCert = certs.signedCerts().get(1);
        var ctx = SslContexts.clientContext(clientCert, certs.all(), password);
        var engine = SslContexts.clientEngine(ctx, "localhost", 443);
        assertThat(engine.getUseClientMode()).isTrue();
    }

    @Test void clientAuthServerEngine() {
        var serverCert = certs.signedCerts().get(0);
        var ctx = SslContexts.serverContext(serverCert, certs.all(), password);
        var engine = SslContexts.clientAuthServerEngine(ctx, "localhost", 443);
        assertThat(engine.getNeedClientAuth()).isTrue();
        assertThat(engine.getUseClientMode()).isFalse();
    }

    @Test void fullHandshakeServerClient() throws Exception {
        var serverCert = certs.signedCerts().get(0);
        var clientCert = certs.signedCerts().get(1);
        var trustCerts = certs.all();

        var serverCtx = SslContexts.serverContext(serverCert, trustCerts, password);
        var clientCtx = SslContexts.clientContext(clientCert, trustCerts, password);

        var serverEngine = SslContexts.serverEngine(serverCtx, "localhost", 443);
        var clientEngine = SslContexts.clientEngine(clientCtx, "localhost", 443);

        handshake(serverEngine, clientEngine);

        assertThat(serverEngine.getSession().getProtocol()).isNotNull();
        assertThat(clientEngine.getSession().getProtocol()).isNotNull();
    }

    @Test void handshakeWithClientAuth() throws Exception {
        var serverCert = certs.signedCerts().get(0);
        var clientCert = certs.signedCerts().get(1);
        var trustCerts = certs.all();

        var serverCtx = SslContexts.serverContext(serverCert, trustCerts, password);
        var clientCtx = SslContexts.clientContext(clientCert, trustCerts, password);

        var serverEngine = SslContexts.clientAuthServerEngine(serverCtx, "localhost", 443);
        var clientEngine = SslContexts.clientEngine(clientCtx, "localhost", 443);

        handshake(serverEngine, clientEngine);

        // Verify peer certificates
        var peerCerts = SslContexts.getPeerCertificates(serverEngine);
        assertThat(peerCerts).isNotEmpty();
        var cn = peerCerts[0].getSubjectX500Principal().getName();
        assertThat(cn).contains("client");
    }

    @Test void handshakeTrustOnlyClient() throws Exception {
        var serverCert = certs.signedCerts().get(0);
        var trustCerts = certs.all();

        var serverCtx = SslContexts.serverContext(serverCert, trustCerts, password);
        var clientCtx = SslContexts.trustOnlyContext(trustCerts, password);

        var serverEngine = SslContexts.serverEngine(serverCtx, "localhost", 443);
        var clientEngine = SslContexts.clientEngine(clientCtx, "localhost", 443);

        handshake(serverEngine, clientEngine);

        assertThat(serverEngine.getSession().getProtocol()).isNotNull();
    }

    @Test void encryptedDataTransfer() throws Exception {
        var serverCert = certs.signedCerts().get(0);
        var clientCert = certs.signedCerts().get(1);
        var trustCerts = certs.all();

        var serverCtx = SslContexts.serverContext(serverCert, trustCerts, password);
        var clientCtx = SslContexts.clientContext(clientCert, trustCerts, password);

        var serverEngine = SslContexts.serverEngine(serverCtx, "localhost", 443);
        var clientEngine = SslContexts.clientEngine(clientCtx, "localhost", 443);

        handshake(serverEngine, clientEngine);

        // Send "Hello" from client to server
        var message = "Hello SSL".getBytes();
        var clientToServer = ByteBuffer.wrap(message);
        var serverPlaintext = new byte[1024];
        var serverPlaintextBuf = ByteBuffer.wrap(serverPlaintext);

        clientEngine.wrap(clientToServer, serverPlaintextBuf);
        serverPlaintextBuf.flip();

        var serverDecrypted = new byte[1024];
        var serverDecryptedBuf = ByteBuffer.wrap(serverDecrypted);
        serverEngine.unwrap(serverPlaintextBuf, serverDecryptedBuf);
        serverDecryptedBuf.flip();

        var received = new String(serverDecrypted, 0, serverDecryptedBuf.remaining());
        assertThat(received).isEqualTo("Hello SSL");
    }

    private void handshake(SSLEngine server, SSLEngine client) throws Exception {
        var s2c = ByteBuffer.allocate(16384);
        var c2s = ByteBuffer.allocate(16384);
        var s2cTmp = ByteBuffer.allocate(16384);
        var c2sTmp = ByteBuffer.allocate(16384);

        boolean sDone = false, cDone = false;
        server.beginHandshake();
        client.beginHandshake();

        while (!(sDone && cDone)) {
            if (!sDone) {
                s2c.clear();
                var status = server.wrap(ByteBuffer.allocate(0), s2c);
                s2c.flip();
                if (status.getStatus() == SSLEngineResult.Status.CLOSED) sDone = true;
                else if (status.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) continue;
            }
            if (!cDone) {
                c2s.clear();
                var status = client.wrap(ByteBuffer.allocate(0), c2s);
                c2s.flip();
                if (status.getStatus() == SSLEngineResult.Status.CLOSED) cDone = true;
                else if (status.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) continue;
            }

            // Feed server->client bytes to client
            if (s2c.hasRemaining() && !cDone) {
                s2cTmp.clear();
                client.unwrap(s2c, s2cTmp);
                s2cTmp.flip();
                if (client.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) cDone = true;
            }

            // Feed client->server bytes to server
            if (c2s.hasRemaining() && !sDone) {
                c2sTmp.clear();
                server.unwrap(c2s, c2sTmp);
                c2sTmp.flip();
                if (server.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) sDone = true;
            }

            if (!sDone && !cDone) {
                // Continue wrapping
                continue;
            }
            if (sDone && cDone) break;
        }

        assertThat(server.getHandshakeStatus()).isEqualTo(SSLEngineResult.HandshakeStatus.FINISHED);
        assertThat(client.getHandshakeStatus()).isEqualTo(SSLEngineResult.HandshakeStatus.FINISHED);
    }
}
