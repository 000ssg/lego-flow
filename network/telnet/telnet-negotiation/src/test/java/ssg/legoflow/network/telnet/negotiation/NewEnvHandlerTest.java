package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NewEnvHandler} (RFC 1408).
 */
class NewEnvHandlerTest {

    private NewEnvHandler handler;

    @BeforeEach
    void setUp() {
        handler = NewEnvHandler.create("xterm", 80, 24);
    }

    @Test
    void testDefaultEnvironment() {
        assertThat(handler.get("TERM")).isEqualTo("xterm");
        assertThat(handler.get("COLS")).isEqualTo("80");
        assertThat(handler.get("LINES")).isEqualTo("24");
        assertThat(handler.get("UNKNOWN")).isNull();
    }

    @Test
    void testSetAndGet() {
        handler.set("HOME", "/home/user");
        assertThat(handler.get("HOME")).isEqualTo("/home/user");
    }

    @Test
    void testInfoRequest() {
        // Peer requests all environment info (NEW_ENV INFO with INFO_ALL = 0xFF)
        byte[] response = handler.handle(Arrays.asList(0, 0xFF));
        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte) 1); // NEW_ENV IS

        // The response should contain TERM, COLS, LINES variables
        String responseStr = new String(response);
        assertThat(responseStr).contains("TERM");
        assertThat(responseStr).contains("xterm");
        assertThat(responseStr).contains("COLS");
        assertThat(responseStr).contains("80");
    }

    @Test
    void testIsRequest() {
        // Peer sends environment variables (NEW_ENV IS with INFOMASK)
        // Format: IS INFOMASK nameLen name... valueLen value...
        // TERM=xterm from peer: nameLen=4, name="TERM", valueLen=5, value="xterm"
        List<Integer> data = new ArrayList<>();
        data.add(1);     // IS
        data.add(255);   // INFOMASK (0xFF as int)
        data.add(4);     // name length
        data.add((int) 'T'); data.add((int) 'E'); data.add((int) 'R'); data.add((int) 'M');
        data.add(5);     // value length
        data.add((int) 'x'); data.add((int) 't'); data.add((int) 'e'); data.add((int) 'r'); data.add((int) 'm');

        byte[] response = handler.handle(data);
        assertThat(response).isNull(); // IS doesn't require a response

        // The handler stores the peer's env vars
        assertThat(handler.get("TERM")).isEqualTo("xterm");
    }

    @Test
    void testNoProducts() {
        byte[] response = handler.handle(Arrays.asList(2)); // NEW_ENV NO-PRODUCTS
        assertThat(response).isNull();
    }

    @Test
    void testEmptyData() {
        byte[] response = handler.handle(List.of());
        assertThat(response).isNull();
    }

    @Test
    void testUnknownCommand() {
        byte[] response = handler.handle(Arrays.asList(99));
        assertThat(response).isNull();
    }

    @Test
    void testMultipleEnvVars() {
        handler.set("USER", "testuser");
        handler.set("SHELL", "/bin/bash");

        byte[] response = handler.handle(Arrays.asList(0, 0xFF));
        String responseStr = new String(response);
        assertThat(responseStr).contains("USER");
        assertThat(responseStr).contains("testuser");
        assertThat(responseStr).contains("SHELL");
        assertThat(responseStr).contains("/bin/bash");
    }
}
