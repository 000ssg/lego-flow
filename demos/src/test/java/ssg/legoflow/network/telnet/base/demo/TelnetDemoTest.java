package ssg.legoflow.network.telnet.base.demo;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelnetDemoTest {

    @Test
    void testDataReception() {
        List<byte[]> received = new ArrayList<>();

        var connection = TelnetConnection.builder()
                .writer(data -> {})
                .onData(received::add)
                .build();

        connection.feed("Hello".getBytes());
        connection.flush();

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo("Hello".getBytes());
    }

    @Test
    void testIACEscaping() {
        List<byte[]> sent = new ArrayList<>();

        var connection = TelnetConnection.builder()
                .writer(sent::add)
                .build();

        byte[] input = {(byte) 0xFF, 'H', 'i'};
        connection.send(input);

        assertThat(sent).hasSize(1);
        byte[] output = sent.get(0);
        assertThat(output).containsSequence(new byte[]{(byte) 0xFF, (byte) 0xFF});
    }

    @Test
    void testNegotiationParsing() {
        int[] receivedOption = {-1};

        var connection = TelnetConnection.builder()
                .writer(data -> {})
                .onNegotiate((cmd, opt) -> receivedOption[0] = opt)
                .build();

        byte[] data = {(byte) 0xFF, (byte) 251, (byte) TelnetOption.ECHO.code()};
        connection.feed(data);

        assertThat(receivedOption[0]).isEqualTo(TelnetOption.ECHO.code());
    }
}
