package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;

import java.util.Set;

/**
 * Option negotiator for Telnet gateway (server-side).
 *
 * <p>Rejects unknown options with DONT/WONT, and only accepts known standard options
 * that the gateway can handle. This prevents protocol desync from peers advertising
 * unsupported or experimental options.
 *
 * <p>Known accepted options: BINARY, ECHO, SUPPRESS_GO_AHEAD, TTYPE, NAWS,
 * LINEMODE, NEW_ENV, TERMINAL_SPEED.
 *
 * @since 0.2.0
 */
public class GatewayNegotiator extends OptionNegotiator {

    private static final Set<Integer> KNOWN_OPTIONS = Set.of(
            TelnetOption.BINARY.code(),
            TelnetOption.ECHO.code(),
            TelnetOption.SUPPRESS_GO_AHEAD.code(),
            TelnetOption.TTYPE.code(),
            TelnetOption.NAWS.code(),
            TelnetOption.LINEMODE.code(),
            TelnetOption.NEW_ENV.code(),
            TelnetOption.TERMINAL_SPEED.code()
    );

    private final Set<Integer> acceptedOptions;

    /**
     * Create a gateway negotiator accepting all standard options.
     */
    public GatewayNegotiator() {
        this.acceptedOptions = Set.copyOf(KNOWN_OPTIONS);
    }

    /**
     * Create a gateway negotiator with a custom set of accepted options.
     *
     * @param accepted option codes this gateway will negotiate
     */
    public GatewayNegotiator(Set<Integer> accepted) {
        this.acceptedOptions = Set.copyOf(accepted);
    }

    @Override
    public TelnetCommand shouldAcceptRemote(int option) {
        return acceptedOptions.contains(option) ? TelnetCommand.DO : TelnetCommand.DONT;
    }

    @Override
    public TelnetCommand shouldEnableLocal(int option) {
        return acceptedOptions.contains(option) ? TelnetCommand.WILL : TelnetCommand.WONT;
    }

    @Override
    public TelnetCommand shouldKeepLocalEnabled(int option) {
        // For accepted options, keep enabled (WILL); otherwise disable (WONT)
        return acceptedOptions.contains(option) ? TelnetCommand.WILL : TelnetCommand.WONT;
    }
}
