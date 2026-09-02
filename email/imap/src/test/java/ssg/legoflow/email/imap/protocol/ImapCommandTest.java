package ssg.legoflow.email.imap.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ImapCommand}.
 *
 * @since 0.1.0
 */
class ImapCommandTest {

    @Test
    void testParseCapability() {
        assertThat(ImapCommand.parse("CAPABILITY")).isEqualTo(ImapCommand.CAPABILITY);
    }

    @Test
    void testParseCaseInsensitive() {
        assertThat(ImapCommand.parse("login")).isEqualTo(ImapCommand.LOGIN);
        assertThat(ImapCommand.parse("Login")).isEqualTo(ImapCommand.LOGIN);
        assertThat(ImapCommand.parse("LOGIN")).isEqualTo(ImapCommand.LOGIN);
    }

    @Test
    void testParseAllCommands() {
        for (ImapCommand cmd : ImapCommand.values()) {
            assertThat(ImapCommand.parse(cmd.text())).isEqualTo(cmd);
        }
    }

    @Test
    void testParseUnknownCommand() {
        assertThatThrownBy(() -> ImapCommand.parse("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCommandText() {
        assertThat(ImapCommand.SELECT.text()).isEqualTo("SELECT");
        assertThat(ImapCommand.FETCH.text()).isEqualTo("FETCH");
    }

    @Test
    void testRequiredState() {
        assertThat(ImapCommand.CAPABILITY.requiredState()).isEqualTo(ImapCommand.ImapState.ANY);
        assertThat(ImapCommand.LOGIN.requiredState()).isEqualTo(ImapCommand.ImapState.NOT_AUTHENTICATED);
        assertThat(ImapCommand.SELECT.requiredState()).isEqualTo(ImapCommand.ImapState.AUTHENTICATED);
        assertThat(ImapCommand.FETCH.requiredState()).isEqualTo(ImapCommand.ImapState.SELECTED);
    }

    @Test
    void testAnyStateCommands() {
        assertThat(ImapCommand.CAPABILITY.requiredState()).isEqualTo(ImapCommand.ImapState.ANY);
        assertThat(ImapCommand.NOOP.requiredState()).isEqualTo(ImapCommand.ImapState.ANY);
        assertThat(ImapCommand.LOGOUT.requiredState()).isEqualTo(ImapCommand.ImapState.ANY);
    }

    @Test
    void testNotAuthenticatedCommands() {
        assertThat(ImapCommand.LOGIN.requiredState()).isEqualTo(ImapCommand.ImapState.NOT_AUTHENTICATED);
        assertThat(ImapCommand.AUTHENTICATE.requiredState()).isEqualTo(ImapCommand.ImapState.NOT_AUTHENTICATED);
        assertThat(ImapCommand.STARTTLS.requiredState()).isEqualTo(ImapCommand.ImapState.NOT_AUTHENTICATED);
    }

    @Test
    void testAuthenticatedCommands() {
        assertThat(ImapCommand.SELECT.requiredState()).isEqualTo(ImapCommand.ImapState.AUTHENTICATED);
        assertThat(ImapCommand.EXAMINE.requiredState()).isEqualTo(ImapCommand.ImapState.AUTHENTICATED);
        assertThat(ImapCommand.CREATE.requiredState()).isEqualTo(ImapCommand.ImapState.AUTHENTICATED);
        assertThat(ImapCommand.DELETE.requiredState()).isEqualTo(ImapCommand.ImapState.AUTHENTICATED);
        assertThat(ImapCommand.IDLE.requiredState()).isEqualTo(ImapCommand.ImapState.AUTHENTICATED);
    }

    @Test
    void testSelectedCommands() {
        assertThat(ImapCommand.FETCH.requiredState()).isEqualTo(ImapCommand.ImapState.SELECTED);
        assertThat(ImapCommand.STORE.requiredState()).isEqualTo(ImapCommand.ImapState.SELECTED);
        assertThat(ImapCommand.COPY.requiredState()).isEqualTo(ImapCommand.ImapState.SELECTED);
        assertThat(ImapCommand.MOVE.requiredState()).isEqualTo(ImapCommand.ImapState.SELECTED);
        assertThat(ImapCommand.SEARCH.requiredState()).isEqualTo(ImapCommand.ImapState.SELECTED);
        assertThat(ImapCommand.EXPUNGE.requiredState()).isEqualTo(ImapCommand.ImapState.SELECTED);
    }
}
