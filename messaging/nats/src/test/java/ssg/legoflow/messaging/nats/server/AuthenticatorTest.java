package ssg.legoflow.messaging.nats.server;

import ssg.legoflow.messaging.nats.protocol.ConnectOptions;
import ssg.legoflow.messaging.nats.server.auth.TokenAuthenticator;
import ssg.legoflow.messaging.nats.server.auth.UserPassAuthenticator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for authenticator implementations.
 */
class AuthenticatorTest {

    // --- TokenAuthenticator ---

    @Test
    void testTokenAuthSuccess() {
        var auth = new TokenAuthenticator("mytoken");
        var opts = ConnectOptions.withDefaults("c").withToken("mytoken");
        assertThat(auth.authenticate(opts)).isTrue();
    }

    @Test
    void testTokenAuthFailure() {
        var auth = new TokenAuthenticator("mytoken");
        var opts = ConnectOptions.withDefaults("c").withToken("wrong");
        assertThat(auth.authenticate(opts)).isFalse();
    }

    @Test
    void testTokenAuthNoToken() {
        var auth = new TokenAuthenticator("mytoken");
        var opts = ConnectOptions.withDefaults("c");
        assertThat(auth.authenticate(opts)).isFalse();
    }

    @Test
    void testTokenAuthNullThrows() {
        assertThatThrownBy(() -> new TokenAuthenticator(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- UserPassAuthenticator ---

    @Test
    void testUserPassSuccess() {
        var auth = new UserPassAuthenticator().addUser("admin", "secret");
        var opts = ConnectOptions.withDefaults("c").withUserPass("admin", "secret");
        assertThat(auth.authenticate(opts)).isTrue();
    }

    @Test
    void testUserPassWrongPassword() {
        var auth = new UserPassAuthenticator().addUser("admin", "secret");
        var opts = ConnectOptions.withDefaults("c").withUserPass("admin", "wrong");
        assertThat(auth.authenticate(opts)).isFalse();
    }

    @Test
    void testUserPassUnknownUser() {
        var auth = new UserPassAuthenticator().addUser("admin", "secret");
        var opts = ConnectOptions.withDefaults("c").withUserPass("unknown", "secret");
        assertThat(auth.authenticate(opts)).isFalse();
    }

    @Test
    void testUserPassNoCredentials() {
        var auth = new UserPassAuthenticator().addUser("admin", "secret");
        var opts = ConnectOptions.withDefaults("c");
        assertThat(auth.authenticate(opts)).isFalse();
    }

    @Test
    void testUserPassMultipleUsers() {
        var auth = new UserPassAuthenticator()
                .addUser("alice", "pass1")
                .addUser("bob", "pass2");

        assertThat(auth.userCount()).isEqualTo(2);
        assertThat(auth.authenticate(ConnectOptions.withDefaults("c").withUserPass("alice", "pass1"))).isTrue();
        assertThat(auth.authenticate(ConnectOptions.withDefaults("c").withUserPass("bob", "pass2"))).isTrue();
    }

    @Test
    void testUserPassRemoveUser() {
        var auth = new UserPassAuthenticator()
                .addUser("alice", "pass")
                .removeUser("alice");

        assertThat(auth.userCount()).isEqualTo(0);
        assertThat(auth.authenticate(ConnectOptions.withDefaults("c").withUserPass("alice", "pass"))).isFalse();
    }
}
