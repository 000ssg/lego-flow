package ssg.legoflow.network.telnet.negotiation.demo;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;
import ssg.legoflow.network.telnet.negotiation.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationDemoTest {

    @Test
    void testDefaultNegotiator() {
        var negotiator = new OptionNegotiator();

        TelnetCommand response = negotiator.negotiate(
                TelnetCommand.WILL, TelnetOption.ECHO.code());
        assertThat(response).isEqualTo(TelnetCommand.DO);
    }

    @Test
    void testTTYPEHandler() {
        var receivedType = new AtomicInteger(-1);
        var handler = TTYPEHandler.localType("xterm")
                .onRemoteType(type -> receivedType.set(type.length()));

        byte[] response = handler.handle(List.of(1)); // SEND = 1
        assertThat(response).isNotNull();
    }

    @Test
    void testNAWSHandler() {
        int[] receivedSize = {-1, -1};
        var handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> {
                    receivedSize[0] = cols;
                    receivedSize[1] = rows;
                });

        handler.handle(List.of(0, 120, 0, 40));
        assertThat(receivedSize[0]).isEqualTo(120);
        assertThat(receivedSize[1]).isEqualTo(40);
    }
}
