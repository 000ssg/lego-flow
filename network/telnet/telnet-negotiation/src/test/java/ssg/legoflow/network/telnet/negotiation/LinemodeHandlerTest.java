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
        assertThat(handler.getSendMode()).isEqualTo(LinemodeHandler.SEND_NORMAL);
        assertThat(handler.getOutputMode()).isEqualTo(LinemodeHandler.OUTPUT_NORMAL);
    }

    @Test
    void testLinemodeSend() {
        // Peer requests our current mode (LINEMODE SEND = 1)
        byte[] response = handler.handle(List.of(1));
        assertThat(response).isNotNull();
        assertThat(response).hasSize(3);
        assertThat(response[0]).isEqualTo((byte) 0); // LINEMODE IS
        assertThat(response[1]).isEqualTo((byte) 0); // send mode = NORMAL
        assertThat(response[2]).isEqualTo((byte) 0); // output mode = NORMAL
    }

    @Test
    void testLinemodeIs() {
        // Peer sends their mode (LINEMODE IS = 0) with send-mode and output-mode
        byte[] response = handler.handle(List.of(0, 0, 0));
        assertThat(response).isNull(); // No response needed
        assertThat(handler.getSendMode()).isZero();
        assertThat(handler.getOutputMode()).isZero();
    }

    @Test
    void testLinemodeIsWithFlags() {
        // Peer sends mode with TRMAC flag
        byte[] response = handler.handle(List.of(0, LinemodeHandler.SEND_TRMAC, 0));
        assertThat(response).isNull();
        assertThat(handler.getSendMode()).isEqualTo(LinemodeHandler.SEND_TRMAC);
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

    @Test
    void testLineEditing() {
        handler.handle(List.of(2)); // START linemode

        // Feed printable characters
        assertThat(handler.processLineChar('h')).isTrue();
        assertThat(handler.processLineChar('e')).isTrue();
        assertThat(handler.processLineChar('l')).isTrue();
        assertThat(handler.processLineChar('l')).isTrue();
        assertThat(handler.processLineChar('o')).isTrue();
        assertThat(handler.getLineBuffer()).isEqualTo("hello");

        // Backspace
        assertThat(handler.processLineChar('\b')).isTrue();
        assertThat(handler.getLineBuffer()).isEqualTo("hell");

        // DEL (127) also deletes
        assertThat(handler.processLineChar((char) 127)).isTrue();
        assertThat(handler.getLineBuffer()).isEqualTo("hel");

        // CR submits the line
        String[] submitted = new String[1];
        handler.onLineSubmitted(line -> submitted[0] = line);
        assertThat(handler.processLineChar('\r')).isTrue();
        assertThat(submitted[0]).isEqualTo("hel");
        assertThat(handler.getLineBuffer()).isEmpty();
    }

    @Test
    void testControlCharsPassthrough() {
        handler.handle(List.of(2)); // START

        // Control characters below 32 are passed through (not consumed)
        assertThat(handler.processLineChar('\t')).isFalse();
        assertThat(handler.processLineChar('\n')).isFalse();
    }

    @Test
    void testBuildIsResponse() {
        handler.handle(List.of(0, LinemodeHandler.SEND_ECHO, LinemodeHandler.OUTPUT_SUPPRESS_LOG));
        byte[] is = handler.buildIsResponse();
        assertThat(is).hasSize(3);
        assertThat(is[0]).isEqualTo((byte) LinemodeHandler.IS);
        assertThat(is[1]).isEqualTo((byte) LinemodeHandler.SEND_ECHO);
        assertThat(is[2]).isEqualTo((byte) LinemodeHandler.OUTPUT_SUPPRESS_LOG);
    }

    @Test
    void testSlcCommand() {
        // SLC SET for EC (index 0), value 0x08 (BS)
        byte[] response = handler.handle(List.of(LinemodeHandler.SLC, LinemodeHandler.SLC_SET, 0, 0x08));
        assertThat(response).isNull();
        assertThat(handler.getSlicValue(0)).isEqualTo(0x08);
    }

    @Test
    void testSlcDefaults() {
        byte[] response = handler.handle(List.of(LinemodeHandler.SLC, LinemodeHandler.SLC_DEFAULTS, 0));
        assertThat(response).isNull();
        assertThat(handler.isSlicAbsent(0)).isTrue();
    }
}
