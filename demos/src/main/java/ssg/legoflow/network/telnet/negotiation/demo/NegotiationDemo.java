package ssg.legoflow.network.telnet.negotiation.demo;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;
import ssg.legoflow.network.telnet.negotiation.*;

import java.util.List;

/**
 * Demonstrates all Telnet option negotiation handlers.
 *
 * <p>Covers:
 * <ul>
 *   <li>OptionNegotiator state machine (WILL/WONT/DO/DONT transitions)</li>
 *   <li>GatewayNegotiator (server-side with known options whitelist)</li>
 *   <li>TTYPEHandler — terminal type exchange (IS/SEND)</li>
 *   <li>NAWSHandler — window size negotiation</li>
 *   <li>SpeedHandler — terminal speed negotiation</li>
 *   <li>BinaryHandler — binary mode negotiation</li>
 *   <li>LinemodeHandler — linemode state machine</li>
 *   <li>NewEnvHandler — environment variable exchange (RFC 1408)</li>
 *   <li>OptionRecord — option state tracking</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class NegotiationDemo {

    private NegotiationDemo() {}

    public static void demonstrate() {
        System.out.println("=== Telnet Negotiation Demo ===\n");

        System.out.println("1. OptionNegotiator Basics");
        demonstrateOptionNegotiator();

        System.out.println("\n2. GatewayNegotiator (Whitelist)");
        demonstrateGatewayNegotiator();

        System.out.println("\n3. TTYPE Handler");
        demonstrateTTYPEHandler();

        System.out.println("\n4. NAWS Handler");
        demonstrateNAWSHandler();

        System.out.println("\n5. Speed Handler");
        demonstrateSpeedHandler();

        System.out.println("\n6. Binary Handler");
        demonstrateBinaryHandler();

        System.out.println("\n7. Linemode Handler");
        demonstrateLinemodeHandler();

        System.out.println("\n8. NewEnv Handler");
        demonstrateNewEnvHandler();

        System.out.println("\n9. OptionRecord State");
        demonstrateOptionRecord();

        System.out.println("\n10. Full Negotiation Flow");
        demonstrateFullFlow();

        System.out.println("\n=== Demo Complete ===");
    }

    private static void demonstrateOptionNegotiator() {
        var negotiator = new OptionNegotiator();
        System.out.println("  Default rules:");
        System.out.println("    WILL ECHO → " + negotiator.negotiate(TelnetCommand.WILL, TelnetOption.ECHO.code()));
        System.out.println("    DO ECHO → " + negotiator.negotiate(TelnetCommand.DO, TelnetOption.ECHO.code()));
        System.out.println("    WONT ECHO → " + negotiator.negotiate(TelnetCommand.WONT, TelnetOption.ECHO.code()));
        System.out.println("    DONT ECHO → " + negotiator.negotiate(TelnetCommand.DONT, TelnetOption.ECHO.code()));
        System.out.println("    WILL unknown → " + negotiator.negotiate(TelnetCommand.WILL, 99));
    }

    private static void demonstrateGatewayNegotiator() {
        var gateway = new GatewayNegotiator();
        System.out.println("  Gateway (known options):");
        System.out.println("    WILL TTYPE → " + gateway.negotiate(TelnetCommand.WILL, TelnetOption.TTYPE.code()));
        System.out.println("    WILL NAWS → " + gateway.negotiate(TelnetCommand.WILL, TelnetOption.NAWS.code()));
        System.out.println("    DO BINARY → " + gateway.negotiate(TelnetCommand.DO, TelnetOption.BINARY.code()));
        System.out.println("    WILL unknown → " + gateway.negotiate(TelnetCommand.WILL, 99));
        System.out.println("    DO unknown → " + gateway.negotiate(TelnetCommand.DO, 99));
    }

    private static void demonstrateTTYPEHandler() {
        var ttype = TTYPEHandler.localType("vt100")
                .onRemoteType(type -> System.out.println("    Peer type: " + type));

        System.out.println("  TTYPE IS (responding with type):");
        byte[] response = ttype.handle(List.of(Integer.valueOf(TTYPEHandler.IS)));
        System.out.println("    IS response length: " + (response != null ? response.length : "null"));

        System.out.println("  TTYPE SEND (peer sent their type):");
        response = ttype.handle(List.of(
            Integer.valueOf(TTYPEHandler.IS),
            Integer.valueOf('v'), Integer.valueOf('t'),
            Integer.valueOf('1'), Integer.valueOf('0'), Integer.valueOf('0')
        ));
        System.out.println("    No response to IS (null)");
    }

    private static void demonstrateNAWSHandler() {
        int[] cols = {0}, rows = {0};
        var naws = NAWSHandler.localSize(80, 24)
                .onRemoteSize((c, r) -> { cols[0] = c; rows[0] = r; });

        naws.handle(List.of(0, 120, 0, 40));
        System.out.println("  After 120x40: " + cols[0] + "x" + rows[0]);
        naws.handle(List.of(0, 200, 0, 60));
        System.out.println("  After 200x60: " + cols[0] + "x" + rows[0]);
        naws.handle(List.of());
        System.out.println("  Empty: " + cols[0] + "x" + rows[0]);
    }

    private static void demonstrateSpeedHandler() {
        var speed = SpeedHandler.localSpeed("38400");
        byte[] response = speed.handle(List.of(0, 0, 0, 9, 6, 0));
        System.out.println("  IS response: " + (response != null ? response.length + " bytes" : "null"));
    }

    private static void demonstrateBinaryHandler() {
        var binary = BinaryHandler.create();
        System.out.println("  Initial: local=" + binary.isLocalBinary() + " remote=" + binary.isRemoteBinary());
        binary.setLocalBinary(true);
        System.out.println("  After setLocalBinary(true): local=" + binary.isLocalBinary());
        binary.setRemoteBinary(true);
        System.out.println("  After setRemoteBinary(true): remote=" + binary.isRemoteBinary());
        binary.setLocalBinary(false);
        binary.setRemoteBinary(false);
        System.out.println("  After reset: local=" + binary.isLocalBinary() + " remote=" + binary.isRemoteBinary());
    }

    private static void demonstrateLinemodeHandler() {
        var linemode = LinemodeHandler.create()
                .onLineSubmitted(line -> System.out.println("    Line: " + line));
        System.out.println("  Initial: active=" + linemode.isActive());

        byte[] startResp = linemode.handle(List.of(
                Integer.valueOf(LinemodeHandler.START), 0xFF, 0xF0));
        System.out.println("  After START: active=" + linemode.isActive());

        linemode.processLineChar('h');
        linemode.processLineChar('i');
        linemode.processLineChar('\r');
        System.out.println("  Line buffer: " + linemode.getLineBuffer());

        byte[] offResp = linemode.handle(List.of(
                Integer.valueOf(LinemodeHandler.OFF), 0xFF, 0xF0));
        System.out.println("  After OFF: active=" + linemode.isActive());
    }

    private static void demonstrateNewEnvHandler() {
        var newEnv = NewEnvHandler.create("vt100", 80, 24)
                .onRemoteVar((name, variable) ->
                        System.out.println("    Remote: " + name + " = " + variable));
        byte[] response = newEnv.handle(List.of(
                Integer.valueOf(0),    // IS
                Integer.valueOf(4),    // name length
                Integer.valueOf('T'), Integer.valueOf('E'),
                Integer.valueOf('R'), Integer.valueOf('M'),
                Integer.valueOf(5),    // value length
                Integer.valueOf('v'), Integer.valueOf('t'),
                Integer.valueOf('1'), Integer.valueOf('0'),
                Integer.valueOf('0')
        ));
        System.out.println("  NewEnv IS response: " + (response != null ? response.length + " bytes" : "null"));
    }

    private static void demonstrateOptionRecord() {
        var negotiator = new OptionNegotiator();
        var rec = new OptionRecord(TelnetOption.ECHO.code());
        System.out.println("  New: local=" + rec.localState() + " remote=" + rec.remoteState());
        rec.onWill(negotiator);
        System.out.println("  After WILL: local=" + rec.localState() + " remote=" + rec.remoteState());
        rec.onDo(negotiator);
        System.out.println("  After DO: local=" + rec.localState() + " remote=" + rec.remoteState());
        rec.onWont(negotiator);
        System.out.println("  After WONT: local=" + rec.localState() + " remote=" + rec.remoteState());
        rec.onDont(negotiator);
        System.out.println("  After DONT: local=" + rec.localState() + " remote=" + rec.remoteState());
    }

    private static void demonstrateFullFlow() {
        var negotiator = new GatewayNegotiator();
        System.out.println("  Dial-up sequence:");
        for (int opt : new int[]{1, 3, 24, 31}) {
            TelnetCommand resp = negotiator.negotiate(TelnetCommand.WILL, opt);
            System.out.println("    Peer WILL " + TelnetOption.fromCode(opt) + " → " + resp);
        }
        System.out.println("  Negotiation complete");
    }
}
