package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelnetGatewayTest {

    private Terminal terminal;
    private List<byte[]> written;

    @BeforeEach
    void setUp() {
        TerminalConfig config = TerminalConfig.builder()
                .rows(24).cols(80).build();
        terminal = VT100Terminal.create(config);
        written = new ArrayList<>();
    }

    @Test
    void feedPlainTextToTerminal() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Feed plain text "Hi" to the gateway
        gateway.feed("Hi".getBytes());

        // Echo should be sent back (default: echo on)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo("Hi".getBytes());

        // Terminal should have the text
        List<String> lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hi");
    }

    @Test
    void echoCanBeDisabled() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.setEchoEnabled(false);
        gateway.feed("test".getBytes());

        // No echo
        assertThat(written).isEmpty();
    }

    @Test
    void negotiationResponse() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC WILL ECHO (255 251 1)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 1});

        // Gateway responds with IAC DO ECHO (255 253 1)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xFD, 1});
    }

    @Test
    void dontEchoDisablesEcho() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC DONT ECHO
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFE, 1});

        // Gateway responds with IAC WONT ECHO
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xFC, 1});

        // Echo is now disabled
        assertThat(gateway.isEchoEnabled()).isFalse();

        // Feed data — no echo
        written.clear();
        gateway.feed("no echo".getBytes());
        assertThat(written).isEmpty();
    }

    @Test
    void ttypeSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC SB 24 1 IAC SE (TTYPE SEND)
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 24, 1,
                (byte) 0xFF, (byte) 0xF0
        });

        // Gateway responds with IAC SB 24 0 "vt100\0" IAC SE (TTYPE IS)
        assertThat(written).hasSize(1);
        byte[] response = written.get(0);
        assertThat(response[0]).isEqualTo((byte) 0xFF); // IAC
        assertThat(response[1]).isEqualTo((byte) 0xFA); // SB
        assertThat(response[2]).isEqualTo((byte) 24);   // TTYPE
        assertThat(response[3]).isEqualTo((byte) 0);    // IS
        assertThat(response[4]).isEqualTo((byte) 'v');
        assertThat(response[response.length - 2]).isEqualTo((byte) 0xFF); // IAC
        assertThat(response[response.length - 1]).isEqualTo((byte) 0xF0); // SE
    }

    @Test
    void nawsSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends NAWS: 132 cols, 43 rows
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, (byte) 31, (byte) 0, (byte) 132, (byte) 0, (byte) 43,
                (byte) 0xFF, (byte) 0xF0
        });

        // No response expected for NAWS
        assertThat(written).isEmpty();
    }

    @Test
    void sendToPeer() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.send("hello");
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo("hello".getBytes());
    }

    @Test
    void sendEscapesIac() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.send(new byte[]{0x01, (byte) 0xFF, 0x02});
        assertThat(written).hasSize(1);
        assertThat(written.get(0))
                .isEqualTo(new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, 0x02});
    }

    @Test
    void terminalAccess() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.terminal()).isSameAs(terminal);
        assertThat(gateway.terminal().type()).isEqualTo("vt100");
    }
}
