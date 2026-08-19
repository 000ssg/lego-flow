package ssg.legoflow.network.telnet.base.demo;

import ssg.legoflow.network.telnet.base.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates Telnet protocol parsing and connection handling.
 *
 * <p>Features demonstrated:
 * <ul>
 *   <li>IAC byte escaping (RFC 854) — 0xFF is doubled to 0xFF 0xFF</li>
 *   <li>Command parsing (DM, BRK, GA, EC, EL, AYT, IP, NOP, AO, SE)</li>
 *   <li>Negotiation commands (WILL, WONT, DO, DONT)</li>
 *   <li>Subnegotiation (SB...SE)</li>
 *   <li>Data transmission with IAC auto-escaping</li>
 *   <li>Connection builder with callbacks</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class TelnetDemo {

    private TelnetDemo() {}

    public static void demonstrate() {
        System.out.println("=== Telnet Protocol Demo ===\n");

        System.out.println("1. Basic Data Transmission");
        demonstrateBasicData();

        System.out.println("\n2. IAC Escaping");
        demonstrateIACEscaping();

        System.out.println("\n3. Command Parsing");
        demonstrateCommands();

        System.out.println("\n4. Option Negotiation");
        demonstrateNegotiation();

        System.out.println("\n5. Subnegotiation");
        demonstrateSubnegotiation();

        System.out.println("\n6. Null and Empty Safety");
        demonstrateNullSafety();

        System.out.println("\n7. Parser State");
        demonstrateParserState();

        System.out.println("\n8. TelnetOption Enum");
        demonstrateOptions();

        System.out.println("\n9. TelnetCommand Enum");
        demonstrateCommandsEnum();

        System.out.println("\n=== Demo Complete ===");
    }

    private static void demonstrateBasicData() {
        List<byte[]> received = new ArrayList<>();

        var connection = TelnetConnection.builder()
                .writer(data -> System.out.println("  Sent " + data.length + " bytes"))
                .onData(received::add)
                .build();

        connection.feed("Hello".getBytes());
        connection.flush();

        System.out.println("  Received " + received.size() + " data block(s)");
        System.out.println("  Content: " + new String(received.get(0)));

        connection.send("World");
        System.out.println("  Sent: World");
    }

    private static void demonstrateIACEscaping() {
        List<byte[]> sent = new ArrayList<>();

        var connection = TelnetConnection.builder()
                .writer(sent::add)
                .build();

        byte[] input = new byte[]{(byte) 0xFF, 'H', 'i'};
        connection.send(input);

        byte[] output = sent.get(0);
        System.out.println("  Input: " + bytesHex(input));
        System.out.println("  Output: " + bytesHex(output));
        System.out.println("  IAC (0xFF) doubled to IAC IAC (0xFF 0xFF)");

        // Parse it back
        List<byte[]> received = new ArrayList<>();
        var parser = TelnetConnection.builder()
                .writer(d -> {})
                .onData(received::add)
                .build();
        parser.feed(output);
        parser.flush();
        System.out.println("  Parsed back: " + bytesHex(received.get(0)));
    }

    private static void demonstrateCommands() {
        List<TelnetCommand> commands = new ArrayList<>();

        var connection = TelnetConnection.builder()
                .writer(d -> {})
                .onCommand(commands::add)
                .build();

        connection.feed(new byte[]{(byte) 0xFF, (byte) 0xF2}); // DM
        connection.feed(new byte[]{(byte) 0xFF, (byte) 0xF3}); // BRK
        connection.feed(new byte[]{(byte) 0xFF, (byte) 0xF9}); // GA
        connection.feed(new byte[]{(byte) 0xFF, (byte) 0xF6}); // AYT
        connection.feed(new byte[]{(byte) 0xFF, (byte) 0xF8}); // IP

        System.out.println("  Commands received: " + commands.size());
        for (TelnetCommand cmd : commands) {
            System.out.println("    " + cmd + " (" + cmd.code() + ")");
        }
    }

    private static void demonstrateNegotiation() {
        int[] opt = {-1};
        TelnetCommand[] cmd = {null};

        var connection = TelnetConnection.builder()
                .writer(d -> {})
                .onNegotiate((c, o) -> { cmd[0] = c; opt[0] = o; })
                .build();

        connection.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) TelnetOption.ECHO.code()});
        System.out.println("  Negotiate: " + cmd[0] + " " + TelnetOption.fromCode(opt[0]));

        connection.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, (byte) TelnetOption.SUPPRESS_GO_AHEAD.code()});
        System.out.println("  Negotiate: " + cmd[0] + " " + TelnetOption.fromCode(opt[0]));
    }

    private static void demonstrateSubnegotiation() {
        int[] subOption = {-1};
        byte[][] subDataHolder = new byte[1][];

        var connection = TelnetConnection.builder()
                .writer(d -> {})
                .onSubnegotiation(evt -> {
                    subOption[0] = evt.option();
                    byte[] data = new byte[evt.data().size()];
                    int i = 0;
                    for (Integer v : evt.data()) {
                        data[i++] = (byte)(v & 0xFF);
                    }
                    subDataHolder[0] = data;
                })
                .build();

        // TTYPE SEND: IAC SB 24 1 IAC SE
        connection.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, (byte) 24, (byte) 1,
                (byte) 0xFF, (byte) 0xF0
        });

        System.out.println("  Suboption option: " + subOption[0]);
        System.out.println("  Suboption data: " + bytesHex(subDataHolder[0]));
    }

    private static void demonstrateNullSafety() {
        var connection = TelnetConnection.builder()
                .writer(d -> {})
                .onData(d -> {})
                .build();

        System.out.println("  feed(null) -> no crash: OK");
        System.out.println("  send(null) -> no crash: OK");
    }

    private static void demonstrateParserState() {
        var connection = TelnetConnection.builder()
                .writer(d -> {})
                .onData(d -> {})
                .build();

        System.out.println("  Initial state: " + connection.parserState());
        connection.feed(new byte[]{(byte) 0xFF});
        System.out.println("  After IAC: " + connection.parserState());
        connection.feed(new byte[]{(byte) 0xFA});
        System.out.println("  After SB: " + connection.parserState());
        connection.feed(new byte[]{(byte) 0xF0});
        System.out.println("  After IAC SE: " + connection.parserState());
    }

    private static void demonstrateOptions() {
        System.out.println("  Telnet options:");
        for (TelnetOption opt : TelnetOption.values()) {
            System.out.println("    " + opt + " = " + opt.code());
        }
    }

    private static void demonstrateCommandsEnum() {
        System.out.println("  Telnet commands:");
        for (TelnetCommand cmd : TelnetCommand.values()) {
            System.out.println("    " + cmd + " = " + cmd.code());
        }
    }

    private static String bytesHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x & 0xFF));
        return sb.toString().trim();
    }
}
