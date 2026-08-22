package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
class SpeedHandlerTest {

    @Test
    void testHandleSendRequest() {
        SpeedHandler handler = SpeedHandler.localSpeed("38400");
        byte[] response = handler.handle(List.of(SpeedHandler.SEND));

        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte) SpeedHandler.IS);
        assertThat(response).isEqualTo(new byte[]{
                (byte) SpeedHandler.IS,
                (byte)'3', (byte)'8', (byte)'4', (byte)'0', (byte)'0', 0
        });
    }

    @Test
    void testHandleIsRequest() {
        List<Integer> data = new ArrayList<>();
        data.add(SpeedHandler.IS);
        data.add((int)'9');
        data.add((int)'6');
        data.add((int)'0');
        data.add((int)'0');
        data.add(0);

        List<String> received = new ArrayList<>();
        SpeedHandler handler = SpeedHandler.localSpeed("38400")
                .onRemoteSpeed(received::add);

        byte[] response = handler.handle(data);
        assertThat(response).isNull();
        assertThat(received).containsExactly("9600");
    }

    @Test
    void testHandleEmptyData() {
        SpeedHandler handler = SpeedHandler.localSpeed("38400");
        assertThat(handler.handle(List.of())).isNull();
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

        SpeedHandler handler = SpeedHandler.localSpeed("9600");
        handler.sendRequest(conn);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sent).hasSize(1);
        byte[] data = sent.get(0);
        assertThat(data).containsExactly(
                (byte) 255, (byte) 250,
                (byte) TelnetOption.TERMINAL_SPEED.code(),
                (byte) SpeedHandler.SEND,
                (byte) 255, (byte) 240
        );
    }

    @Test
    void testSendSpeed() throws InterruptedException {
        List<byte[]> sent = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        TelnetConnection conn = TelnetConnection.builder()
                .writer(bytes -> {
                    sent.add(bytes);
                    latch.countDown();
                })
                .build();

        SpeedHandler handler = SpeedHandler.localSpeed("115200");
        handler.sendSpeed(conn);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sent).hasSize(1);
        byte[] data = sent.get(0);
        // IAC SB 42 IS '1' '1' '5' '2' '0' '0' 0 IAC SE = 13 bytes
        assertThat(data.length).isEqualTo(13);
        assertThat(data[0]).isEqualTo((byte) 255);
        assertThat(data[1]).isEqualTo((byte) 250);
        assertThat(data[2] & 0xFF).isEqualTo(TelnetOption.TERMINAL_SPEED.code());
        assertThat(data[3]).isEqualTo((byte) SpeedHandler.IS);
        // speed bytes: data[4..9], null at data[10], IAC at data[11], SE at data[12]
        String speed = new String(data, 4, data.length - 7);
        assertThat(speed).isEqualTo("115200");
        assertThat(data[data.length - 3]).isEqualTo((byte) 0); // null
        assertThat(data[data.length - 2]).isEqualTo((byte) 255); // IAC
        assertThat(data[data.length - 1]).isEqualTo((byte) 240); // SE
    }

    @Test
    void testHandleIsWithoutNullTerminator() {
        List<Integer> data = List.of(SpeedHandler.IS, (int)'4', (int)'8', (int)'0', (int)'0');

        List<String> received = new ArrayList<>();
        SpeedHandler handler = SpeedHandler.localSpeed("38400")
                .onRemoteSpeed(received::add);

        handler.handle(data);
        assertThat(received).containsExactly("4800");
    }

    @Test
    void testHandleUnknownSuboption() {
        SpeedHandler handler = SpeedHandler.localSpeed("38400");
        assertThat(handler.handle(List.of(99))).isNull();
    }
}
