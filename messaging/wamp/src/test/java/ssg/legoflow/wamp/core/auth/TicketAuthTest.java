package ssg.legoflow.wamp.core.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Ticket authentication.
 */
class TicketAuthTest {

    @Test
    void testAuthMethod() {
        assertThat(TicketAuth.AUTH_METHOD).isEqualTo("ticket");
    }

    @Test
    void testGenerateChallengeIsEmpty() {
        var challenge = TicketAuth.generateChallenge();
        assertThat(challenge).isEmpty();
    }

    @Test
    void testVerifyCorrectTicket() {
        assertThat(TicketAuth.verify("my-token-123", "my-token-123")).isTrue();
    }

    @Test
    void testVerifyWrongTicket() {
        assertThat(TicketAuth.verify("expected", "actual")).isFalse();
    }

    @Test
    void testVerifyNullExpected() {
        assertThat(TicketAuth.verify(null, "ticket")).isFalse();
    }
}
