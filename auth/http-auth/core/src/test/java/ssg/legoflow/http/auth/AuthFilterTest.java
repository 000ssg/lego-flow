package ssg.legoflow.http.auth;

import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AuthFilterTest {

    private AuthSchemeRegistry registry;
    private AuthContext context;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        registry = new AuthSchemeRegistry();
        context = AuthContext.ofRealm("test-realm");

        // Register a simple "Test" scheme
        registry.register(new AuthenticationScheme() {
            @Override public String schemeName() { return "Test"; }
            @Override public AuthResult authenticate(HttpRequest request, AuthContext ctx) {
                var creds = extractCredentials(request);
                if (creds instanceof AuthCredentials.Bearer b && "valid-token".equals(b.token())) {
                    return AuthResult.success(AuthPrincipal.of("testuser"));
                }
                return AuthResult.failure("Invalid token");
            }
            @Override public void challenge(HttpResponse response, AuthContext ctx) {
                response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE,
                        "Test realm=\"" + ctx.getRealm() + "\"");
            }
            @Override public AuthCredentials extractCredentials(HttpRequest request) {
                String auth = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
                if (auth != null && auth.startsWith("Test ")) {
                    return new AuthCredentials.Bearer(auth.substring(5));
                }
                return new AuthCredentials.None();
            }
        });

        filter = new AuthFilter(registry, context, "Test");
    }

    @Test
    void testNoAuthorizationHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        var result = filter.filter(request);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testSuccessfulAuth() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Test valid-token");
        var result = filter.filter(request);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
        assertThat(context.getAuthenticatedPrincipal()).isPresent();
    }

    @Test
    void testFailedAuth() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Test invalid");
        var result = filter.filter(request);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testUnknownScheme() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Unknown token");
        var result = filter.filter(request);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testBuildChallengeResponse() {
        var response = filter.buildChallengeResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE))
                .contains("Test").contains("test-realm");
    }

    @Test
    void testBuildChallengeResponseForSpecificScheme() {
        var response = filter.buildChallengeResponse("Test");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testNoDefaultScheme() {
        var filterNoDefault = new AuthFilter(registry, context);
        var request = HttpRequest.of(HttpMethod.GET, "/");
        var result = filterNoDefault.filter(request);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testGetContext() {
        assertThat(filter.getContext()).isEqualTo(context);
    }

    @Test
    void testGetRegistry() {
        assertThat(filter.getRegistry()).isEqualTo(registry);
    }

    @Test
    void testBlankAuthorizationHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "   ");
        var result = filter.filter(request);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }
}
