package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;

import static org.assertj.core.api.Assertions.assertThat;

class OptionRecordTest {

    private static final OptionNegotiator ACCEPT_ALL = new OptionNegotiator();

    @Test
    void testInitialState() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code());
        assertThat(rec.localState()).isEqualTo(OptionState.OFF);
        assertThat(rec.remoteState()).isEqualTo(OptionState.OFF);
        assertThat(rec.isEnabled()).isFalse();
    }

    @Test
    void testInitialStateWithBothOn() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code(), OptionState.ON, OptionState.ON);
        assertThat(rec.isEnabled()).isTrue();
    }

    @Test
    void testOnWillOffToOnDef() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code());
        TelnetCommand resp = rec.onWill(ACCEPT_ALL);
        assertThat(resp).isEqualTo(TelnetCommand.DO);
        assertThat(rec.remoteState()).isEqualTo(OptionState.ON_DEF);
        assertThat(rec.isEnabled()).isFalse(); // local still OFF
    }

    @Test
    void testOnWillOnDefToOn() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code(), OptionState.OFF, OptionState.ON_DEF);
        TelnetCommand resp = rec.onWill(ACCEPT_ALL);
        assertThat(resp).isEqualTo(TelnetCommand.DO);
        assertThat(rec.remoteState()).isEqualTo(OptionState.ON);
    }

    @Test
    void testOnWillOnToOffDef() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code(), OptionState.ON, OptionState.ON);
        // Simulate a DONT response to force OFF_DEF
        OptionNegotiator reject = new OptionNegotiator() {
            @Override
            public TelnetCommand shouldAcceptRemote(int option) {
                return TelnetCommand.DONT;
            }
        };
        TelnetCommand resp = rec.onWill(reject);
        assertThat(resp).isEqualTo(TelnetCommand.DONT);
        assertThat(rec.remoteState()).isEqualTo(OptionState.OFF_DEF);
    }

    @Test
    void testOnWontOnToOff() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code(), OptionState.ON, OptionState.ON);
        TelnetCommand resp = rec.onWont(ACCEPT_ALL);
        assertThat(resp).isEqualTo(TelnetCommand.DONT);
        assertThat(rec.remoteState()).isEqualTo(OptionState.OFF);
    }

    @Test
    void testOnWontOffDefToOff() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code(), OptionState.OFF, OptionState.OFF_DEF);
        TelnetCommand resp = rec.onWont(ACCEPT_ALL);
        assertThat(resp).isEqualTo(TelnetCommand.DONT);
        assertThat(rec.remoteState()).isEqualTo(OptionState.OFF);
    }

    @Test
    void testOnDoOffToOnDef() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code());
        TelnetCommand resp = rec.onDo(ACCEPT_ALL);
        assertThat(resp).isEqualTo(TelnetCommand.WILL);
        assertThat(rec.localState()).isEqualTo(OptionState.ON_DEF);
    }

    @Test
    void testOnDoOnDefToOn() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code(), OptionState.ON_DEF, OptionState.OFF);
        TelnetCommand resp = rec.onDo(ACCEPT_ALL);
        assertThat(resp).isEqualTo(TelnetCommand.WILL);
        assertThat(rec.localState()).isEqualTo(OptionState.ON);
    }

    @Test
    void testOnDontOnToOff() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code(), OptionState.ON, OptionState.OFF);
        OptionNegotiator keepDisabled = new OptionNegotiator() {
            @Override
            public TelnetCommand shouldKeepLocalEnabled(int option) {
                return TelnetCommand.WONT;
            }
        };
        TelnetCommand resp = rec.onDont(keepDisabled);
        assertThat(resp).isEqualTo(TelnetCommand.WONT);
        assertThat(rec.localState()).isEqualTo(OptionState.OFF);
    }

    @Test
    void testOnDontOffToOff() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code());
        OptionNegotiator keepDisabled = new OptionNegotiator() {
            @Override
            public TelnetCommand shouldKeepLocalEnabled(int option) {
                return TelnetCommand.WONT;
            }
        };
        TelnetCommand resp = rec.onDont(keepDisabled);
        assertThat(resp).isEqualTo(TelnetCommand.WONT);
        assertThat(rec.localState()).isEqualTo(OptionState.OFF);
    }

    @Test
    void testToString() {
        OptionRecord rec = new OptionRecord(1, OptionState.ON, OptionState.ON_DEF);
        String str = rec.toString();
        assertThat(str).contains("1");
        assertThat(str).contains("ON");
        assertThat(str).contains("ON_DEF");
    }

    @Test
    void testFullHandshakeBothSides() {
        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code());

        // Phase 1: Remote sends WILL, we respond DO, remote stays ON_DEF
        assertThat(rec.onWill(ACCEPT_ALL)).isEqualTo(TelnetCommand.DO);
        assertThat(rec.remoteState()).isEqualTo(OptionState.ON_DEF);

        // Phase 2: Remote sends another WILL (confirmation), remote goes ON
        assertThat(rec.onWill(ACCEPT_ALL)).isEqualTo(TelnetCommand.DO);
        assertThat(rec.remoteState()).isEqualTo(OptionState.ON);

        // Phase 3: Remote sends DO (asking us to enable), we respond WILL, local goes ON_DEF
        assertThat(rec.onDo(ACCEPT_ALL)).isEqualTo(TelnetCommand.WILL);
        assertThat(rec.localState()).isEqualTo(OptionState.ON_DEF);

        // Phase 4: Remote sends another DO (confirmation), local goes ON
        assertThat(rec.onDo(ACCEPT_ALL)).isEqualTo(TelnetCommand.WILL);
        assertThat(rec.localState()).isEqualTo(OptionState.ON);

        // Now both sides are ON
        assertThat(rec.isEnabled()).isTrue();
    }

    @Test
    void testRejectRemoteThenAccept() {
        OptionNegotiator rejectFirst = new OptionNegotiator() {
            @Override
            public TelnetCommand shouldAcceptRemote(int option) {
                return TelnetCommand.DONT;
            }
        };
        OptionNegotiator acceptAll = ACCEPT_ALL;

        OptionRecord rec = new OptionRecord(TelnetOption.ECHO.code());

        // Remote WILL, we DONT -> remote stays OFF
        assertThat(rec.onWill(rejectFirst)).isEqualTo(TelnetCommand.DONT);
        assertThat(rec.remoteState()).isEqualTo(OptionState.OFF);

        // Now switch to accepting
        assertThat(rec.onWill(acceptAll)).isEqualTo(TelnetCommand.DO);
        assertThat(rec.remoteState()).isEqualTo(OptionState.ON_DEF);
    }
}
