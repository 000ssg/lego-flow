package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TTYPEHandlerTest {

    @Test
    void testHandleSendRequest() {
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
    void testHandleIsRequest() {
        List<Integer> data = new ArrayList<>();
        data.add(TTYPEHandler.IS);
        data.add((int) 'v');
        data.add((int) 't');
        data.add((int) '1');
        data.add((int) '0');
        data.add((int) '0');
        data.add(0);

        List<String> received = new ArrayList<>();
        TTYPEHandler handler = TTYPEHandler.localType("xterm")
                .onRemoteType(received::add);

        byte[] response = handler.handle(data);
        assertThat(response).isNull();
        assertThat(received).containsExactly("vt100");
    }

    @Test
    void testHandleIsWithoutNullTerminator() {
        List<Integer> data = List.of(TTYPEHandler.IS, (int)'a', (int)'n', (int)'s', (int)'i');

        List<String> received = new ArrayList<>();
        TTYPEHandler handler = TTYPEHandler.localType("xterm")
                .onRemoteType(received::add);

        handler.handle(data);
        assertThat(received).containsExactly("ansi");
    }

    @Test
    void testHandleEmptyData() {
        TTYPEHandler handler = TTYPEHandler.localType("xterm");
        assertThat(handler.handle(List.of())).isNull();
    }

    @Test
    void testHandleUnknownSuboption() {
        TTYPEHandler handler = TTYPEHandler.localType("xterm");
        assertThat(handler.handle(List.of(99))).isNull();
    }

    @Test
    void testSendRequest() throws InterruptedException {
        List<byte[]> sent = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        TelnetConnection conn = TelnetConnection.builder()
                .writer(bytes -> {
                    sent.add(bytes);
                    latch.countDown();
                })
                .build();

        TTYPEHandler handler = TTYPEHandler.localType("vt100");
        handler.sendRequest(conn);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sent).hasSize(1);
        byte[] data = sent.get(0);
        assertThat(data).containsExactly(
                (byte) 255, (byte) 250,
                (byte) TelnetOption.TTYPE.code(),
                (byte) TTYPEHandler.SEND,
                (byte) 255, (byte) 240
        );
    }

    @Test
    void testSendType() throws InterruptedException {
        List<byte[]> sent = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        TelnetConnection conn = TelnetConnection.builder()
                .writer(bytes -> {
                    sent.add(bytes);
                    latch.countDown();
                })
                .build();

        TTYPEHandler handler = TTYPEHandler.localType("xterm-256color");
        handler.sendType(conn);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sent).hasSize(1);
        byte[] data = sent.get(0);
        // IAC SB 24 IS "xterm-256color" 0 IAC SE
        assertThat(data[0]).isEqualTo((byte) 255);
        assertThat(data[1]).isEqualTo((byte) 250);
        assertThat(data[2] & 0xFF).isEqualTo(TelnetOption.TTYPE.code());
        assertThat(data[3]).isEqualTo((byte) TTYPEHandler.IS);
        // type starts at data[4], ends before null at data[length-3]
        String type = new String(data, 4, data.length - 7);
        assertThat(type).isEqualTo("xterm-256color");
        assertThat(data[data.length - 3]).isEqualTo((byte) 0); // null
        assertThat(data[data.length - 2]).isEqualTo((byte) 255); // IAC
        assertThat(data[data.length - 1]).isEqualTo((byte) 240); // SE
    }

    @Test
    void testDynamicLocalType() {
        List<String> received = new ArrayList<>();
        TTYPEHandler handler = TTYPEHandler.localType(() -> "rxvt")
                .onRemoteType(received::add);

        byte[] resp = handler.handle(List.of(TTYPEHandler.SEND));
        assertThat(resp).isNotNull();
        String type = new String(resp, 1, resp.length - 2);
        assertThat(type).isEqualTo("rxvt");

        handler.handle(List.of(TTYPEHandler.IS, (int)'a', (int)'n', (int)'s', (int)'i', 0));
        assertThat(received).containsExactly("ansi");
    }
}
