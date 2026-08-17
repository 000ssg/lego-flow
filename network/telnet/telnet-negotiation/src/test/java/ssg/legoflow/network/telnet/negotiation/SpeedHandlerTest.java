package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeedHandlerTest {

    @Test
    void testHandleSendRequest() {
        SpeedHandler handler = SpeedHandler.localSpeed("38400");
        byte[] response = handler.handle(List.of(SpeedHandler.SEND));

        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte) SpeedHandler.IS);
        // "38400\0"
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
}
