package ssg.legoflow.http.auth;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class AuthMiddlewareTest {

    private AuthSchemeRegistry registry;
    private AuthContext context;
    private AuthFilter authFilter;
    private HttpRequestHandler delegate;

    @BeforeEach
    void setUp() {
        registry = new AuthSchemeRegistry();
        context = AuthContext.ofRealm("test");
        registry.register(new AuthenticationScheme() {
            @Override public String schemeName() { return "Bearer"; }
            @Override public AuthResult authenticate(HttpRequest request, AuthContext ctx) {
                String auth = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
                if (auth != null && auth.equals("Bearer valid")) {
                    return AuthResult.success(AuthPrincipal.of("user", Set.of("admin")));
                }
                if (auth != null && auth.equals("Bearer viewer")) {
                    return AuthResult.success(AuthPrincipal.of("viewer", Set.of("viewer")));
                }
                return AuthResult.failure("Invalid");
            }
            @Override public void challenge(HttpResponse response, AuthContext ctx) {
                response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"test\"");
            }
            @Override public AuthCredentials extractCredentials(HttpRequest request) {
                return new AuthCredentials.None();
            }
        });
        authFilter = new AuthFilter(registry, context, "Bearer");
        delegate = (ctx, req) -> HttpResponse.of(HttpStatus.OK, "Hello");
    }

    @Test
    void testAuthenticatedRequest() {
        var mw = new AuthMiddleware(delegate, authFilter);
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer valid");
        var response = mw.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testUnauthenticatedRequest() {
        var mw = new AuthMiddleware(delegate, authFilter);
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        var response = mw.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testExcludedPath() {
        var mw = new AuthMiddleware(delegate, authFilter, Set.of("/health"), Set.of());
        var request = HttpRequest.of(HttpMethod.GET, "/health");
        var response = mw.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testRoleBasedAccess() {
        var mw = new AuthMiddleware(delegate, authFilter, Set.of(), Set.of("admin"));
        var request = HttpRequest.of(HttpMethod.GET, "/admin");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer valid");
        var response = mw.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testInsufficientRoles() {
        var mw = new AuthMiddleware(delegate, authFilter, Set.of(), Set.of("admin"));
        var request = HttpRequest.of(HttpMethod.GET, "/admin");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer viewer");
        var response = mw.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testFailedAuthReturnsChallengeResponse() {
        var mw = new AuthMiddleware(delegate, authFilter);
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer invalid");
        var response = mw.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testExcludedPathWithQueryString() {
        var mw = new AuthMiddleware(delegate, authFilter, Set.of("/health"), Set.of());
        var request = HttpRequest.of(HttpMethod.GET, "/health?check=deep");
        var response = mw.handle(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testGetDelegate() {
        var mw = new AuthMiddleware(delegate, authFilter);
        assertThat(mw.getDelegate()).isEqualTo(delegate);
    }

    @Test
    void testGetExcludedPaths() {
        var mw = new AuthMiddleware(delegate, authFilter, Set.of("/a", "/b"), Set.of());
        assertThat(mw.getExcludedPaths()).containsExactlyInAnyOrder("/a", "/b");
    }

    @Test
    void testGetRequiredRoles() {
        var mw = new AuthMiddleware(delegate, authFilter, Set.of(), Set.of("admin"));
        assertThat(mw.getRequiredRoles()).containsExactly("admin");
    }
}
