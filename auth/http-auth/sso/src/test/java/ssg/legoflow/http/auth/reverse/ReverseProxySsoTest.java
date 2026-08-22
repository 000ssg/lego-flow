package ssg.legoflow.http.auth.reverse;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class ReverseProxySsoTest {

    private ReverseProxySso proxySso;
    private ReverseProxySsoConfig config;

    @BeforeEach
    void setUp() {
        config = ReverseProxySsoConfig.defaults();
        proxySso = new ReverseProxySso(config);
    }

    @Test
    void testExtractPrincipal() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "alice");
        var principal = proxySso.extractPrincipal(request);
        assertThat(principal).isPresent();
        assertThat(principal.get().getName()).isEqualTo("alice");
    }

    @Test
    void testExtractPrincipalWithRoles() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "alice");
        request.getHeaders().set("x-forwarded-roles", "admin,user");
        var principal = proxySso.extractPrincipal(request);
        assertThat(principal).isPresent();
        assertThat(principal.get().getRoles()).containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    void testExtractPrincipalWithEmail() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "alice");
        request.getHeaders().set("x-forwarded-email", "alice@example.com");
        var principal = proxySso.extractPrincipal(request);
        assertThat(principal).isPresent();
        assertThat(principal.get().<String>getAttribute("email")).isEqualTo("alice@example.com");
    }

    @Test
    void testExtractPrincipalWithDisplayName() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "alice");
        request.getHeaders().set("x-forwarded-name", "Alice Smith");
        var principal = proxySso.extractPrincipal(request);
        assertThat(principal).isPresent();
        assertThat(principal.get().<String>getAttribute("display_name")).isEqualTo("Alice Smith");
    }

    @Test
    void testExtractPrincipalNoHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var principal = proxySso.extractPrincipal(request);
        assertThat(principal).isEmpty();
    }

    @Test
    void testExtractPrincipalBlankUser() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "   ");
        var principal = proxySso.extractPrincipal(request);
        assertThat(principal).isEmpty();
    }

    @Test
    void testPrepareBackendRequest() {
        var principal = new AuthPrincipal("alice", Set.of("admin"),
                Map.of("email", "alice@example.com"));
        var request = HttpRequest.of(HttpMethod.GET, "/backend");
        // Pre-set spoofed headers
        request.getHeaders().set("x-forwarded-user", "evil");

        proxySso.prepareBackendRequest(request, principal);

        assertThat(request.getHeaders().get("x-forwarded-user")).isEqualTo("alice");
        assertThat(request.getHeaders().get("x-forwarded-roles")).isEqualTo("admin");
        assertThat(request.getHeaders().get("x-forwarded-email")).isEqualTo("alice@example.com");
    }

    @Test
    void testAuthenticate() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "alice");
        var response = HttpResponse.of(HttpStatus.OK);
        var principal = proxySso.authenticate(request, response);
        assertThat(principal).isPresent();
        assertThat(principal.get().getName()).isEqualTo("alice");
    }

    @Test
    void testAuthenticateNoHeaders() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        var response = HttpResponse.of(HttpStatus.OK);
        var principal = proxySso.authenticate(request, response);
        assertThat(principal).isEmpty();
    }

    @Test
    void testGetConfig() {
        assertThat(proxySso.getConfig()).isEqualTo(config);
    }

    @Test
    void testGetHeaderInjector() {
        assertThat(proxySso.getHeaderInjector()).isNotNull();
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> new ReverseProxySso(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExtractPrincipalEmptyRoles() {
        var request = HttpRequest.of(HttpMethod.GET, "/api");
        request.getHeaders().set("x-forwarded-user", "alice");
        request.getHeaders().set("x-forwarded-roles", "  ");
        var principal = proxySso.extractPrincipal(request);
        assertThat(principal).isPresent();
        assertThat(principal.get().getRoles()).isEmpty();
    }
}
