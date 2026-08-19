package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NAWSHandlerTest {

    @Test
    void testHandleStandardSize() {
        List<Integer> data = List.of(0, 80, 0, 24);

        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(data);
        assertThat(received).hasSize(1);
        assertThat(received.get(0)).containsExactly(80, 24);
    }

    @Test
    void testHandleLargeSize() {
        List<Integer> data = List.of(7, 128, 4, 56);

        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(data);
        assertThat(received.get(0)).containsExactly(1920, 1080);
    }

    @Test
    void testHandleTooShortData() {
        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(List.of(0, 80));
        assertThat(received).isEmpty();
    }

    @Test
    void testHandleInvalidSize() {
        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(List.of(0, 0, 0, 0));
        assertThat(received).isEmpty();
    }

    @Test
    void testWindowSizeValidation() {
        assertThatThrownBy(() -> new NAWSHandler.WindowSize(0, 24))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NAWSHandler.WindowSize(80, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSendSize() throws InterruptedException {
        List<byte[]> sent = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        TelnetConnection conn = TelnetConnection.builder()
                .writer(bytes -> {
                    sent.add(bytes);
                    latch.countDown();
                })
                .build();

        NAWSHandler handler = NAWSHandler.localSize(130, 40);
        handler.sendSize(conn);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sent).hasSize(1);
        byte[] data = sent.get(0);
        assertThat(data[0]).isEqualTo((byte) 255);
        assertThat(data[1]).isEqualTo((byte) 250);
        assertThat(data[2] & 0xFF).isEqualTo(TelnetOption.NAWS.code());
        assertThat(data[3]).isEqualTo((byte) 0);
        assertThat(data[4] & 0xFF).isEqualTo(130);
        assertThat(data[5]).isEqualTo((byte) 0);
        assertThat(data[6] & 0xFF).isEqualTo(40);
        assertThat(data[7]).isEqualTo((byte) 255);
        assertThat(data[8]).isEqualTo((byte) 240);
    }

    @Test
    void testDynamicLocalSize() throws InterruptedException {
        int[] colsHolder = {120};
        int[] rowsHolder = {50};

        NAWSHandler handler = NAWSHandler.localSize(() ->
                new NAWSHandler.WindowSize(colsHolder[0], rowsHolder[0]))
                .onRemoteSize((c, r) -> {});

        handler.handle(List.of(0, 80, 0, 24));

        colsHolder[0] = 200;
        rowsHolder[0] = 60;

        List<byte[]> sent = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        TelnetConnection conn = TelnetConnection.builder()
                .writer(bytes -> {
                    sent.add(bytes);
                    latch.countDown();
                })
                .build();

        handler.sendSize(conn);
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

        byte[] data = sent.get(0);
        assertThat(data[3]).isEqualTo((byte) 0);
        assertThat(data[4] & 0xFF).isEqualTo(200);
        assertThat(data[5]).isEqualTo((byte) 0);
        assertThat(data[6] & 0xFF).isEqualTo(60);
    }

    @Test
    void testWindowSizeRecord() {
        NAWSHandler.WindowSize size = new NAWSHandler.WindowSize(80, 24);
        assertThat(size.cols()).isEqualTo(80);
        assertThat(size.rows()).isEqualTo(24);
    }
}
