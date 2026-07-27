package ssg.legoflow.http.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AuthContextTest {

    @Test
    void testMinimalContext() {
        var ctx = AuthContext.ofRealm("test-realm");
        assertThat(ctx.getRealm()).isEqualTo("test-realm");
        assertThat(ctx.getUserStore()).isEmpty();
        assertThat(ctx.getSessionManager()).isEmpty();
        assertThat(ctx.getAuthenticatedPrincipal()).isEmpty();
    }

    @Test
    void testSetAuthenticatedPrincipal() {
        var ctx = AuthContext.ofRealm("test");
        assertThat(ctx.getAuthenticatedPrincipal()).isEmpty();
        ctx.setAuthenticatedPrincipal(AuthPrincipal.of("alice"));
        assertThat(ctx.getAuthenticatedPrincipal()).isPresent();
        assertThat(ctx.getAuthenticatedPrincipal().get().getName()).isEqualTo("alice");
    }

    @Test
    void testNullRealmThrows() {
        assertThatThrownBy(() -> AuthContext.ofRealm(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testFullContext() {
        var ctx = new AuthContext("realm", null, null);
        assertThat(ctx.getRealm()).isEqualTo("realm");
    }
}
