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
    void testPutTyped() {
        handler.put("DEBUG", "1", NewEnvHandler.TYPE_BOOL);
        NewEnvHandler.EnvVar var = handler.getEnvironment().get("DEBUG");
        assertThat(var).isNotNull();
        assertThat(var.value()).isEqualTo("1");
        assertThat(var.type()).isEqualTo(NewEnvHandler.TYPE_BOOL);
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
    void testInfoRequestWithFilter() {
        // Peer requests only TERM variable
        List<Integer> data = new ArrayList<>();
        data.add(0);   // INFO
        data.add(4);   // name length = 4
        data.add((int) 'T'); data.add((int) 'E'); data.add((int) 'R'); data.add((int) 'M');
        data.add(0xFF); // infomask = all

        byte[] response = handler.handle(data);
        assertThat(response).isNotNull();
        String responseStr = new String(response);
        assertThat(responseStr).contains("TERM");
        assertThat(responseStr).contains("xterm");
        // Should NOT contain COLS or LINES (filtered)
        assertThat(responseStr).doesNotContain("COLS");
    }

    @Test
    void testIsRequest() {
        // Peer sends environment variables (NEW_ENV IS)
        // Format: IS <nameLen> <name> <valueLen> <value> [<type>]...
        List<Integer> data = new ArrayList<>();
        data.add(1);     // IS
        data.add(4);     // name length = 4
        data.add((int) 'T'); data.add((int) 'E'); data.add((int) 'R'); data.add((int) 'M');
        data.add(5);     // value length = 5
        data.add((int) 'x'); data.add((int) 't'); data.add((int) 'e'); data.add((int) 'r'); data.add((int) 'm');
        data.add(0);     // type = STRING

        byte[] response = handler.handle(data);
        assertThat(response).isNull(); // IS doesn't require a response

        // The handler stores the peer's env vars in remote environment
        NewEnvHandler.EnvVar remote = handler.getRemoteEnvironment().get("TERM");
        assertThat(remote).isNotNull();
        assertThat(remote.value()).isEqualTo("xterm");
        assertThat(remote.type()).isEqualTo(NewEnvHandler.TYPE_STRING);
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

    @Test
    void testRemoteEnvCallback() {
        String[] received = new String[1];
        handler.onRemoteVar((name, variable) -> received[0] = name);

        List<Integer> data = new ArrayList<>();
        data.add(1);     // IS
        data.add(4);     // name length
        data.add((int) 'H'); data.add((int) 'O'); data.add((int) 'M'); data.add((int) 'E');
        data.add(10);    // value length
        data.add((int) '/'); data.add((int) 'h'); data.add((int) 'o'); data.add((int) 'm'); data.add((int) 'e');
        data.add((int) '/'); data.add((int) 'u'); data.add((int) 's'); data.add((int) 'e'); data.add((int) 'r');

        handler.handle(data);
        assertThat(received[0]).isEqualTo("HOME");
    }

    @Test
    void testInfomaskTypeFiltering() {
        handler.put("DEBUG", "1", NewEnvHandler.TYPE_BOOL);

        // Request with INFO_TYPE mask (0x01) — should include type byte
        List<Integer> data = new ArrayList<>();
        data.add(0);   // INFO
        data.add(0x01); // infomask = TYPE only

        byte[] response = handler.handle(data);
        assertThat(response).isNotNull();
        String responseStr = new String(response);
        assertThat(responseStr).contains("TERM");
    }

    @Test
    void testRemoteEnvWithoutType() {
        // IS without explicit type byte — defaults to STRING
        List<Integer> data = new ArrayList<>();
        data.add(1);     // IS
        data.add(4);     // name length
        data.add((int) 'X'); data.add((int) 'Y'); data.add((int) 'Z'); data.add((int) 'Z');
        data.add(3);     // value length
        data.add((int) '4'); data.add((int) '2'); data.add((int) '0');
        // No type byte

        handler.handle(data);
        NewEnvHandler.EnvVar var = handler.getRemoteEnvironment().get("XYZZ");
        assertThat(var).isNotNull();
        assertThat(var.type()).isEqualTo(NewEnvHandler.TYPE_STRING);
    }
}
