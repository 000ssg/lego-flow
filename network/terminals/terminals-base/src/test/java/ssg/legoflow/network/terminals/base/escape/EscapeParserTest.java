package ssg.legoflow.network.terminals.base.escape;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EscapeParserTest {

    @Test
    void plainTextPassthrough() {
        boolean[] captured = {false};
        EscapeParser parser = new EscapeParser(params -> captured[0] = true);
        parser.feed("Hello World");
        assertThat(captured[0]).isFalse();
    }

    @Test
    void simpleCsiSequence() {
        CSIParams[] result = {null};
        EscapeParser parser = new EscapeParser(params -> result[0] = params);
        parser.feed("\u001B[2;10H");
        assertThat(result[0]).isNotNull();
        assertThat(result[0].finalByte()).isEqualTo('H');
        assertThat(result[0].get(0)).isEqualTo(2);
        assertThat(result[0].get(1)).isEqualTo(10);
    }

    @Test
    void csiNoParams() {
        CSIParams[] result = {null};
        EscapeParser parser = new EscapeParser(params -> result[0] = params);
        parser.feed("\u001B[J");
        assertThat(result[0]).isNotNull();
        assertThat(result[0].finalByte()).isEqualTo('J');
        assertThat(result[0].get(0, 0)).isZero();
    }

    @Test
    void csiSingleParam() {
        CSIParams[] result = {null};
        EscapeParser parser = new EscapeParser(params -> result[0] = params);
        parser.feed("\u001B[32m");
        assertThat(result[0]).isNotNull();
        assertThat(result[0].finalByte()).isEqualTo('m');
        assertThat(result[0].get(0)).isEqualTo(32);
    }

    @Test
    void csiMultipleParams() {
        CSIParams[] result = {null};
        EscapeParser parser = new EscapeParser(params -> result[0] = params);
        parser.feed("\u001B[38;5;196m");
        assertThat(result[0]).isNotNull();
        assertThat(result[0].get(0)).isEqualTo(38);
        assertThat(result[0].get(1)).isEqualTo(5);
        assertThat(result[0].get(2)).isEqualTo(196);
    }

    @Test
    void oscSequence() {
        String[] result = {null};
        EscapeParser parser = new EscapeParser(new EscapeParser.SequenceHandler() {
            @Override public void handleCSI(CSIParams p) {}
            @Override public void handleOSC(String data) { result[0] = data; }
        });
        parser.feed("\u001B]0;My Title\u0007");
        assertThat(result[0]).isEqualTo("0;My Title");
    }

    @Test
    void oscWithStTerminator() {
        String[] result = {null};
        EscapeParser parser = new EscapeParser(new EscapeParser.SequenceHandler() {
            @Override public void handleCSI(CSIParams p) {}
            @Override public void handleOSC(String data) { result[0] = data; }
        });
        parser.feed("\u001B]2;Title\u001B\\");
        assertThat(result[0]).isEqualTo("2;Title");
    }

    @Test
    void incompleteSequenceDiscardedOnReset() {
        EscapeParser parser = new EscapeParser(p -> {});
        parser.feed('\u001B');
        parser.feed('[');
        parser.reset();
        assertThat(parser.currentState()).isEqualTo(EscapeParser.State.GROUND);
    }

    @Test
    void midSequenceDetection() {
        EscapeParser parser = new EscapeParser(p -> {});
        parser.feed('\u001B');
        assertThat(parser.currentState()).isEqualTo(EscapeParser.State.ESC);
        assertThat(parser.isMidSequence()).isTrue();
        parser.feed('[');
        assertThat(parser.currentState()).isEqualTo(EscapeParser.State.CSI);
    }

    @Test
    void escapeByteInGroundState() {
        EscapeParser parser = new EscapeParser(p -> {});
        parser.feed('\u001B');
        assertThat(parser.currentState()).isEqualTo(EscapeParser.State.ESC);
    }

    @Test
    void bytesAfterEscNonCsi() {
        boolean[] captured = {false};
        EscapeParser parser = new EscapeParser(p -> captured[0] = true);
        parser.feed('\u001B');
        parser.feed('M'); // RS — not CSI
        assertThat(captured[0]).isFalse();
        assertThat(parser.currentState()).isEqualTo(EscapeParser.State.GROUND);
    }

    @Test
    void feedBytesArray() {
        CSIParams[] result = {null};
        EscapeParser parser = new EscapeParser(params -> result[0] = params);
        byte[] data = new byte[]{0x1B, '[', 'J'};
        parser.feed(data);
        assertThat(result[0]).isNotNull();
        assertThat(result[0].finalByte()).isEqualTo('J');
    }
}
