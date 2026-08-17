package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayNegotiatorTest {

    @Test
    void testDefaultNegotiatorAcceptsStandardOptions() {
        GatewayNegotiator negotiator = new GatewayNegotiator();

        assertThat(negotiator.shouldAcceptRemote(TelnetOption.ECHO.code()))
                .as("should accept ECHO from remote")
                .isEqualTo(TelnetCommand.DO);
        assertThat(negotiator.shouldEnableLocal(TelnetOption.ECHO.code()))
                .as("should enable ECHO locally")
                .isEqualTo(TelnetCommand.WILL);
        assertThat(negotiator.shouldKeepLocalEnabled(TelnetOption.ECHO.code()))
                .as("should keep ECHO enabled")
                .isEqualTo(TelnetCommand.WILL);
    }

    @Test
    void testDefaultNegotiatorRejectsUnknownOptions() {
        GatewayNegotiator negotiator = new GatewayNegotiator();

        int unknownOption = 200; // Not in KNOWN_OPTIONS
        assertThat(negotiator.shouldAcceptRemote(unknownOption))
                .as("should reject unknown option from remote")
                .isEqualTo(TelnetCommand.DONT);
        assertThat(negotiator.shouldEnableLocal(unknownOption))
                .as("should not enable unknown option locally")
                .isEqualTo(TelnetCommand.WONT);
        assertThat(negotiator.shouldKeepLocalEnabled(unknownOption))
                .as("should disable unknown option")
                .isEqualTo(TelnetCommand.WONT);
    }

    @Test
    void testCustomAcceptedOptions() {
        Set<Integer> accepted = Set.of(TelnetOption.ECHO.code(), TelnetOption.TTYPE.code());
        GatewayNegotiator negotiator = new GatewayNegotiator(accepted);

        assertThat(negotiator.shouldAcceptRemote(TelnetOption.ECHO.code()))
                .isEqualTo(TelnetCommand.DO);
        assertThat(negotiator.shouldAcceptRemote(TelnetOption.TTYPE.code()))
                .isEqualTo(TelnetCommand.DO);
        assertThat(negotiator.shouldAcceptRemote(TelnetOption.NAWS.code()))
                .as("NAWS not in custom set")
                .isEqualTo(TelnetCommand.DONT);
    }

    @Test
    void testAllKnownOptions() {
        GatewayNegotiator negotiator = new GatewayNegotiator();

        for (TelnetOption opt : new TelnetOption[]{
                TelnetOption.BINARY, TelnetOption.ECHO, TelnetOption.SUPPRESS_GO_AHEAD,
                TelnetOption.TTYPE, TelnetOption.NAWS, TelnetOption.LINEMODE,
                TelnetOption.NEW_ENV, TelnetOption.TERMINAL_SPEED
        }) {
            assertThat(negotiator.shouldAcceptRemote(opt.code()))
                    .as("should accept " + opt.name())
                    .isEqualTo(TelnetCommand.DO);
            assertThat(negotiator.shouldEnableLocal(opt.code()))
                    .as("should enable " + opt.name())
                    .isEqualTo(TelnetCommand.WILL);
            assertThat(negotiator.shouldKeepLocalEnabled(opt.code()))
                    .as("should keep " + opt.name())
                    .isEqualTo(TelnetCommand.WILL);
        }
    }
}
