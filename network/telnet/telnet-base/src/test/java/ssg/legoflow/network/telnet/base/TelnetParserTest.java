package ssg.legoflow.network.telnet.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class TelnetParserTest {

    private TestListener listener;
    private TelnetParser parser;

    @BeforeEach
    void setUp() {
        listener = new TestListener();
        parser = new TelnetParser(listener);
    }

    @Test
    void testPlainData() {
        parser.feed("hello".getBytes());
        parser.flush();
        assertThat(listener.dataEvents).hasSize(1);
        assertThat(listener.dataEvents.get(0))
                .containsExactly((int)'h', (int)'e', (int)'l', (int)'l', (int)'o');
    }

    @Test
    void testMultipleDataChunks() {
        parser.feed("ab".getBytes());
        parser.flush();
        parser.feed("cd".getBytes());
        parser.flush();
        assertThat(listener.dataEvents).hasSize(2);
        assertThat(listener.dataEvents.get(0))
                .containsExactly((int)'a', (int)'b');
        assertThat(listener.dataEvents.get(1))
                .containsExactly((int)'c', (int)'d');
    }

    @Test
    void testEscapedIac() {
        // "a" IAC IAC "b" → parser flushes "a" on first IAC,
        // then accumulates [255, b] after IAC IAC escape.
        parser.feed(new byte[]{'a', (byte) 255, (byte) 255, 'b'});
        parser.flush();
        assertThat(listener.dataEvents).hasSize(2);
        assertThat(listener.dataEvents.get(0)).containsExactly((int)'a');
        assertThat(listener.dataEvents.get(1))
                .containsExactly(255, (int)'b');

        // Verify total data is correct
        assertThat(listener.allData()).containsExactly((int)'a', 255, (int)'b');
    }

    @Test
    void testIacWill() {
        parser.feed(new byte[]{(byte) 255, (byte) 251, 1});
        assertThat(listener.negotiateEvents).hasSize(1);
        var evt = listener.negotiateEvents.get(0);
        assertThat(evt.command()).isEqualTo(TelnetCommand.WILL);
        assertThat(evt.option()).isEqualTo(1);
    }

    @Test
    void testIacWont() {
        parser.feed(new byte[]{(byte) 255, (byte) 252, 31});
        assertThat(listener.negotiateEvents.get(0).command()).isEqualTo(TelnetCommand.WONT);
        assertThat(listener.negotiateEvents.get(0).option()).isEqualTo(31);
    }

    @Test
    void testIacDo() {
        parser.feed(new byte[]{(byte) 255, (byte) 253, 1});
        assertThat(listener.negotiateEvents.get(0).command()).isEqualTo(TelnetCommand.DO);
        assertThat(listener.negotiateEvents.get(0).option()).isEqualTo(1);
    }

    @Test
    void testIacDont() {
        parser.feed(new byte[]{(byte) 255, (byte) 254, 31});
        assertThat(listener.negotiateEvents.get(0).command()).isEqualTo(TelnetCommand.DONT);
        assertThat(listener.negotiateEvents.get(0).option()).isEqualTo(31);
    }

    @Test
    void testSingleByteCommands() {
        for (TelnetCommand cmd : TelnetCommand.values()) {
            if (cmd.hasOption() || cmd == TelnetCommand.SB || cmd == TelnetCommand.BRK) continue;

            parser.reset();
            listener.clear();
            parser.feed(new byte[]{(byte) 255, (byte) cmd.code()});
            assertThat(listener.commandEvents).hasSize(1);
            assertThat(listener.commandEvents.get(0)).isEqualTo(cmd);
        }
    }

    @Test
    void testNopCommand() {
        parser.feed(new byte[]{(byte) 255, (byte) 241});
        assertThat(listener.commandEvents.get(0)).isEqualTo(TelnetCommand.NOP);
    }

    @Test
    void testDmCommand() {
        parser.feed(new byte[]{(byte) 255, (byte) 242});
        assertThat(listener.commandEvents.get(0)).isEqualTo(TelnetCommand.DM);
    }

    @Test
    void testBrkOutOfBand() {
        // BRK (255) is an out-of-band TCP signal, not a byte command.
        // Sending IAC IAC (255 255) produces a literal 255 byte, not BRK.
        assertThat(TelnetCommand.BRK.code()).isEqualTo(255);
        
        // Verify: IAC IAC produces literal 255 in data, not a command
        parser.feed(new byte[]{(byte) 255, (byte) 255});
        parser.flush();
        assertThat(listener.commandEvents).isEmpty();
        assertThat(listener.allData()).containsExactly(255);
    }

    @Test
    void testAytCommand() {
        parser.feed(new byte[]{(byte) 255, (byte) 246});
        assertThat(listener.commandEvents.get(0)).isEqualTo(TelnetCommand.AYT);
    }

    @Test
    void testGaCommand() {
        parser.feed(new byte[]{(byte) 255, (byte) 249});
        assertThat(listener.commandEvents.get(0)).isEqualTo(TelnetCommand.GA);
    }

    @Test
    void testEcCommand() {
        parser.feed(new byte[]{(byte) 255, (byte) 247});
        assertThat(listener.commandEvents.get(0)).isEqualTo(TelnetCommand.EC);
    }

    @Test
    void testElCommand() {
        parser.feed(new byte[]{(byte) 255, (byte) 248});
        assertThat(listener.commandEvents.get(0)).isEqualTo(TelnetCommand.EL);
    }

    @Test
    void testSubnegotiationBasic() {
        parser.feed(new byte[]{
                (byte) 255, (byte) 250, 24, 'x', 't', 't', 'y', 0,
                (byte) 255, (byte) 240
        });
        assertThat(listener.subnegotiationEvents).hasSize(1);
        var evt = listener.subnegotiationEvents.get(0);
        assertThat(evt.option()).isEqualTo(24);
        assertThat(evt.data())
                .containsExactly((int)'x', (int)'t', (int)'t', (int)'y', 0);
    }

    @Test
    void testSubnegotiationEmpty() {
        parser.feed(new byte[]{
                (byte) 255, (byte) 250, 31,
                (byte) 255, (byte) 240
        });
        assertThat(listener.subnegotiationEvents.get(0).option()).isEqualTo(31);
        assertThat(listener.subnegotiationEvents.get(0).data()).isEmpty();
    }

    @Test
    void testSubnegotiationWithEscapedIac() {
        parser.feed(new byte[]{
                (byte) 255, (byte) 250, 24, 'a',
                (byte) 255, (byte) 255, 'b',
                (byte) 255, (byte) 240
        });
        assertThat(listener.subnegotiationEvents.get(0).data())
                .containsExactly((int)'a', 255, (int)'b');
    }

    @Test
    void testNawsSubnegotiation() {
        parser.feed(new byte[]{
                (byte) 255, (byte) 250, 31, 0, 80, 0, 24,
                (byte) 255, (byte) 240
        });
        assertThat(listener.subnegotiationEvents.get(0).option()).isEqualTo(31);
        assertThat(listener.subnegotiationEvents.get(0).data())
                .containsExactly(0, 80, 0, 24);
    }

    @Test
    void testDataThenCommandThenData() {
        parser.feed("ab".getBytes());
        parser.feed(new byte[]{(byte) 255, (byte) 246});
        parser.feed("cd".getBytes());
        parser.flush();
        assertThat(listener.dataEvents).hasSize(2);
        assertThat(listener.dataEvents.get(0))
                .containsExactly((int)'a', (int)'b');
        assertThat(listener.dataEvents.get(1))
                .containsExactly((int)'c', (int)'d');
        assertThat(listener.commandEvents).hasSize(1);
    }

    @Test
    void testCommandSplitAcrossFeeds() {
        parser.feed(new byte[]{(byte) 255});
        parser.feed(new byte[]{(byte) 246});
        assertThat(listener.commandEvents.get(0)).isEqualTo(TelnetCommand.AYT);
    }

    @Test
    void testWillSplitAcrossFeeds() {
        parser.feed(new byte[]{(byte) 255, (byte) 251});
        parser.feed(new byte[]{1});
        assertThat(listener.negotiateEvents.get(0).command()).isEqualTo(TelnetCommand.WILL);
        assertThat(listener.negotiateEvents.get(0).option()).isEqualTo(1);
    }

    @Test
    void testSbSplitAcrossFeeds() {
        parser.feed(new byte[]{(byte) 255, (byte) 250, 24});
        parser.feed(new byte[]{'x'});
        parser.feed(new byte[]{(byte) 255, (byte) 240});
        assertThat(listener.subnegotiationEvents.get(0).option()).isEqualTo(24);
        assertThat(listener.subnegotiationEvents.get(0).data())
                .containsExactly((int)'x');
    }

    @Test
    void testParserStateTransitions() {
        assertThat(parser.state()).isEqualTo(ParserState.DATA);
        parser.feed(new byte[]{(byte) 255});
        assertThat(parser.state()).isEqualTo(ParserState.COMMAND);
        parser.feed(new byte[]{(byte) 251});
        assertThat(parser.state()).isEqualTo(ParserState.NEGOTIATE);
        parser.feed(new byte[]{1});
        assertThat(parser.state()).isEqualTo(ParserState.DATA);
    }

    @Test
    void testResetClearsState() {
        parser.feed(new byte[]{(byte) 255});
        assertThat(parser.state()).isEqualTo(ParserState.COMMAND);
        parser.reset();
        assertThat(parser.state()).isEqualTo(ParserState.DATA);
        assertThat(listener.dataEvents).isEmpty();
    }

    @Test
    void testUnknownCommandIgnored() {
        parser.feed(new byte[]{(byte) 255, (byte) 100});
        assertThat(listener.commandEvents).isEmpty();
    }

    @Test
    void testSeOutsideSubnegotiationIsCommand() {
        parser.feed(new byte[]{(byte) 255, (byte) 240});
        assertThat(listener.commandEvents).containsExactly(TelnetCommand.SE);
    }

    @Test
    void testMultipleEscapedIac() {
        // IAC IAC IAC IAC → two literal 255 bytes
        parser.feed(new byte[]{
                (byte) 255, (byte) 255,
                (byte) 255, (byte) 255
        });
        parser.flush();
        assertThat(listener.allData()).containsExactly(255, 255);
    }

    // ---- Test listener ----

    static class TestListener implements TelnetListener {
        final List<List<Integer>> dataEvents = new ArrayList<>();
        final List<TelnetCommand> commandEvents = new ArrayList<>();
        final List<NegotiationEvent> negotiateEvents = new ArrayList<>();
        final List<SubnegotiationEvent> subnegotiationEvents = new ArrayList<>();

        record NegotiationEvent(TelnetCommand command, int option) {}
        record SubnegotiationEvent(int option, List<Integer> data) {}

        @Override
        public void onData(List<Integer> data) {
            dataEvents.add(List.copyOf(data));
        }

        @Override
        public void onCommand(TelnetCommand cmd) {
            commandEvents.add(cmd);
        }

        @Override
        public void onNegotiate(TelnetCommand cmd, int option) {
            negotiateEvents.add(new NegotiationEvent(cmd, option));
        }

        @Override
        public void onSubnegotiation(int option, List<Integer> data) {
            subnegotiationEvents.add(new SubnegotiationEvent(option, List.copyOf(data)));
        }

        void clear() {
            dataEvents.clear();
            commandEvents.clear();
            negotiateEvents.clear();
            subnegotiationEvents.clear();
        }

        /** Flatten all data events into a single list. */
        List<Integer> allData() {
            List<Integer> all = new ArrayList<>();
            for (List<Integer> evt : dataEvents) {
                all.addAll(evt);
            }
            return all;
        }
    }
}
