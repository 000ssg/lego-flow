package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LinemodeHandler} (RFC 1143).
 */
class LinemodeHandlerTest {

    private LinemodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = LinemodeHandler.create();
    }

    @Test
    void testInitialState() {
        assertThat(handler.isActive()).isFalse();
    }

    @Test
    void testLinemodeSend() {
        // Peer requests our current mode (LINEMODE SEND = 1)
        byte[] response = handler.handle(List.of(1));
        assertThat(response).isNotNull();
        assertThat(response).hasSize(2);
        assertThat(response[0]).isEqualTo((byte) 0); // LINEMODE IS
        assertThat(response[1]).isEqualTo((byte) 0); // default send mode
    }

    @Test
    void testLinemodeIs() {
        // Peer sends their mode (LINEMODE IS = 0)
        byte[] response = handler.handle(List.of(0, 0));
        assertThat(response).isNull(); // No response needed
    }

    @Test
    void testLinemodeStart() {
        byte[] response = handler.handle(List.of(2)); // LINEMODE START
        assertThat(response).isNull();
        assertThat(handler.isActive()).isTrue();
    }

    @Test
    void testLinemodeOff() {
        handler.handle(List.of(2)); // START first
        assertThat(handler.isActive()).isTrue();

        byte[] response = handler.handle(List.of(3)); // LINEMODE OFF
        assertThat(response).isNull();
        assertThat(handler.isActive()).isFalse();
    }

    @Test
    void testLinemodeDefault() {
        handler.handle(List.of(2)); // START first
        assertThat(handler.isActive()).isTrue();

        byte[] response = handler.handle(List.of(4)); // LINEMODE DEFAULT
        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte) 0); // LINEMODE IS
        assertThat(handler.isActive()).isFalse();
    }

    @Test
    void testEmptyData() {
        byte[] response = handler.handle(List.of());
        assertThat(response).isNull();
    }

    @Test
    void testUnknownCommand() {
        byte[] response = handler.handle(List.of(99));
        assertThat(response).isNull();
    }
}
