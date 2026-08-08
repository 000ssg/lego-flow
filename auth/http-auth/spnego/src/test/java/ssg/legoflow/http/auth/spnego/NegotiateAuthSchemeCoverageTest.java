package ssg.legoflow.http.auth.spnego;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.auth.gssapi.GssConfig;
import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpHeaders;

/**
 * Coverage tests for the remaining branches in NegotiateAuthScheme.
 */
class NegotiateAuthSchemeCoverageTest {

    private static SpnegoConfig makeConfig() {
        return SpnegoConfig.builder()
                .gssConfig(GssConfig.builder()
                        .realm("EXAMPLE.COM")
                        .kdc("kdc.example.com")
                        .servicePrincipal("HTTP/server@example.com")
                        .build())
                .build();
    }

    @Test void testAuthenticateInvalidBase64Token() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // Invalid base64 data that will trigger decode failure
        request.getHeaders().set("Authorization", "Negotiate invalid!base64@#");
        
        var result = scheme.authenticate(request, AuthContext.ofRealm("test.realm"));
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test void testAuthenticateWithStripRealmTrue() {
        var config = SpnegoConfig.builder()
                .gssConfig(GssConfig.builder()
                        .realm("EXAMPLE.COM")
                        .kdc("kdc.example.com")
                        .servicePrincipal("HTTP/server@example.com")
                        .build())
                .stripRealmFromPrincipal(true)
                .build();
        var scheme = new NegotiateAuthScheme(config);
        
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("token".getBytes());
        request.getHeaders().set("Authorization", "Negotiate " + token);
        
        var result = scheme.authenticate(request, AuthContext.ofRealm("test.realm"));
        assertThat(result).isInstanceOfAny(AuthResult.Challenge.class, AuthResult.Failure.class);
    }

    @Test void testAuthenticateWithJustSpacesToken() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Negotiate   ");
        
        var result = scheme.authenticate(request, AuthContext.ofRealm("test.realm"));
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test void testExtractCredentialsWithEmptyToken() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Negotiate  ");
        
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.None.class);
    }

    @Test void testExtractCredentialsWithWhitespaceOnlyToken() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Negotiate\t  \t");
        
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.None.class);
    }

    @Test void testExtractCredentialsWithValidToken() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("mytoken".getBytes());
        request.getHeaders().set("Authorization", "Negotiate " + token);
        
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.Bearer.class);
    }

    @Test void testAuthenticateMixedCasePrefix() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("token".getBytes());
        request.getHeaders().set("Authorization", "nEGotIaTe " + token);
        
        var result = scheme.authenticate(request, AuthContext.ofRealm("test.realm"));
        assertThat(result).isInstanceOfAny(AuthResult.Challenge.class, AuthResult.Failure.class);
    }

    @Test void testAuthenticateWithGssUnavailableReturnsChallengeOrFailure() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("somegssdata".getBytes());
        request.getHeaders().set("Authorization", "Negotiate " + token);
        
        var result = scheme.authenticate(request, AuthContext.ofRealm("test.realm"));
        assertThat(result).isInstanceOfAny(AuthResult.Challenge.class, AuthResult.Failure.class);
    }

    @Test void testSpnegoConfigStripRealmExplicitFalse() {
        var config = SpnegoConfig.builder().gssConfig(
                GssConfig.builder().realm("EXAMPLE.COM")
                        .kdc("kdc.example.com")
                        .servicePrincipal("HTTP/server@example.com").build())
                .stripRealmFromPrincipal(false)
                .build();
        assertThat(config.stripRealmFromPrincipal()).isFalse();
    }

    @Test void testNullAuthHeaderReturnsChallenge() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // No Authorization header at all
        
        var result = scheme.authenticate(request, AuthContext.ofRealm("test.realm"));
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }
}
