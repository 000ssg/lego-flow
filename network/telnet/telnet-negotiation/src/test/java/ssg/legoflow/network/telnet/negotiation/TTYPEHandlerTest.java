package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TTYPEHandlerTest {

    @Test
    void handleSendRequest() {
        TTYPEHandler handler = TTYPEHandler.localType("xterm");
        byte[] response = handler.handle(List.of(TTYPEHandler.SEND));

        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte) TTYPEHandler.IS);
        assertThat(response[1]).isEqualTo((byte) 'x');
        assertThat(response[2]).isEqualTo((byte) 't');
        assertThat(response[3]).isEqualTo((byte) 'e');
        assertThat(response[4]).isEqualTo((byte) 'r');
        assertThat(response[5]).isEqualTo((byte) 'm');
        assertThat(response[6]).isEqualTo((byte) 0);
    }

    @Test
    void handleIsRequest() {
        List<Integer> data = new ArrayList<>();
        data.add(TTYPEHandler.IS);
        data.add((int) 'v');
        data.add((int) 't');
        data.add((int) '1');
        data.add((int) '0');
        data.add((int) '0');
        data.add(0); // null terminator

        List<String> received = new ArrayList<>();
        TTYPEHandler handler = TTYPEHandler.localType("xterm")
                .onRemoteType(received::add);

        byte[] response = handler.handle(data);
        assertThat(response).isNull(); // No response to IS
        assertThat(received).containsExactly("vt100");
    }

    @Test
    void handleIsWithoutNullTerminator() {
        List<Integer> data = List.of(TTYPEHandler.IS, (int)'a', (int)'n', (int)'s', (int)'i');

        List<String> received = new ArrayList<>();
        TTYPEHandler handler = TTYPEHandler.localType("xterm")
                .onRemoteType(received::add);

        handler.handle(data);
        assertThat(received).containsExactly("ansi");
    }

    @Test
    void handleEmptyData() {
        TTYPEHandler handler = TTYPEHandler.localType("xterm");
        assertThat(handler.handle(List.of())).isNull();
    }

    @Test
    void handleUnknownSuboption() {
        TTYPEHandler handler = TTYPEHandler.localType("xterm");
        assertThat(handler.handle(List.of(99))).isNull();
    }
}
