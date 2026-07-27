package ssg.legoflow.http.auth.reverse;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class AuthHeaderInjectorTest {

    private AuthHeaderInjector injector;
    private ReverseProxySsoConfig config;

    @BeforeEach
    void setUp() {
        config = ReverseProxySsoConfig.defaults();
        injector = new AuthHeaderInjector(config);
    }

    @Test
    void testInjectUsername() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = new AuthPrincipal("alice", Set.of(), null);
        injector.injectHeaders(request, principal);
        assertThat(request.getHeaders().get("x-forwarded-user")).isEqualTo("alice");
    }

    @Test
    void testInjectRoles() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = new AuthPrincipal("alice", Set.of("admin", "user"), null);
        injector.injectHeaders(request, principal);
        String roles = request.getHeaders().get("x-forwarded-roles");
        assertThat(roles).isNotNull();
        assertThat(roles).contains("admin");
        assertThat(roles).contains("user");
    }

    @Test
    void testInjectNoRoles() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = new AuthPrincipal("alice", Set.of(), null);
        injector.injectHeaders(request, principal);
        assertThat(request.getHeaders().get("x-forwarded-roles")).isNull();
    }

    @Test
    void testInjectEmail() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = new AuthPrincipal("alice", Set.of(), Map.of("email", "alice@example.com"));
        injector.injectHeaders(request, principal);
        assertThat(request.getHeaders().get("x-forwarded-email")).isEqualTo("alice@example.com");
    }

    @Test
    void testInjectDisplayName() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = new AuthPrincipal("alice", Set.of(), Map.of("display_name", "Alice Smith"));
        injector.injectHeaders(request, principal);
        assertThat(request.getHeaders().get("x-forwarded-name")).isEqualTo("Alice Smith");
    }

    @Test
    void testInjectNoEmail() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = new AuthPrincipal("alice", Set.of(), null);
        injector.injectHeaders(request, principal);
        assertThat(request.getHeaders().get("x-forwarded-email")).isNull();
    }

    @Test
    void testStripHeaders() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "spoofed");
        request.getHeaders().set("x-forwarded-roles", "admin");
        request.getHeaders().set("x-forwarded-email", "evil@example.com");
        request.getHeaders().set("x-forwarded-name", "Evil");

        injector.stripHeaders(request);

        assertThat(request.getHeaders().get("x-forwarded-user")).isNull();
        assertThat(request.getHeaders().get("x-forwarded-roles")).isNull();
        assertThat(request.getHeaders().get("x-forwarded-email")).isNull();
        assertThat(request.getHeaders().get("x-forwarded-name")).isNull();
    }

    @Test
    void testCustomHeaders() {
        var customConfig = new ReverseProxySsoConfig("x-auth-user", "x-auth-roles",
                "x-auth-email", "x-auth-name", Set.of(), false);
        var customInjector = new AuthHeaderInjector(customConfig);
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = new AuthPrincipal("bob", Set.of("user"), Map.of("email", "bob@test.com"));

        customInjector.injectHeaders(request, principal);

        assertThat(request.getHeaders().get("x-auth-user")).isEqualTo("bob");
        assertThat(request.getHeaders().get("x-auth-roles")).isEqualTo("user");
        assertThat(request.getHeaders().get("x-auth-email")).isEqualTo("bob@test.com");
    }

    @Test
    void testGetConfig() {
        assertThat(injector.getConfig()).isEqualTo(config);
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> new AuthHeaderInjector(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullRequestThrows() {
        var principal = new AuthPrincipal("alice", Set.of(), null);
        assertThatThrownBy(() -> injector.injectHeaders(null, principal))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullPrincipalThrows() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        assertThatThrownBy(() -> injector.injectHeaders(request, null))
                .isInstanceOf(NullPointerException.class);
    }
}
