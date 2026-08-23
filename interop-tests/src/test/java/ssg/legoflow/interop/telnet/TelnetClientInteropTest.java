package ssg.legoflow.interop.telnet;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.telnet.gateway.TelnetClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow Telnet client ↔ reference Telnet server (wistic/telnetd).
 *
 * @since 0.2.0
 */
    @Tag("terminal-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelnetClientInteropTest {

    private static final int TELNET_PORT = Integer.parseInt(
            System.getProperty("interop.telnet.port", "2223"));
    private static final String TELNET_HOST = System.getProperty("interop.telnet.host", "localhost");

    private TelnetClient client;

    @BeforeEach
    void setUp() throws IOException {
        client = TelnetClient.builder()
                .connect(TELNET_HOST, TELNET_PORT)
                .connectTimeout(5000)
                .build();
        client.start();
    }

    @AfterEach
    void tearDown() {
        if (client != null && client.isConnected()) {
            client.close();
        }
    }

    @Test
    void testBasicConnectionToTelnetd() throws IOException {
        assertThat(client).isNotNull();
        assertThat(client.isConnected()).isTrue();
        assertThat(client.socket()).isNotNull();
        assertThat(client.socket().isConnected()).isTrue();
    }

    @Test
    void testSendAndReceiveWithTelnetd() throws IOException, InterruptedException {
        AtomicReference<byte[]> received = new AtomicReference<>();
        client.onData(data -> { received.set(data); });

        Thread.sleep(500);
        client.send("HELLO TELNET\r\n");
        Thread.sleep(1000);
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testSendBinaryDataToTelnetd() throws IOException, InterruptedException {
        AtomicReference<byte[]> received = new AtomicReference<>();
        client.onData(data -> { received.set(data); });

        byte[] binaryData = new byte[]{(byte)0xFF, (byte)'A', (byte)'B', (byte)'C'};
        client.send(binaryData);
        Thread.sleep(1000);
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testTelnetOptionNegotiation() throws IOException, InterruptedException {
        assertThat(client.negotiator()).isNotNull();
        client.send("STATUS\r\n");
        Thread.sleep(500);
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testMultipleSequentialMessages() throws IOException, InterruptedException {
        List<byte[]> allReceived = new ArrayList<>();
        client.onData(data -> allReceived.add(data));

        String[] messages = {"MSG1\r\n", "MSG2\r\n", "MSG3\r\n"};
        for (String msg : messages) {
            client.send(msg);
            Thread.sleep(300);
        }
        Thread.sleep(1000);
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testClientResilientToIdle() throws IOException, InterruptedException {
        client.send("PING\r\n");
        Thread.sleep(2000);
        assertThat(client.isConnected()).isTrue();
        client.send("PONG\r\n");
        Thread.sleep(500);
        assertThat(client.isConnected()).isTrue();
    }
}
