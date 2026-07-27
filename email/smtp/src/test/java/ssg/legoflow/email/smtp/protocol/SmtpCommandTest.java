package ssg.legoflow.email.smtp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SmtpCommand}.
 */
class SmtpCommandTest {

    @ParameterizedTest
    @EnumSource(SmtpCommand.class)
    void testParseAllCommands(SmtpCommand command) {
        assertThat(SmtpCommand.parse(command.name())).isEqualTo(command);
    }

    @Test
    void testParseCaseInsensitive() {
        assertThat(SmtpCommand.parse("ehlo")).isEqualTo(SmtpCommand.EHLO);
        assertThat(SmtpCommand.parse("Ehlo")).isEqualTo(SmtpCommand.EHLO);
        assertThat(SmtpCommand.parse("EHLO")).isEqualTo(SmtpCommand.EHLO);
    }

    @Test
    void testParseWithWhitespace() {
        assertThat(SmtpCommand.parse("  MAIL  ")).isEqualTo(SmtpCommand.MAIL);
    }

    @Test
    void testParseNullThrows() {
        assertThatThrownBy(() -> SmtpCommand.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseBlankThrows() {
        assertThatThrownBy(() -> SmtpCommand.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmtpCommand.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseUnknownThrows() {
        assertThatThrownBy(() -> SmtpCommand.parse("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(SmtpCommand.class)
    void testWireFormMatchesName(SmtpCommand command) {
        assertThat(command.wireForm()).isEqualTo(command.name());
    }

    @Test
    void testAllExpectedCommandsExist() {
        assertThat(SmtpCommand.values()).hasSize(14);
        assertThat(SmtpCommand.valueOf("EHLO")).isNotNull();
        assertThat(SmtpCommand.valueOf("HELO")).isNotNull();
        assertThat(SmtpCommand.valueOf("MAIL")).isNotNull();
        assertThat(SmtpCommand.valueOf("RCPT")).isNotNull();
        assertThat(SmtpCommand.valueOf("DATA")).isNotNull();
        assertThat(SmtpCommand.valueOf("BDAT")).isNotNull();
        assertThat(SmtpCommand.valueOf("RSET")).isNotNull();
        assertThat(SmtpCommand.valueOf("QUIT")).isNotNull();
        assertThat(SmtpCommand.valueOf("NOOP")).isNotNull();
        assertThat(SmtpCommand.valueOf("VRFY")).isNotNull();
        assertThat(SmtpCommand.valueOf("EXPN")).isNotNull();
        assertThat(SmtpCommand.valueOf("HELP")).isNotNull();
        assertThat(SmtpCommand.valueOf("STARTTLS")).isNotNull();
        assertThat(SmtpCommand.valueOf("AUTH")).isNotNull();
    }
}
