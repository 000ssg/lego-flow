package ssg.legoflow.http3.quic;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link QuicConnection} — lifecycle, settings, state transitions,
 * TLS 1.3 handshake phases, and invalid transitions.
 *
 * @since 0.1.0
 */
class QuicConnectionTest {

    @Test
    void testInitialState() {
        var conn = new QuicConnection(1L, false);

        assertThat(conn.getState()).isEqualTo(QuicConnectionState.IDLE);
        assertThat(conn.isConnected()).isFalse();
        assertThat(conn.sourceConnectionId()).isEqualTo(1L);
    }

    @Test
    void testConnectLifecycle() {
        var conn = new QuicConnection(1L, false);
        var address = new InetSocketAddress("localhost", 443);

        conn.connect(address);

        assertThat(conn.getState()).isEqualTo(QuicConnectionState.CONNECTED);
        assertThat(conn.isConnected()).isTrue();
        assertThat(conn.remoteAddress()).isEqualTo(address);
    }

    @Test
    void testAcceptLifecycle() {
        var conn = new QuicConnection(2L, true);

        conn.accept();

        assertThat(conn.getState()).isEqualTo(QuicConnectionState.CONNECTED);
        assertThat(conn.isConnected()).isTrue();
    }

    @Test
    void testConnectThenClose() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));

        conn.close(QuicErrorCode.NO_ERROR, "graceful shutdown");

        assertThat(conn.getState()).isEqualTo(QuicConnectionState.CLOSED);
        assertThat(conn.isConnected()).isFalse();
    }

    @Test
    void testCloseAlreadyClosed() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));
        conn.close(QuicErrorCode.NO_ERROR, "first close");

        // Should not throw
        conn.close(QuicErrorCode.NO_ERROR, "second close");
        assertThat(conn.getState()).isEqualTo(QuicConnectionState.CLOSED);
    }

    @Test
    void testCannotConnectWhenAlreadyConnected() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));

        assertThatThrownBy(() -> conn.connect(new InetSocketAddress("localhost", 8443)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testDefaultSettings() {
        var conn = new QuicConnection(1L, false);

        assertThat(conn.localSettings().maxIdleTimeout()).isEqualTo(30_000);
        assertThat(conn.localSettings().initialMaxData()).isEqualTo(1_048_576);
    }

    @Test
    void testCustomSettings() {
        var settings = QuicSettings.builder()
                .maxIdleTimeout(60_000)
                .initialMaxData(2_097_152)
                .build();
        var conn = new QuicConnection(1L, false, settings);

        assertThat(conn.localSettings().maxIdleTimeout()).isEqualTo(60_000);
        assertThat(conn.localSettings().initialMaxData()).isEqualTo(2_097_152);
    }

    @Test
    void testCreateBidiStream() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));

        var stream = conn.createStream(true);

        assertThat(stream).isNotNull();
        assertThat(stream.isBidirectional()).isTrue();
        assertThat(stream.state()).isEqualTo(QuicStreamState.OPEN);
    }

    @Test
    void testCreateUniStream() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));

        var stream = conn.createStream(false);

        assertThat(stream).isNotNull();
        assertThat(stream.isUnidirectional()).isTrue();
    }

    @Test
    void testCannotCreateStreamWhenNotConnected() {
        var conn = new QuicConnection(1L, false);

        assertThatThrownBy(() -> conn.createStream(true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testSendFrameWhenNotConnected() {
        var conn = new QuicConnection(1L, false);
        var frame = QuicFrame.connectionFrame(QuicFrameType.PING, null);

        assertThatThrownBy(() -> conn.sendFrame(frame))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testSendFrameWhenConnected() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));
        var frame = QuicFrame.connectionFrame(QuicFrameType.PING, null);

        // Should not throw
        conn.sendFrame(frame);
    }

    @Test
    void testConnectionMigration() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));
        var newAddress = new InetSocketAddress("192.168.1.1", 443);

        conn.migrate(newAddress);

        assertThat(conn.remoteAddress()).isEqualTo(newAddress);
    }

    @Test
    void testMigrationDisabled() {
        var settings = QuicSettings.builder()
                .disableActiveMigration(true)
                .build();
        var conn = new QuicConnection(1L, false, settings);
        conn.connect(new InetSocketAddress("localhost", 443));

        assertThatThrownBy(() -> conn.migrate(new InetSocketAddress("192.168.1.1", 443)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testMigrationWhenNotConnected() {
        var conn = new QuicConnection(1L, false);

        assertThatThrownBy(() -> conn.migrate(new InetSocketAddress("192.168.1.1", 443)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testDestinationConnectionId() {
        var conn = new QuicConnection(1L, false);
        conn.setDestinationConnectionId(42L);

        assertThat(conn.destinationConnectionId()).isEqualTo(42L);
    }

    @Test
    void testPeerSettings() {
        var conn = new QuicConnection(1L, false);
        var peerSettings = QuicSettings.builder()
                .maxIdleTimeout(120_000)
                .build();
        conn.setPeerSettings(peerSettings);

        assertThat(conn.peerSettings().maxIdleTimeout()).isEqualTo(120_000);
    }

    @Test
    void testNextPacketNumber() {
        var conn = new QuicConnection(1L, false);

        assertThat(conn.nextPacketNumber()).isEqualTo(0);
        assertThat(conn.nextPacketNumber()).isEqualTo(1);
        assertThat(conn.nextPacketNumber()).isEqualTo(2);
    }

    @Test
    void testFrameListener() {
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));

        var received = new java.util.ArrayList<QuicFrame>();
        conn.addFrameListener(received::add);

        var frame = QuicFrame.connectionFrame(QuicFrameType.PING, null);
        conn.sendFrame(frame);

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().type()).isEqualTo(QuicFrameType.PING);
    }

    @Test
    void testStreamManagerAccess() {
        var conn = new QuicConnection(1L, true);

        assertThat(conn.streamManager()).isNotNull();
        assertThat(conn.streamManager().isServer()).isTrue();
    }

    @Test
    void testFlowControlAccess() {
        var conn = new QuicConnection(1L, false);

        assertThat(conn.flowControl()).isNotNull();
    }

    // ==================== TLS 1.3 Handshake Tests ====================

    @Test
    void testHandshakePhaseInitialBeforeConnect() {
        // Given
        var conn = new QuicConnection(1L, false);

        // Then: handshake phase starts at INITIAL
        assertThat(conn.handshakePhase()).isEqualTo(QuicConnection.HandshakePhase.INITIAL);
    }

    @Test
    void testClientHandshakePhaseTransitions() {
        // Given
        var conn = new QuicConnection(1L, false);
        var address = new InetSocketAddress("localhost", 4433);

        // When
        conn.connect(address);

        // Then: handshake completed, phase is ESTABLISHED
        assertThat(conn.handshakePhase()).isEqualTo(QuicConnection.HandshakePhase.ESTABLISHED);
        assertThat(conn.isConnected()).isTrue();
    }

    @Test
    void testServerHandshakePhaseTransitions() {
        // Given
        var conn = new QuicConnection(2L, true);

        // When
        conn.accept();

        // Then: server handshake completed, phase is ESTABLISHED
        assertThat(conn.handshakePhase()).isEqualTo(QuicConnection.HandshakePhase.ESTABLISHED);
        assertThat(conn.isConnected()).isTrue();
    }

    @Test
    void testNegotiatedAlpnAfterClientConnect() {
        // Given
        var conn = new QuicConnection(1L, false);

        // When
        conn.connect(new InetSocketAddress("localhost", 443));

        // Then: ALPN should be "h3"
        assertThat(conn.negotiatedAlpn()).isEqualTo("h3");
    }

    @Test
    void testNegotiatedAlpnAfterServerAccept() {
        // Given
        var conn = new QuicConnection(2L, true);

        // When
        conn.accept();

        // Then
        assertThat(conn.negotiatedAlpn()).isEqualTo("h3");
    }

    @Test
    void testNegotiatedCipherSuiteAfterConnect() {
        // Given
        var conn = new QuicConnection(1L, false);

        // When
        conn.connect(new InetSocketAddress("localhost", 443));

        // Then: cipher suite should be a TLS 1.3 cipher
        assertThat(conn.negotiatedCipherSuite()).isNotNull();
        assertThat(conn.negotiatedCipherSuite()).contains("TLS_AES");
    }

    @Test
    void testNegotiatedProtocolIsTls13() {
        // Given
        var conn = new QuicConnection(1L, false);

        // When
        conn.connect(new InetSocketAddress("localhost", 443));

        // Then
        assertThat(conn.negotiatedProtocol()).isEqualTo("TLSv1.3");
    }

    @Test
    void testTlsEngineCreatedOnConnect() {
        // Given
        var conn = new QuicConnection(1L, false);

        // When
        conn.connect(new InetSocketAddress("localhost", 443));

        // Then: TLS engine is available
        assertThat(conn.tlsEngine()).isNotNull();
        assertThat(conn.tlsEngine().isServer()).isFalse();
    }

    @Test
    void testHandshakePhaseClosedAfterClose() {
        // Given
        var conn = new QuicConnection(1L, false);
        conn.connect(new InetSocketAddress("localhost", 443));
        assertThat(conn.handshakePhase()).isEqualTo(QuicConnection.HandshakePhase.ESTABLISHED);

        // When
        conn.close(QuicErrorCode.NO_ERROR, "test close");

        // Then
        assertThat(conn.handshakePhase()).isEqualTo(QuicConnection.HandshakePhase.CLOSED);
    }

    @Test
    void testHandshakePhaseEnumValues() {
        // Verify all handshake phase values exist
        assertThat(QuicConnection.HandshakePhase.values()).hasSize(4);
        assertThat(QuicConnection.HandshakePhase.INITIAL).isNotNull();
        assertThat(QuicConnection.HandshakePhase.HANDSHAKE).isNotNull();
        assertThat(QuicConnection.HandshakePhase.ESTABLISHED).isNotNull();
        assertThat(QuicConnection.HandshakePhase.CLOSED).isNotNull();
    }

    @Test
    void testServerWithoutSslContextUsesSimulatedHandshake() {
        // Given: server without explicit SSLContext
        var conn = new QuicConnection(3L, true);

        // When
        conn.accept();

        // Then: simulated handshake completes with expected params
        assertThat(conn.handshakePhase()).isEqualTo(QuicConnection.HandshakePhase.ESTABLISHED);
        assertThat(conn.negotiatedAlpn()).isEqualTo("h3");
        assertThat(conn.negotiatedCipherSuite()).isEqualTo("TLS_AES_128_GCM_SHA256");
        assertThat(conn.negotiatedProtocol()).isEqualTo("TLSv1.3");
    }

    @Test
    void testPeerCertificatesNullForSimulatedHandshake() {
        // Given
        var conn = new QuicConnection(1L, false);

        // When
        conn.connect(new InetSocketAddress("localhost", 443));

        // Then: no peer certs in simulated mode
        assertThat(conn.peerCertificates()).isNull();
    }

    @Test
    void testSetSslContext() {
        // Given
        var conn = new QuicConnection(1L, false);

        // When/Then: should accept SSLContext without error
        assertThatCode(() -> conn.setSslContext(null)).doesNotThrowAnyException();
    }
}
