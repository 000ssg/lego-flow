package ssg.legoflow.network.telnet.negotiation.demo;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;
import ssg.legoflow.network.telnet.negotiation.*;

/**
 * Demonstrates Telnet option negotiation.
 *
 * <p>Shows OptionNegotiator state machine, TTYPE handling,
 * and NAWS dimension reporting.
 *
 * @since 0.2.0
 */
public final class NegotiationDemo {

    private NegotiationDemo() {}

    public static void demonstrate() {
        // Option negotiator
        var negotiator = new OptionNegotiator();

        TelnetCommand response = negotiator.negotiate(
                TelnetCommand.WILL, TelnetOption.ECHO.code());
        System.out.println("Response to WILL ECHO: " + response);

        // TTYPE handler
        var ttype = TTYPEHandler.localType("xterm")
                .onRemoteType(type -> System.out.println("Remote type: " + type));

        // NAWS handler
        var naws = NAWSHandler.localSize(80, 24)
                .onRemoteSize((cols, rows) ->
                        System.out.println("Remote size: " + cols + "x" + rows));

        System.out.println("Negotiation demo complete");
    }
}
