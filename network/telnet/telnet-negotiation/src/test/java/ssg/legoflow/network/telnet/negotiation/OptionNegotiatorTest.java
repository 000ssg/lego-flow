package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OptionNegotiatorTest {

    private OptionNegotiator negotiator;

    @BeforeEach
    void setUp() {
        negotiator = new OptionNegotiator();
    }

    @Test
    void testWillIsAcceptedByDefault() {
        assertThat(negotiator.negotiate(TelnetCommand.WILL, 1))
                .isEqualTo(TelnetCommand.DO);
    }

    @Test
    void testWontIsRejectedByDefault() {
        assertThat(negotiator.negotiate(TelnetCommand.WONT, 1))
                .isEqualTo(TelnetCommand.DONT);
    }

    @Test
    void testDoTriggersWillByDefault() {
        assertThat(negotiator.negotiate(TelnetCommand.DO, 1))
                .isEqualTo(TelnetCommand.WILL);
    }

    @Test
    void testDontTriggersWontByDefault() {
        assertThat(negotiator.negotiate(TelnetCommand.DONT, 1))
                .isEqualTo(TelnetCommand.WONT);
    }

    @Test
    void testWillHandshake() {
        // Remote WILL → we DO; remote confirms with WILL again → we DO
        assertThat(negotiator.negotiate(TelnetCommand.WILL, 1)).isEqualTo(TelnetCommand.DO);
        assertThat(negotiator.negotiate(TelnetCommand.WILL, 1)).isEqualTo(TelnetCommand.DO);
        // Remote side confirmed ON (we sent DO twice)
        OptionRecord rec = negotiator.getOption(1);
        assertThat(rec.remoteState()).isEqualTo(OptionState.ON);
    }

    @Test
    void testDoHandshake() {
        // Remote DO → we WILL; remote confirms with DO again → we WILL
        assertThat(negotiator.negotiate(TelnetCommand.DO, 1)).isEqualTo(TelnetCommand.WILL);
        assertThat(negotiator.negotiate(TelnetCommand.DO, 1)).isEqualTo(TelnetCommand.WILL);
        // Local side confirmed ON (we sent WILL twice)
        OptionRecord rec = negotiator.getOption(1);
        assertThat(rec.localState()).isEqualTo(OptionState.ON);
    }

    @Test
    void testFullNegotiation() {
        // Both sides negotiate: remote WILL, we DO; remote confirms WILL
        // Then remote DO (asking us to enable), we WILL; remote confirms DO
        assertThat(negotiator.negotiate(TelnetCommand.WILL, 1)).isEqualTo(TelnetCommand.DO);
        assertThat(negotiator.negotiate(TelnetCommand.WILL, 1)).isEqualTo(TelnetCommand.DO);
        assertThat(negotiator.negotiate(TelnetCommand.DO, 1)).isEqualTo(TelnetCommand.WILL);
        assertThat(negotiator.negotiate(TelnetCommand.DO, 1)).isEqualTo(TelnetCommand.WILL);

        assertThat(negotiator.isOptionEnabled(1)).isTrue();
    }

    @Test
    void testWontAfterWillDisablesOption() {
        negotiator.negotiate(TelnetCommand.WILL, 1);
        assertThat(negotiator.negotiate(TelnetCommand.WONT, 1))
                .isEqualTo(TelnetCommand.DONT);
        assertThat(negotiator.isOptionEnabled(1)).isFalse();
    }

    @Test
    void testCustomRejectPolicy() {
        negotiator = new OptionNegotiator() {
            @Override
            public TelnetCommand shouldAcceptRemote(int option) {
                return option == 1 ? TelnetCommand.DONT : TelnetCommand.DO;
            }
        };

        assertThat(negotiator.negotiate(TelnetCommand.WILL, 1))
                .isEqualTo(TelnetCommand.DONT);
        assertThat(negotiator.negotiate(TelnetCommand.WILL, 31))
                .isEqualTo(TelnetCommand.DO);
    }

    @Test
    void testCustomLocalPolicy() {
        negotiator = new OptionNegotiator() {
            @Override
            public TelnetCommand shouldEnableLocal(int option) {
                return option == 1 ? TelnetCommand.WONT : TelnetCommand.WILL;
            }
        };

        assertThat(negotiator.negotiate(TelnetCommand.DO, 1))
                .isEqualTo(TelnetCommand.WONT);
        assertThat(negotiator.negotiate(TelnetCommand.DO, 31))
                .isEqualTo(TelnetCommand.WILL);
    }

    @Test
    void testOptionRecordInitialState() {
        OptionRecord rec = new OptionRecord(1);
        assertThat(rec.localState()).isEqualTo(OptionState.OFF);
        assertThat(rec.remoteState()).isEqualTo(OptionState.OFF);
        assertThat(rec.isEnabled()).isFalse();
    }
}
