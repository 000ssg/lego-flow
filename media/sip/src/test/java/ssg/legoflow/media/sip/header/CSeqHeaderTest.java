package ssg.legoflow.media.sip.header;

import org.junit.jupiter.api.Test;
import ssg.legoflow.media.sip.protocol.SipMethod;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CSeqHeader}.
 */
class CSeqHeaderTest {

    @Test
    void testParseCSeq() {
        var cseq = CSeqHeader.parse("1 INVITE");
        assertThat(cseq.sequence()).isEqualTo(1);
        assertThat(cseq.method()).isEqualTo(SipMethod.INVITE);
    }

    @Test
    void testParseLargeSequence() {
        var cseq = CSeqHeader.parse("2147483647 BYE");
        assertThat(cseq.sequence()).isEqualTo(2147483647L);
        assertThat(cseq.method()).isEqualTo(SipMethod.BYE);
    }

    @Test
    void testParseRegister() {
        var cseq = CSeqHeader.parse("314159 REGISTER");
        assertThat(cseq.sequence()).isEqualTo(314159);
        assertThat(cseq.method()).isEqualTo(SipMethod.REGISTER);
    }

    @Test
    void testFormat() {
        var cseq = new CSeqHeader(42, SipMethod.OPTIONS);
        assertThat(cseq.format()).isEqualTo("42 OPTIONS");
    }

    @Test
    void testInvalidFormatThrows() {
        assertThatThrownBy(() -> CSeqHeader.parse("INVITE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegativeSequenceThrows() {
        assertThatThrownBy(() -> new CSeqHeader(-1, SipMethod.INVITE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullMethodThrows() {
        assertThatThrownBy(() -> new CSeqHeader(1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
