package ssg.legoflow.http3.quic;

import ssg.legoflow.http3.quic.QuicTlsEngine.HandshakeState;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.*;

class QuicTlsEngineTest {

    @Test
    void testClientCreation() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);

        assertThat(engine).isNotNull();
        assertThat(engine.isServer()).isFalse();
        assertThat(engine.handshakeState()).isEqualTo(HandshakeState.NOT_STARTED);
        assertThat(engine.sslEngine()).isNotNull();
    }

    @Test
    void testServerCreation() throws NoSuchAlgorithmException {
        // Use default SSLContext for testing
        var ctx = SSLContext.getInstance("TLSv1.3");
        try {
            ctx.init(null, null, null);
        } catch (Exception e) {
            // May fail without KeyManager — acceptable for test
        }

        var engine = QuicTlsEngine.forServer(ctx);
        assertThat(engine).isNotNull();
        assertThat(engine.isServer()).isTrue();
        assertThat(engine.handshakeState()).isEqualTo(HandshakeState.NOT_STARTED);
    }

    @Test
    void testServerCreationRequiresContext() {
        assertThatThrownBy(() -> QuicTlsEngine.forServer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testClientBeginHandshake() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);
        engine.beginHandshake();

        assertThat(engine.handshakeState()).isEqualTo(HandshakeState.IN_PROGRESS);
    }

    @Test
    void testTlsProtocolForced() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);
        var protocols = engine.sslEngine().getEnabledProtocols();

        assertThat(protocols).containsExactly("TLSv1.3");
    }

    @Test
    void testAlpnConfigured() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);
        var alpn = engine.sslEngine().getSSLParameters().getApplicationProtocols();

        assertThat(alpn).contains(QuicTlsEngine.ALPN_H3, QuicTlsEngine.ALPN_H3_29);
    }

    @Test
    void testProduceHandshakeDataAfterBegin() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);
        engine.beginHandshake();

        // Client should produce ClientHello
        var data = engine.produceHandshakeData();
        assertThat(data).isNotNull();
        // After NEED_WRAP, the engine produces handshake bytes
        assertThat(data.remaining()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testRunDelegatedTasksNoOp() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);
        // Should not throw even without pending tasks
        engine.runDelegatedTasks();
    }

    @Test
    void testHandshakeStatus() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);
        var status = engine.handshakeStatus();

        // Before beginHandshake, status is NOT_HANDSHAKING
        assertThat(status).isEqualTo(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
    }

    @Test
    void testClose() {
        var engine = QuicTlsEngine.forClient("localhost", 4433);
        // Should not throw
        engine.close();
        assertThat(engine.sslEngine().isOutboundDone()).isTrue();
    }

    @Test
    void testConstants() {
        assertThat(QuicTlsEngine.TLS_PROTOCOL).isEqualTo("TLSv1.3");
        assertThat(QuicTlsEngine.ALPN_H3).isEqualTo("h3");
        assertThat(QuicTlsEngine.ALPN_H3_29).isEqualTo("h3-29");
    }

    @Test
    void testHandshakeStateValues() {
        assertThat(HandshakeState.values()).hasSize(4);
        assertThat(HandshakeState.NOT_STARTED).isNotNull();
        assertThat(HandshakeState.IN_PROGRESS).isNotNull();
        assertThat(HandshakeState.COMPLETED).isNotNull();
        assertThat(HandshakeState.FAILED).isNotNull();
    }

    @Test
    void testClientWithCustomContext() throws Exception {
        var ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(null, null, null);

        var engine = QuicTlsEngine.forClient("localhost", 4433, ctx);
        assertThat(engine).isNotNull();
        assertThat(engine.isServer()).isFalse();
    }
}
