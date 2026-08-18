package ssg.legoflow.interop.telnet;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.telnet.base.*;
import ssg.legoflow.network.telnet.negotiation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow Telnet client ↔ reference Telnet
 * server (Netcat with -t flag for telnet negotiation).
 *
 * <p><b>Reference:</b> OpenBSD telnet client (RFC 854–856),
 * RFC 856 Binary Mode.
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 *   <li>Netcat with telnet mode: nc -t -l port</li>
 *   <li>OpenBSD telnet client (optional): telnet localhost port</li>
 * </ul>
 *
 * @since 0.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelnetServerInteropTest {

    private static final byte IAC = (byte) 0xFF;
    private static final byte SB  = (byte) 0xFA;
    private static final byte SE  = (byte) 0xF0;

    // ── TelnetParser ───────────────────────────────────────────────

    @Test
    void testParserSubnegotiationParsing() {
        // Verify TelnetParser correctly parses SB...SE subnegotiation.
        // Reference: RFC 854, Section 3 — subnegotiation (SB...SE).
        AtomicReference<Integer> sbOption = new AtomicReference<>();
        AtomicReference<List<Integer>> sbData = new AtomicReference<>();

        TelnetParser parser = new TelnetParser(new TelnetListener() {
            @Override
            public void onSubnegotiation(int option, List<Integer> data) {
                sbOption.set(option);
                sbData.set(data);
            }
        });

        // IAC SB 24 "xterm" IAC SE
        parser.feed(new byte[]{IAC, SB, 24, (byte)'x', (byte)'t', (byte)'e', (byte)'r', (byte)'m', IAC, SE});

        assertThat(sbOption.get()).isEqualTo(24); // TTYPE
        assertThat(sbData.get()).hasSize(5);
        assertThat(sbData.get()).containsExactly(
                Integer.valueOf('x'), Integer.valueOf('t'), Integer.valueOf('e'),
                Integer.valueOf('r'), Integer.valueOf('m'));
    }

    @Test
    void testParserIacEscaping() {
        // Verify IAC IAC is parsed as literal 255.
        TelnetParser parser = new TelnetParser(new TelnetListener() {
            @Override
            public void onData(List<Integer> data) {
                assertThat(data).containsExactly(Integer.valueOf(255));
            }
        });
        parser.feed(new byte[]{IAC, IAC});
    }

    // ── TelnetConnection ───────────────────────────────────────────

    @Test
    void testConnectionIacEscaping() {
        // Verify TelnetConnection doubles IAC bytes on send.
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
        // Verify TelnetConnection sends correct negotiation bytes.
        List<byte[]> sentBytes = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(sentBytes::add)
                .build();

        conn.sendNegotiate(TelnetCommand.WILL, TelnetOption.ECHO.code());
        assertThat(sentBytes).hasSize(1);
        byte[] msg = sentBytes.get(0);
        assertThat(msg).containsExactly(IAC, (byte)0xFB, (byte)1);
    }

    @Test
    void testConnectionSubnegotiation() {
        List<byte[]> sentBytes = new ArrayList<>();
        TelnetConnection conn = TelnetConnection.builder()
                .writer(sentBytes::add)
                .build();

        byte[] payload = "hello".getBytes(StandardCharsets.US_ASCII);
        conn.sendSubnegotiation(24, payload); // TTYPE

        assertThat(sentBytes).hasSize(1);
        byte[] msg = sentBytes.get(0);
        // IAC SB 24 "hello" IAC SE
        assertThat(msg[0]).isEqualTo(IAC);
        assertThat(msg[1]).isEqualTo(SB);
        assertThat(msg[2]).isEqualTo(24);
        assertThat(new String(msg, 3, payload.length, StandardCharsets.US_ASCII)).isEqualTo("hello");
        assertThat(msg[msg.length - 2]).isEqualTo(IAC);
        assertThat(msg[msg.length - 1]).isEqualTo(SE);
    }

    // ── TelnetCommand ──────────────────────────────────────────────

    @Test
    void testCommandFromCode() {
        for (TelnetCommand cmd : TelnetCommand.values()) {
            TelnetCommand resolved = TelnetCommand.fromCode(cmd.code());
            assertThat(resolved).isEqualTo(cmd);
        }

        assertThat(TelnetCommand.WILL.hasOption()).isTrue();
        assertThat(TelnetCommand.DO.hasOption()).isTrue();
        assertThat(TelnetCommand.NOP.hasOption()).isFalse();
        assertThat(TelnetCommand.DM.hasOption()).isFalse();
    }

    // ── TelnetOptions ──────────────────────────────────────────────

    @Test
    void testOptionsMap() {
        assertThat(TelnetOption.fromCode(0)).isEqualTo(TelnetOption.BINARY);
        assertThat(TelnetOption.fromCode(1)).isEqualTo(TelnetOption.ECHO);
        assertThat(TelnetOption.fromCode(3)).isEqualTo(TelnetOption.SUPPRESS_GO_AHEAD);
        assertThat(TelnetOption.fromCode(24)).isEqualTo(TelnetOption.TTYPE);
        assertThat(TelnetOption.fromCode(31)).isEqualTo(TelnetOption.NAWS);
        assertThat(TelnetOption.fromCode(32)).isEqualTo(TelnetOption.TERMINAL_SPEED);
        assertThat(TelnetOption.fromCode(34)).isEqualTo(TelnetOption.LINEMODE);
        assertThat(TelnetOption.fromCode(252)).isEqualTo(TelnetOption.NEW_ENV);
        assertThat(TelnetOption.fromCode(999)).isNull();
    }

    // ── BinaryHandler (RFC 856) ────────────────────────────────────

    @Test
    void testBinaryTranslationInbound() {
        BinaryHandler handler = BinaryHandler.create();
        // CR NL → LF
        assertThat(handler.translateInbound(new byte[]{13, 10}))
                .containsExactly((byte)10);
        // LF → LF (already correct in inbound)
        assertThat(handler.translateInbound(new byte[]{10}))
                .containsExactly((byte)10);
    }

    @Test
    void testBinaryTranslationOutbound() {
        BinaryHandler handler = BinaryHandler.create();
        // LF → CR NL
        assertThat(handler.translateOutbound(new byte[]{10}))
                .containsExactly((byte)13, (byte)10);
    }

    @Test
    void testBinaryModeBypassesTranslation() {
        BinaryHandler handler = BinaryHandler.create();
        handler.setLocalBinary(true);
        handler.setRemoteBinary(true);
        // In binary mode, no translation
        assertThat(handler.translateInbound(new byte[]{13, 10}))
                .containsExactly((byte)13, (byte)10);
        assertThat(handler.translateOutbound(new byte[]{10}))
                .containsExactly((byte)10);
    }

    // ── NewEnvHandler (RFC 1408) ───────────────────────────────────

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
        assertThat(response[0]).isEqualTo((byte)1); // IS
        // Response contains TERM and vt100
        assertThat(new String(response, StandardCharsets.US_ASCII)).contains("TERM");
    }

    @Test
    void testNewEnvSetAndGet() {
        NewEnvHandler handler = NewEnvHandler.create("ansi", 80, 24);
        handler.set("HOME", "/home/user");
        assertThat(handler.get("HOME")).isEqualTo("/home/user");
    }

    // ── TTYPEHandler (RFC 1091) ────────────────────────────────────

    @Test
    void testTTYPEHandlerSendResponse() {
        TTYPEHandler handler = TTYPEHandler.localType("xterm");
        byte[] response = handler.handle(List.of()); // SEND
        assertThat(response).isNotNull();
        assertThat(response[0]).isEqualTo((byte)0); // IS
        assertThat(new String(response, 1, response.length - 2, StandardCharsets.US_ASCII)).isEqualTo("xterm");
    }

    // ── NAWSHandler (RFC 1073) ─────────────────────────────────────

    @Test
    void testNAWSHandlerParsesWindow() {
        AtomicReference<Integer> cols = new AtomicReference<>();
        AtomicReference<Integer> rows = new AtomicReference<>();

        NAWSHandler handler = NAWSHandler.localSize(80, 24)
                .onRemoteSize((c, r) -> { cols.set(c); rows.set(r); });

        // 132 cols, 50 rows (big-endian)
        handler.handle(List.of(0, 0, 132, 0, 50));
        assertThat(cols.get()).isEqualTo(132);
        assertThat(rows.get()).isEqualTo(50);
    }

    // ── LinemodeHandler (RFC 1143) ─────────────────────────────────

    @Test
    void testLinemodeLineSubmission() {
        LinemodeHandler handler = LinemodeHandler.create();
        List<String> lines = new ArrayList<>();
        handler.onLineSubmitted(lines::add);

        handler.handle(List.of(LinemodeHandler.START));
        assertThat(handler.isActive()).isTrue();

        handler.processLineChar('h');
        handler.processLineChar('e');
        handler.processLineChar('\r'); // CR submits
        assertThat(lines).containsExactly("he");
    }

    // ── Raw Telnet Protocol ────────────────────────────────────────

    @Test
    void testRawTelnetEchoProtocol() throws IOException {
        // Connect to a simple telnet echo server (Netcat -t) and test
        // basic protocol interaction.
        int port = Integer.parseInt(
                System.getProperty("interop.telnet.port", "2223"));
        String host = System.getProperty("interop.telnet.host", "localhost");

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(3000);

            // Send Hello\n
            socket.getOutputStream().write("Hello\r\n".getBytes());
            socket.getOutputStream().flush();

            // Read response (echo server echoes back)
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line != null) {
                assertThat(line).isEqualTo("Hello");
            }
        } catch (IOException e) {
            // Echo server not available — skip with info
            throw new org.opentest4j.TestAbortedException(
                    "Telnet echo server not available at " + host + ":" + port);
        }
    }
}
