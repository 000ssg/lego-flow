package ssg.legoflow.network.telnet.gateway.demo;

import ssg.legoflow.network.telnet.gateway.TelnetGateway;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayDemoTest {

    @Test
    void testGatewayWithVT100() {
        List<byte[]> sentData = new ArrayList<>();
        var terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        var gateway = TelnetGateway.forTerminal(terminal)
                .writer(sentData::add)
                .build();

        assertThat(gateway.terminal()).isNotNull();
        assertThat(gateway.isEchoEnabled()).isTrue();
    }

    @Test
    void testEchoEnabled() {
        List<byte[]> sentData = new ArrayList<>();
        var terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        var gateway = TelnetGateway.forTerminal(terminal)
                .writer(sentData::add)
                .build();

        gateway.feed("test".getBytes());

        // Echo should have sent data back
        assertThat(sentData).isNotEmpty();
    }

    @Test
    void testSendToPeer() {
        List<byte[]> sentData = new ArrayList<>();
        var terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        var gateway = TelnetGateway.forTerminal(terminal)
                .writer(sentData::add)
                .build();

        gateway.send("Hello");
        assertThat(sentData).hasSize(1);
    }

    @Test
    void testEchoDisabled() {
        List<byte[]> sentData = new ArrayList<>();
        var terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        var gateway = TelnetGateway.forTerminal(terminal)
                .writer(sentData::add)
                .build();

        gateway.setEchoEnabled(false);
        gateway.feed("test".getBytes());

        // No echo should be sent
        assertThat(sentData).isEmpty();
    }
}
