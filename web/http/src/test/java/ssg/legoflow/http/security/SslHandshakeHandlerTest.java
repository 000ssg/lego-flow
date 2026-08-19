package ssg.legoflow.http.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SslHandshakeHandlerTest {

    private SslConfig config;
    private SslHandshakeHandler handler;

    @BeforeEach
    void setUp() {
        config = new SslConfig();
        config.setKeystorePath("/test/keystore.jks");
        handler = new SslHandshakeHandler(config);
    }

    @Test
    void testInitialStateIsNotStarted() {
        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.NOT_STARTED);
    }

    @Test
    void testBeginHandshakeTransitionsToInProgress() {
        handler.beginHandshake();

        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.IN_PROGRESS);
    }

    @Test
    void testCompleteHandshakeTransitionsToCompleted() {
        handler.beginHandshake();
        handler.completeHandshake();

        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.COMPLETED);
    }

    @Test
    void testFailHandshakeTransitionsToFailed() {
        handler.beginHandshake();
        handler.failHandshake();

        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.FAILED);
    }

    @Test
    void testFullHandshakeLifecycle() {
        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.NOT_STARTED);
        handler.beginHandshake();
        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.IN_PROGRESS);
        handler.completeHandshake();
        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.COMPLETED);
    }

    @Test
    void testConfigIsPreserved() {
        assertThat(handler.getConfig()).isSameAs(config);
        assertThat(handler.getConfig().getKeystorePath()).isEqualTo("/test/keystore.jks");
    }

    @Test
    void testFailedHandshakeLifecycle() {
        handler.beginHandshake();
        handler.failHandshake();

        assertThat(handler.getState()).isEqualTo(SslHandshakeHandler.HandshakeState.FAILED);
    }
}
