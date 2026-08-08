package ssg.legoflow.xmpp.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TlsHandler}.
 *
 * @since 0.1.0
 */
class TlsHandlerTest {

    private TlsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TlsHandler("example.com", 5222);
    }

    @Test
    void testInitialState() {
        assertThat(handler.getState()).isEqualTo(TlsHandler.TlsState.NOT_STARTED);
        assertThat(handler.isEstablished()).isFalse();
        assertThat(handler.getHostname()).isEqualTo("example.com");
        assertThat(handler.getPort()).isEqualTo(5222);
    }

    @Test
    void testGenerateStartTlsXml() {
        String xml = handler.generateStartTlsXml();

        assertThat(xml).contains("starttls");
        assertThat(xml).contains(TlsHandler.NAMESPACE);
        assertThat(handler.getState()).isEqualTo(TlsHandler.TlsState.STARTTLS_REQUESTED);
    }

    @Test
    void testHandleProceed() {
        handler.generateStartTlsXml();
        handler.handleProceed();

        assertThat(handler.getState()).isEqualTo(TlsHandler.TlsState.PROCEED_RECEIVED);
    }

    @Test
    void testHandleProceedInWrongStateThrows() {
        assertThatThrownBy(() -> handler.handleProceed())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testBeginHandshake() throws Exception {
        handler.generateStartTlsXml();
        handler.handleProceed();

        var data = handler.beginHandshake();
        assertThat(data).isNotNull();
        assertThat(handler.getState()).isEqualTo(TlsHandler.TlsState.HANDSHAKING);
    }

    @Test
    void testBeginHandshakeInWrongStateThrows() {
        // Put handler into HANDSHAKING state first, which is invalid for beginHandshake
        handler.generateStartTlsXml();
        handler.handleProceed();
        try { handler.beginHandshake(); } catch (Exception ignored) {}
        // Now in HANDSHAKING state, calling again should throw
        assertThatThrownBy(() -> handler.beginHandshake())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testSslEngineAvailable() {
        assertThat(handler.getSslEngine()).isNotNull();
        assertThat(handler.getSslEngine().getUseClientMode()).isTrue();
    }

    @Test
    void testProtocolAndCipherBeforeEstablished() {
        assertThat(handler.getProtocol()).isNull();
        assertThat(handler.getCipherSuite()).isNull();
    }

    @Test
    void testWrapBeforeEstablishedThrows() {
        assertThatThrownBy(() -> handler.wrap(java.nio.ByteBuffer.allocate(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not established");
    }

    @Test
    void testUnwrapBeforeEstablishedThrows() {
        assertThatThrownBy(() -> handler.unwrap(java.nio.ByteBuffer.allocate(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not established");
    }

    @Test
    void testClose() {
        handler.close();
        // Should not throw; SSLEngine closeOutbound is called
        assertThat(handler.getSslEngine().isOutboundDone()).isTrue();
    }

    @Test
    void testAllTlsStates() {
        for (var state : TlsHandler.TlsState.values()) {
            assertThat(state).isNotNull();
        }
        assertThat(TlsHandler.TlsState.values()).hasSize(6);
    }

    @Test
    void testNamespaceConstant() {
        assertThat(TlsHandler.NAMESPACE).isEqualTo("urn:ietf:params:xml:ns:xmpp-tls");
    }
}
