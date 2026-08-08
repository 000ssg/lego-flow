package ssg.legoflow.email.imap.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ImapStatusTest {

    @Test
    void testAllStatusTexts() {
        assertThat(ImapStatus.OK.text()).isEqualTo("OK");
        assertThat(ImapStatus.NO.text()).isEqualTo("NO");
        assertThat(ImapStatus.BAD.text()).isEqualTo("BAD");
        assertThat(ImapStatus.BYE.text()).isEqualTo("BYE");
        assertThat(ImapStatus.PREAUTH.text()).isEqualTo("PREAUTH");
    }

    @Test
    void testParseExactMatch() {
        assertThat(ImapStatus.parse("OK")).isEqualTo(ImapStatus.OK);
        assertThat(ImapStatus.parse("NO")).isEqualTo(ImapStatus.NO);
        assertThat(ImapStatus.parse("BAD")).isEqualTo(ImapStatus.BAD);
        assertThat(ImapStatus.parse("BYE")).isEqualTo(ImapStatus.BYE);
        assertThat(ImapStatus.parse("PREAUTH")).isEqualTo(ImapStatus.PREAUTH);
    }

    @Test
    void testParseCaseInsensitive() {
        assertThat(ImapStatus.parse("ok")).isEqualTo(ImapStatus.OK);
        assertThat(ImapStatus.parse("Ok")).isEqualTo(ImapStatus.OK);
        assertThat(ImapStatus.parse("no")).isEqualTo(ImapStatus.NO);
        assertThat(ImapStatus.parse("bad")).isEqualTo(ImapStatus.BAD);
        assertThat(ImapStatus.parse("bye")).isEqualTo(ImapStatus.BYE);
        assertThat(ImapStatus.parse("preauth")).isEqualTo(ImapStatus.PREAUTH);
    }

    @Test
    void testParseUnknownThrows() {
        assertThatThrownBy(() -> ImapStatus.parse("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void testParseEmptyString() {
        assertThatThrownBy(() -> ImapStatus.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
