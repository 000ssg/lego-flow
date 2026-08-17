package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BinaryHandler} (RFC 856).
 */
class BinaryHandlerTest {

    private BinaryHandler handler;

    @BeforeEach
    void setUp() {
        handler = BinaryHandler.create();
    }

    @Test
    void testInitialStates() {
        assertThat(handler.isLocalBinary()).isFalse();
        assertThat(handler.isRemoteBinary()).isFalse();
        assertThat(handler.isNegotiated()).isFalse();
    }

    @Test
    void testSetLocalBinary() {
        handler.setLocalBinary(true);
        assertThat(handler.isLocalBinary()).isTrue();
        assertThat(handler.isRemoteBinary()).isFalse();
        assertThat(handler.isNegotiated()).isFalse();

        handler.setLocalBinary(false);
        assertThat(handler.isLocalBinary()).isFalse();
    }

    @Test
    void testSetRemoteBinary() {
        handler.setRemoteBinary(true);
        assertThat(handler.isRemoteBinary()).isTrue();
        assertThat(handler.isLocalBinary()).isFalse();
        assertThat(handler.isNegotiated()).isFalse();

        handler.setRemoteBinary(false);
        assertThat(handler.isRemoteBinary()).isFalse();
    }

    @Test
    void testNegotiatedWhenBothTrue() {
        handler.setLocalBinary(true);
        assertThat(handler.isNegotiated()).isFalse();

        handler.setRemoteBinary(true);
        assertThat(handler.isNegotiated()).isTrue();

        handler.setLocalBinary(false);
        assertThat(handler.isNegotiated()).isFalse();

        handler.setLocalBinary(true);
        assertThat(handler.isNegotiated()).isTrue();
    }
}
