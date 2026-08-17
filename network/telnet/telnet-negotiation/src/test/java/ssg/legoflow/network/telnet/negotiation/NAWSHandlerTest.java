package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NAWSHandlerTest {

    @Test
    void handleStandardSize() {
        List<Integer> data = List.of(0, 80, 0, 24);

        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(data);
        assertThat(received).hasSize(1);
        assertThat(received.get(0)).containsExactly(80, 24);
    }

    @Test
    void handleLargeSize() {
        List<Integer> data = List.of(7, 128, 4, 56);

        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(data);
        assertThat(received.get(0)).containsExactly(1920, 1080);
    }

    @Test
    void handleTooShortData() {
        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(List.of(0, 80));
        assertThat(received).isEmpty();
    }

    @Test
    void handleInvalidSize() {
        List<int[]> received = new ArrayList<>();
        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) -> received.add(new int[]{cols, rows}));

        handler.handle(List.of(0, 0, 0, 0));
        assertThat(received).isEmpty();
    }

    @Test
    void windowSizeValidation() {
        assertThatThrownBy(() -> new NAWSHandler.WindowSize(0, 24))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NAWSHandler.WindowSize(80, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
