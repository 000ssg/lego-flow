package ssg.legoflow.network.telnet.base;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelnetConnectionTest {

    @Test
    void testSendPlainText() {
        List<byte[]> written = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(written::add)
                .build();

        conn.send("hello");
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo("hello".getBytes());
    }

    @Test
    void testSendEscapesIac() {
        List<byte[]> written = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(written::add)
                .build();

        conn.send(new byte[]{0x61, (byte) 0xFF, 0x62});
        assertThat(written).hasSize(1);
        assertThat(written.get(0))
                .isEqualTo(new byte[]{0x61, (byte) 0xFF, (byte) 0xFF, 0x62});
    }

    @Test
    void testReceivePlainText() {
        List<byte[]> received = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .onData(received::add)
                .build();

        conn.feed("hello".getBytes());
        conn.flush();
        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo("hello".getBytes());
    }

    @Test
    void testReceiveEscapedIac() {
        List<byte[]> received = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .onData(received::add)
                .build();

        // Parser flushes on IAC boundary: 'a' triggers first onData,
        // then IAC IAC accumulates 255, then 'b' follows.
        conn.feed(new byte[]{'a', (byte) 0xFF, (byte) 0xFF, 'b'});
        conn.flush();

        // Two data events, but combined they form the correct payload
        assertThat(received).hasSize(2);
        assertThat(received.get(0)).isEqualTo(new byte[]{'a'});
        assertThat(received.get(1)).isEqualTo(new byte[]{(byte) 0xFF, 'b'});
    }

    @Test
    void testReceiveNegotiation() {
        List<TelnetCommand> cmds = new ArrayList<>();
        List<Integer> options = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .onNegotiate((cmd, opt) -> {
                    cmds.add(cmd);
                    options.add(opt);
                })
                .build();

        conn.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 1});
        assertThat(cmds).containsExactly(TelnetCommand.WILL);
        assertThat(options).containsExactly(1);
    }

    @Test
    void testReceiveCommand() {
        List<TelnetCommand> cmds = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .onCommand(cmds::add)
                .build();

        conn.feed(new byte[]{(byte) 0xFF, (byte) 0xF6});
        assertThat(cmds).containsExactly(TelnetCommand.AYT);
    }

    @Test
    void testReceiveSubnegotiation() {
        List<TelnetConnection.SubnegotiationEvent> events = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .onSubnegotiation(events::add)
                .build();

        conn.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 24, 'x', 't', 't', 'y', 0,
                (byte) 0xFF, (byte) 0xF0
        });
        assertThat(events).hasSize(1);
        assertThat(events.get(0).option()).isEqualTo(24);
        assertThat(events.get(0).data())
                .containsExactly((int)'x', (int)'t', (int)'t', (int)'y', 0);
    }

    @Test
    void testSendCommand() {
        List<byte[]> written = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(written::add)
                .build();

        conn.sendCommand(TelnetCommand.AYT);
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xF6});
    }

    @Test
    void testSendNegotiate() {
        List<byte[]> written = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(written::add)
                .build();

        conn.sendNegotiate(TelnetCommand.WILL, 1);
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xFB, 1});
    }

    @Test
    void testSendNegotiateRejectsSingleByteCommand() {
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .build();

        assertThatThrownBy(() -> conn.sendNegotiate(TelnetCommand.NOP, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSendCommandRejectsNegotiationCommand() {
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .build();

        assertThatThrownBy(() -> conn.sendCommand(TelnetCommand.WILL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSendSubnegotiation() {
        List<byte[]> written = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(written::add)
                .build();

        conn.sendSubnegotiation(24, "xterm\0".getBytes());
        assertThat(written).hasSize(1);
        byte[] msg = written.get(0);
        assertThat(msg[0]).isEqualTo((byte) 0xFF);
        assertThat(msg[1]).isEqualTo((byte) 0xFA);
        assertThat(msg[2]).isEqualTo((byte) 24);
        assertThat(msg[msg.length - 2]).isEqualTo((byte) 0xFF);
        assertThat(msg[msg.length - 1]).isEqualTo((byte) 0xF0);
    }

    @Test
    void testRoundTrip() {
        List<byte[]> serverToClient = new ArrayList<>();
        
        TelnetConnection client = TelnetConnection.builder()
                .writer(serverToClient::add)
                .build();

        // Client sends "hello" (no IAC bytes, so no escaping)
        client.send("hello");
        assertThat(serverToClient).hasSize(1);
        assertThat(serverToClient.get(0)).isEqualTo("hello".getBytes());
    }

    @Test
    void testOnDataCallbackWithLatch() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<byte[]> received = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(data -> {})
                .onData(data -> {
                    synchronized (received) { received.add(data); }
                    latch.countDown();
                })
                .build();

        conn.feed("test".getBytes());
        conn.flush();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        synchronized (received) {
            assertThat(received).hasSize(1);
        }
    }

    @Test
    void testEscapeIacUtility() {
        byte[] input = new byte[]{0x01, (byte) 0xFF, 0x02, (byte) 0xFF, 0x03};
        byte[] escaped = TelnetConnection.escapeIac(input);
        assertThat(escaped)
                .isEqualTo(new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, 0x02,
                        (byte) 0xFF, (byte) 0xFF, 0x03});
    }

    @Test
    void testEscapeIacNoIacPresent() {
        byte[] input = "hello".getBytes();
        byte[] escaped = TelnetConnection.escapeIac(input);
        assertThat(escaped).isEqualTo(input);
    }
}
