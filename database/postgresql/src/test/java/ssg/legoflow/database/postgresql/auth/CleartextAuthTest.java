package ssg.legoflow.database.postgresql.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CleartextAuth}.
 */
class CleartextAuthTest {

    @Test
    void testMethod() {
        assertThat(new CleartextAuth().method()).isEqualTo("cleartext");
    }

    @Test
    void testAuthenticateSuccess() {
        var auth = new CleartextAuth().addUser("alice", "secret");
        assertThat(auth.authenticate("alice", "secret")).isTrue();
    }

    @Test
    void testAuthenticateWrongPassword() {
        var auth = new CleartextAuth().addUser("alice", "secret");
        assertThat(auth.authenticate("alice", "wrong")).isFalse();
    }

    @Test
    void testAuthenticateUnknownUser() {
        var auth = new CleartextAuth().addUser("alice", "secret");
        assertThat(auth.authenticate("bob", "secret")).isFalse();
    }

    @Test
    void testMultipleUsers() {
        var auth = new CleartextAuth()
                .addUser("alice", "pass1")
                .addUser("bob", "pass2");
        assertThat(auth.authenticate("alice", "pass1")).isTrue();
        assertThat(auth.authenticate("bob", "pass2")).isTrue();
        assertThat(auth.authenticate("alice", "pass2")).isFalse();
    }

    @Test
    void testEmptyPassword() {
        var auth = new CleartextAuth().addUser("alice", "");
        assertThat(auth.authenticate("alice", "")).isTrue();
        assertThat(auth.authenticate("alice", "notempty")).isFalse();
    }
}
