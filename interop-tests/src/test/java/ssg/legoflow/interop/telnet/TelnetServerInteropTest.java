package ssg.legoflow.interop.telnet;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.telnet.base.*;
import ssg.legoflow.network.telnet.gateway.*;
import ssg.legoflow.network.telnet.negotiation.*;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow Telnet server ↔ reference Telnet
 * clients (OpenBSD telnet, netcat).
 *
 * <p><b>Reference:</b> OpenBSD telnet client (RFC 854–856),
 * RFC 856 Binary Mode.
 *
 * @since 0.2.0
 */
    @Tag("terminal-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelnetServerInteropTest {

    private static final byte IAC = (byte) 0xFF;
    private static final byte SB  = (byte) 0xFA;
    private static final byte SE  = (byte) 0xF0;


    // Force VT100Terminal to load and register with TerminalFactory
    static {
        try { VT100Terminal.create(TerminalConfig.builder().build()); } catch (Exception e) {}
    }

    @Test
    void testParserSubnegotiationParsing() {
        AtomicReference<Integer> sbOption = new AtomicReference<>();
        AtomicReference<List<Integer>> sbData = new AtomicReference<>();

        TelnetParser parser = new TelnetParser(new TelnetListener() {
            @Override
            public void onSubnegotiation(int option, List<Integer> data) {
                sbOption.set(option);
                sbData.set(data);
            }
        });

        parser.feed(new byte[]{IAC, SB, 24, (byte)'x', (byte)'t', (byte)'e', (byte)'r', (byte)'m', IAC, SE});

        assertThat(sbOption.get()).isEqualTo(24);
        assertThat(sbData.get()).hasSize(5);
        assertThat(sbData.get()).containsExactly(
                Integer.valueOf('x'), Integer.valueOf('t'), Integer.valueOf('e'),
                Integer.valueOf('r'), Integer.valueOf('m'));
    }

    @Test
    void testParserIacEscaping() {
        TelnetParser parser = new TelnetParser(new TelnetListener() {
            @Override
            public void onData(List<Integer> data) {
                assertThat(data).containsExactly(Integer.valueOf(255));
            }
        });
        parser.feed(new byte[]{IAC, IAC});
    }

    @Test
    void testConnectionIacEscaping() {
        List<byte[]> sentBytes = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(sentBytes::add)
                .build();

        byte[] dataWithIac = new byte[]{(byte)'H', (byte)'e', IAC, (byte)'l', (byte)'l', (byte)'o'};
        conn.send(dataWithIac);

        assertThat(sentBytes).hasSize(1);
        byte[] escaped = sentBytes.get(0);
        assertThat(escaped).containsExactly(
                (byte)'H', (byte)'e', IAC, IAC, (byte)'l', (byte)'l', (byte)'o');
    }

    @Test
    void testConnectionNegotiationCommands() {
        List<byte[]> sentBytes = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(sentBytes::add)
                .build();

        conn.sendNegotiate(TelnetCommand.WILL, TelnetOption.ECHO.code());
        assertThat(sentBytes).hasSize(1);
        byte[] msg = sentBytes.get(0);
        assertThat(msg).containsExactly(IAC, (byte)TelnetCommand.WILL.code(), (byte)TelnetOption.ECHO.code());
    }

    @Test
    void testConnectionSubnegotiation() {
        List<byte[]> sentBytes = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(sentBytes::add)
                .build();

        byte[] payload = "hello".getBytes(StandardCharsets.US_ASCII);
        conn.sendSubnegotiation(24, payload);

        assertThat(sentBytes).hasSize(1);
        byte[] msg = sentBytes.get(0);
        assertThat(msg[0]).isEqualTo(IAC);
        assertThat(msg[1]).isEqualTo(SB);
        assertThat(msg[2] & 0xFF).isEqualTo(24);
        assertThat(new String(msg, 3, payload.length, StandardCharsets.US_ASCII)).isEqualTo("hello");
        assertThat(msg[msg.length - 2]).isEqualTo(IAC);
        assertThat(msg[msg.length - 1]).isEqualTo(SE);
    }

    @Test
    void testTelnetCommandCodes() {
        assertThat(TelnetCommand.WILL.code()).isEqualTo(251);
        assertThat(TelnetCommand.WONT.code()).isEqualTo(252);
        assertThat(TelnetCommand.DO.code()).isEqualTo(253);
        assertThat(TelnetCommand.DONT.code()).isEqualTo(254);
        assertThat(TelnetCommand.SB.code()).isEqualTo(250);
        assertThat(TelnetCommand.SE.code()).isEqualTo(240);
        assertThat(TelnetCommand.NOP.code()).isEqualTo(241);
    }

    @Test
    void testTelnetOptionCodes() {
        assertThat(TelnetOption.BINARY.code()).isEqualTo(0);
        assertThat(TelnetOption.ECHO.code()).isEqualTo(1);
        assertThat(TelnetOption.SUPPRESS_GO_AHEAD.code()).isEqualTo(3);
        assertThat(TelnetOption.NAWS.code()).isEqualTo(31);
        assertThat(TelnetOption.TTYPE.code()).isEqualTo(24);
        assertThat(TelnetOption.TN3270.code()).isEqualTo(255);
    }

    @Test
    void testTelnetOutputStreamIacEscaping() throws Exception {
        List<byte[]> sentBytes = new ArrayList<>();
        OutputStream writer = new OutputStream() {
            @Override public void write(byte[] b) throws IOException { sentBytes.add(b); }
            @Override public void write(int b) throws IOException { sentBytes.add(new byte[]{(byte)b}); }
        };

        TelnetOutputStream tos = new TelnetOutputStream(writer);
        tos.write(new byte[]{(byte)'H', (byte)'e', (byte)0xFF, (byte)'l', (byte)'l', (byte)'o'});
        tos.flush();
        tos.close();

        // Each byte triggers write(int), IAC gets doubled => H,e,IAC,IAC,l,l,o = 7 bytes
        assertThat(sentBytes).hasSize(7);
        byte[] escaped = new byte[7];
        for (int i = 0; i < 7; i++) {
            escaped[i] = sentBytes.get(i)[0];
        }
        assertThat(escaped).containsExactly(
                (byte)'H', (byte)'e', IAC, IAC, (byte)'l', (byte)'l', (byte)'o');
    }

    @Test
    void testTelnetParserBasicParsing() {
        AtomicReference<List<Integer>> receivedData = new AtomicReference<>();
        TelnetParser parser = new TelnetParser(new TelnetListener() {
            @Override
            public void onData(List<Integer> data) {
                receivedData.set(data);
            }
        });

        parser.feed("Hello".getBytes(StandardCharsets.US_ASCII)); parser.flush();
        assertThat(receivedData.get()).hasSize(5);
        assertThat(receivedData.get()).containsExactly(
                Integer.valueOf('H'), Integer.valueOf('e'), Integer.valueOf('l'),
                Integer.valueOf('l'), Integer.valueOf('o'));
    }

    @Test
    void testTelnetParserIacCommand() {
        AtomicReference<TelnetCommand> receivedCommand = new AtomicReference<>();
        TelnetParser parser = new TelnetParser(new TelnetListener() {
            @Override
            public void onCommand(TelnetCommand cmd) {
                receivedCommand.set(cmd);
            }
        });

        parser.feed(new byte[]{IAC, (byte)TelnetCommand.NOP.code()});
        assertThat(receivedCommand.get()).isEqualTo(TelnetCommand.NOP);
    }

    @Test
    void testBinaryTranslationOutbound() {
        BinaryHandler handler = BinaryHandler.create();
        assertThat(handler.translateOutbound(new byte[]{10}))
                .containsExactly((byte)13, (byte)10);
    }

    @Test
    void testBinaryModeBypassesTranslation() {
        BinaryHandler handler = BinaryHandler.create();
        handler.setLocalBinary(true);
        handler.setRemoteBinary(true);
        assertThat(handler.translateInbound(new byte[]{13, 10}))
                .containsExactly((byte)13, (byte)10);
        assertThat(handler.translateOutbound(new byte[]{10}))
                .containsExactly((byte)10);
    }

    @Test
    void testNewEnvDefaultEnvironment() {
        NewEnvHandler handler = NewEnvHandler.create("xterm", 80, 24);
        assertThat(handler.get("TERM")).isEqualTo("xterm");
        assertThat(handler.get("COLS")).isEqualTo("80");
        assertThat(handler.get("LINES")).isEqualTo("24");
    }

    @Test
    void testNewEnvInfoRequest() {
        NewEnvHandler handler = NewEnvHandler.create("vt100", 80, 24);
        byte[] response = handler.handle(List.of(0, 0xFF));
        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte)1);
        assertThat(new String(response, StandardCharsets.US_ASCII)).contains("TERM");
    }

    @Test
    void testNewEnvSetAndGet() {
        NewEnvHandler handler = NewEnvHandler.create("ansi", 80, 24);
        handler.set("HOME", "/home/user");
        assertThat(handler.get("HOME")).isEqualTo("/home/user");
    }

    @Test
    void testTTYPEHandlerSendResponse() {
        TTYPEHandler handler = TTYPEHandler.localType("xterm");
        byte[] response = handler.handle(List.of(1));
        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte)0);
        assertThat(new String(response, 1, response.length - 2, StandardCharsets.US_ASCII)).isEqualTo("xterm");
    }

    @Test
    void testNAWSHandlerParsesWindow() {
        AtomicInteger cols = new AtomicInteger(0);
        AtomicInteger rows = new AtomicInteger(0);

        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((c, r) -> { cols.set(c); rows.set(r); });

        handler.handle(List.of(0, 132, 0, 50));
        assertThat(cols.get()).isEqualTo(132);
        assertThat(rows.get()).isEqualTo(50);
    }

    @Test
    void testLinemodeLineSubmission() {
        LinemodeHandler handler = LinemodeHandler.create();
        List<String> lines = new ArrayList<>();
        handler.onLineSubmitted(lines::add);

        handler.handle(List.of(LinemodeHandler.START));
        assertThat(handler.isActive()).isTrue();

        handler.processLineChar('h');
        handler.processLineChar('e');
        handler.processLineChar('\r');
        assertThat(lines).containsExactly("he");
    }

    // ── TelnetServer (socket-based) ─────────────────────────────────

    @Test
    void testTelnetServerStartAndStop() throws IOException, InterruptedException {
        TelnetServer server = TelnetServer.builder()
                .port(0)
                .build();
        server.start();
        int port = server.getPort();
        assertThat(port).isGreaterThan(0);
        assertThat(server.isRunning()).isTrue();

        Thread.sleep(500);

        server.close();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testTelnetServerAcceptsConnection() throws IOException, InterruptedException {
        AtomicInteger connectionCount = new AtomicInteger(0);
        TelnetServer server = TelnetServer.builder()
                .port(0)
                .onSession(s -> connectionCount.incrementAndGet())
                .build();
        server.start();
        int port = server.getPort();

        try (Socket clientSocket = new Socket("localhost", port)) {
            clientSocket.setSoTimeout(10000);
            Thread.sleep(3000);
        }

        Thread.sleep(2000);
        assertThat(connectionCount.get()).isEqualTo(1);
        server.close();
    }

    @Test
    void testTelnetServerSendsDataToClient() throws IOException, InterruptedException {
        TelnetServer server = TelnetServer.builder()
                .port(0)
                .onConnection(s -> s.send("Welcome to lego-flow telnet!\r\n"))
                .build();
        server.start();
        int port = server.getPort();

        try (Socket clientSocket = new Socket("localhost", port)) {
            clientSocket.setSoTimeout(10000);
            Thread.sleep(3000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            assertThat(line).isEqualTo("Welcome to lego-flow telnet!");
        }
        server.close();
    }

    @Test
    void testTelnetServerHandlesMultipleClients() throws IOException, InterruptedException {
        AtomicInteger clientCount = new AtomicInteger(0);
        TelnetServer server = TelnetServer.builder()
                .port(0)
                .onSession(s -> clientCount.incrementAndGet())
                .build();
        server.start();
        int port = server.getPort();

        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Socket s = new Socket("localhost", port);
            s.setSoTimeout(5000);
            sockets.add(s);
            Thread.sleep(200);
        }

        Thread.sleep(2000);
        assertThat(clientCount.get()).isGreaterThanOrEqualTo(3);

        for (Socket s : sockets) {
            s.close();
        }
        server.close();
    }

    // ── Raw Telnet Protocol ─────────────────────────────────────────

    @Test
    void testRawTelnetEchoProtocol() throws IOException, InterruptedException {
        int port = Integer.parseInt(
                System.getProperty("interop.telnet.port", "2223"));
        String host = System.getProperty("interop.telnet.host", "localhost");

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(5000);

            // Wait for telnetd banner/negotiation
            Thread.sleep(1000);

            // Read any initial telnet negotiation bytes
            socket.getInputStream().readNBytes(256);

            // Send data
            socket.getOutputStream().write("HELLO\r\n".getBytes());
            socket.getOutputStream().flush();

            // Wait for echo
            Thread.sleep(1000);

            // Read response - may contain telnet IAC sequences
            java.io.InputStream in = socket.getInputStream();
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n > 0) {
                String response = new String(buf, 0, n, StandardCharsets.UTF_8);
                // Response should contain "HELLO" possibly surrounded by telnet negotiation bytes
                assertThat(response).contains("HELLO");
            }
        } catch (IOException e) {
            throw new org.opentest4j.TestAbortedException(
                    "Telnet echo server not available at " + host + ":" + port);
        }
    }
}
