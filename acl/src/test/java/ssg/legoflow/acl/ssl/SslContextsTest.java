package ssg.legoflow.acl.ssl;

import org.junit.jupiter.api.*;
import ssg.legoflow.acl.cert.CertificateFactory;
import ssg.legoflow.acl.cert.DomainCerts;

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SslContextsTest {

    private DomainCerts certs;
    private char[] password = "changeit".toCharArray();

    @BeforeEach void setUp() {
        certs = CertificateFactory.generateDomainCerts("TestSSL", 2048, 10,
                "server", "client");
    }

    @Test void createServerContext() {
        var ctx = SslContexts.serverContext(certs.signedCerts().get(0), certs.all(), password);
        assertThat(ctx).isNotNull();
        assertThat(ctx.getProtocol()).isEqualTo("TLS");
    }

    @Test void createClientContext() {
        var ctx = SslContexts.clientContext(certs.signedCerts().get(1), certs.all(), password);
        assertThat(ctx).isNotNull();
    }

    @Test void trustOnlyContext() {
        var ctx = SslContexts.trustOnlyContext(certs.all(), password);
        assertThat(ctx).isNotNull();
    }

    @Test void serverSslEngine() {
        var ctx = SslContexts.serverContext(certs.signedCerts().get(0), certs.all(), password);
        var engine = SslContexts.serverEngine(ctx, "localhost", 443);
        assertThat(engine.getUseClientMode()).isFalse();
        assertThat(engine.getEnabledProtocols()).contains("TLSv1.3");
    }

    @Test void clientSslEngine() {
        var ctx = SslContexts.clientContext(certs.signedCerts().get(1), certs.all(), password);
        var engine = SslContexts.clientEngine(ctx, "localhost", 443);
        assertThat(engine.getUseClientMode()).isTrue();
    }

    @Test void clientAuthServerEngine() {
        var ctx = SslContexts.serverContext(certs.signedCerts().get(0), certs.all(), password);
        var engine = SslContexts.clientAuthServerEngine(ctx, "localhost", 443);
        assertThat(engine.getNeedClientAuth()).isTrue();
        assertThat(engine.getUseClientMode()).isFalse();
    }

    @Test void fullHandshakeServerClient() throws Exception {
        var sCtx = SslContexts.serverContext(certs.signedCerts().get(0), certs.all(), password);
        var cCtx = SslContexts.clientContext(certs.signedCerts().get(1), certs.all(), password);
        handshake(SslContexts.serverEngine(sCtx, "localhost", 443),
                  SslContexts.clientEngine(cCtx, "localhost", 443));
    }

    @Test void handshakeWithClientAuth() throws Exception {
        var sCtx = SslContexts.serverContext(certs.signedCerts().get(0), certs.all(), password);
        var cCtx = SslContexts.clientContext(certs.signedCerts().get(1), certs.all(), password);
        var srv = SslContexts.clientAuthServerEngine(sCtx, "localhost", 443);
        var cln = SslContexts.clientEngine(cCtx, "localhost", 443);
        handshake(srv, cln);
        var peerCerts = SslContexts.getPeerCertificates(srv);
        assertThat(peerCerts).isNotEmpty();
        assertThat(peerCerts[0].getSubjectX500Principal().getName()).contains("client");
    }

    @Test void handshakeTrustOnlyClient() throws Exception {
        var sCtx = SslContexts.serverContext(certs.signedCerts().get(0), certs.all(), password);
        var cCtx = SslContexts.trustOnlyContext(certs.all(), password);
        handshake(SslContexts.serverEngine(sCtx, "localhost", 443),
                  SslContexts.clientEngine(cCtx, "localhost", 443));
    }

    @Test void encryptedDataTransfer() throws Exception {
        var sCtx = SslContexts.serverContext(certs.signedCerts().get(0), certs.all(), password);
        var cCtx = SslContexts.clientContext(certs.signedCerts().get(1), certs.all(), password);
        var srv = SslContexts.serverEngine(sCtx, "localhost", 443);
        var cln = SslContexts.clientEngine(cCtx, "localhost", 443);

        // Force TLS 1.2
        srv.setEnabledProtocols(new String[]{"TLSv1.2"});
        cln.setEnabledProtocols(new String[]{"TLSv1.2"});

        srv.beginHandshake();
        cln.beginHandshake();

        int maxBuf = Math.max(cln.getSession().getPacketBufferSize() + 32,
                              srv.getSession().getPacketBufferSize() + 32);
        var empty = ByteBuffer.allocate(0);
        var c2s = new ArrayList<ByteBuffer>();
        var s2c = new ArrayList<ByteBuffer>();

        // Handshake
        while (!(done(srv) && done(cln))) {
            process(srv, s2c, c2s, maxBuf, empty);
            process(cln, c2s, s2c, maxBuf, empty);
        }

        // Data: client wraps, passes through c2s, server unwraps
        var serverOut = ByteBuffer.allocate(maxBuf);
        var appData = ByteBuffer.wrap("Hello SSL".getBytes());
        var enc = ByteBuffer.allocate(maxBuf);
        var wr = cln.wrap(appData, enc);
        enc.flip();
        c2s.add((ByteBuffer) ByteBuffer.allocate(enc.remaining()).put(enc).flip());

        var src = c2s.remove(0);
        var ur = srv.unwrap(src, serverOut);
        assertThat(ur.bytesProduced()).isGreaterThan(0);

        serverOut.flip();
        var received = new String(serverOut.array(), 0, serverOut.remaining());
        assertThat(received).isEqualTo("Hello SSL");
    }

    /** Run handshake until both engines report FINISHED or NOT_HANDSHAKING. */
    private void handshake(SSLEngine server, SSLEngine client) throws Exception {
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
    }

    /** Process one SSLEngine step during handshake. */
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
                if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    var bb = ByteBuffer.allocate(maxBuf);
                    var r = engine.wrap(empty, bb);
                    if (r.bytesProduced() > 0) outQ.add((ByteBuffer) bb.flip());
                }
                break;
        }
    }

    private boolean done(SSLEngine engine) {
        var s = engine.getHandshakeStatus();
        return s == SSLEngineResult.HandshakeStatus.FINISHED
            || s == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    private void runTasks(SSLEngine engine) {
        var t = engine.getDelegatedTask();
        while (t != null) { t.run(); t = engine.getDelegatedTask(); }
    }
}
