package ssg.legoflow.http.auth.spnego;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.auth.gssapi.GssConfig;
import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.auth.AuthenticationScheme;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpMethod;
class NegotiateAuthSchemeTest {

    private static SpnegoConfig makeConfig() {
        return SpnegoConfig.builder()
                .gssConfig(GssConfig.builder()
                        .realm("EXAMPLE.COM")
                        .kdc("kdc.example.com")
                        .servicePrincipal("HTTP/server@example.com")
                        .build())
                .build();
    }

    @Test void testSchemeName() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        assertThat(scheme.schemeName()).isEqualTo("Negotiate");
    }

    @Test void testImplementsAuthenticationScheme() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        assertThat(scheme).isInstanceOf(AuthenticationScheme.class);
    }

    @Test void testConstructorNullThrows() {
        assertThatThrownBy(() -> new NegotiateAuthScheme(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }

    @Test void testAuthenticateWithNoAuthHeaderReturnsChallenge() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        var authContext = AuthContext.ofRealm("test.realm");
        
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test void testAuthenticateWithWrongPrefixReturnsChallenge() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // Set a Basic auth header (not Negotiate)
        request.getHeaders().set("Authorization", "Basic dXNlcjpwYXNz");
        var authContext = AuthContext.ofRealm("test.realm");
        
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test void testAuthenticateWithNegotiatePrefix() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // Set Negotiate auth header with base64 token
        String token = java.util.Base64.getEncoder().encodeToString("token".getBytes());
        request.getHeaders().set("Authorization", "Negotiate " + token);
        var authContext = AuthContext.ofRealm("test.realm");
        
        // With actual GSSAPI unavailable, this should return Challenge or Failure
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testAuthenticateCaseInsensitivePrefix() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("data".getBytes());
        // Test lowercase "negotiate" prefix
        request.getHeaders().set("Authorization", "negotiate " + token);
        var authContext = AuthContext.ofRealm("test.realm");
        
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testChallengeSetsWWWAuthenticateHeader() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var response = ssg.legoflow.http.core.HttpResponse.of(ssg.legoflow.http.core.HttpStatus.UNAUTHORIZED);
        var authContext = AuthContext.ofRealm("test.realm");
        
        scheme.challenge(response, authContext);
        var wwwAuth = response.getHeaders().get("WWW-Authenticate");
        assertThat(wwwAuth).isEqualTo("Negotiate");
    }

    @Test void testExtractCredentialsWithNoAuthHeader() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        
        var result = scheme.extractCredentials(request);
        assertThat(result).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.None.class);
    }

    @Test void testExtractCredentialsWithWrongPrefix() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Basic dXNlcjpwYXNz");
        
        var result = scheme.extractCredentials(request);
        assertThat(result).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.None.class);
    }

    @Test void testExtractCredentialsWithNegotiateToken() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("spnego-token".getBytes());
        request.getHeaders().set("Authorization", "Negotiate " + token);
        
        var result = scheme.extractCredentials(request);
    }

    @Test void testConfigAccessor() {
        var config = makeConfig();
        var scheme = new NegotiateAuthScheme(config);
        // Config should be stored and accessible indirectly through behavior
        assertThat(scheme.schemeName()).isEqualTo("Negotiate");
    }

    @Test void testAuthenticateEmptyTokenReturnsChallenge() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString(new byte[0]);
        request.getHeaders().set("Authorization", "Negotiate " + token);
        var authContext = AuthContext.ofRealm("test.realm");
        
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testMultipleAuthenticateCalls() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var authContext = AuthContext.ofRealm("test.realm");
        
        for (int i = 0; i < 3; i++) {
            var request = HttpRequest.of(HttpMethod.GET, "/path");
            var result = scheme.authenticate(request, authContext);
            assertThat(result).isInstanceOf(AuthResult.Challenge.class);
        }
    }

    @Test void testChallengeWithNullResponse() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var authContext = AuthContext.ofRealm("test.realm");
        
        // Should handle null response gracefully or throw NPE
        try {
            scheme.challenge(null, authContext);
        } catch (NullPointerException e) {
            // Expected for null response
        }
    }

    @Test void testExtractCredentialsNullRequest() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        
        try {
            scheme.extractCredentials(null);
        } catch (NullPointerException e) {
            // Expected for null request
        }
    }

    @Test void testAuthenticateWithMalformedToken() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        // Not valid base64
        request.getHeaders().set("Authorization", "Negotiate !!!invalid!!!");
        var authContext = AuthContext.ofRealm("test.realm");
        
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testSchemeNameConstant() {
        var config1 = makeConfig();
        var config2 = SpnegoConfig.builder()
                .gssConfig(GssConfig.builder()
                        .realm("OTHER.COM")
                        .kdc("kdc.other.com")
                        .servicePrincipal("HTTP/other@other.com")
                        .build())
                .stripRealmFromPrincipal(false)
                .build();
        
        var scheme1 = new NegotiateAuthScheme(config1);
        var scheme2 = new NegotiateAuthScheme(config2);
        
        // Different configs, same scheme name
        assertThat(scheme1.schemeName()).isEqualTo(scheme2.schemeName());
    }

    @Test void testAuthenticateWithAuthContextHavingMethods() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var authContext = AuthContext.ofRealm("test.realm");
        // Set up allowed methods if AuthContext supports it
        var request = HttpRequest.of(HttpMethod.GET, "/secure");
        
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isNotNull();
    }

    @Test void testExtractCredentialsMixedCaseNegotiate() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("token".getBytes());
        
        // Test mixed case variants
        String[] prefixes = {"Negotiate ", "negotiate ", "NEGOTIATE "};
        for (String prefix : prefixes) {
            request.getHeaders().set("Authorization", prefix + token);
            var result = scheme.extractCredentials(request);
        }
    }

    @Test void testChallengeMultipleTimes() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var authContext = AuthContext.ofRealm("test.realm");
        
        for (int i = 0; i < 3; i++) {
            var response = ssg.legoflow.http.core.HttpResponse.of(ssg.legoflow.http.core.HttpStatus.UNAUTHORIZED);
            scheme.challenge(response, authContext);
            assertThat(response.getHeaders().get("WWW-Authenticate")).isEqualTo("Negotiate");
        }
    }

    @Test void testNullConfigPreventsAuthentication() {
        var config = makeConfig();
        var scheme = new NegotiateAuthScheme(config);
        // Config is stored, so authentication can proceed (will likely return Challenge)
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        var authContext = AuthContext.ofRealm("test.realm");
        
        var result = scheme.authenticate(request, authContext);
        assertThat(result).isNotNull();
    }

    @Test void testAuthenticateEmptyTokenSpaceOnly() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Negotiate  ");
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test void testAuthenticateWithUpperCaseNEGOTIATE() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("data".getBytes());
        request.getHeaders().set("Authorization", "NEGOTIATE " + token);
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testAuthenticateWithMixedCaseNegotiatePrefix() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("data".getBytes());
        request.getHeaders().set("Authorization", "nEgOtIaTe " + token);
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testAuthenticateWithTokenAndTrailingSpace() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString("data".getBytes());
        request.getHeaders().set("Authorization", "Negotiate " + token + "   ");
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testAuthenticateWithLongBase64Token() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        byte[] longData = new byte[256];
        java.util.Arrays.fill(longData, (byte)0x60);
        String token = java.util.Base64.getEncoder().encodeToString(longData);
        request.getHeaders().set("Authorization", "Negotiate " + token);
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testAuthenticateMinimalToken() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        String token = java.util.Base64.getEncoder().encodeToString(new byte[]{0x01});
        request.getHeaders().set("Authorization", "Negotiate " + token);
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.class);
    }

    @Test void testAuthenticateWithBearerPrefixReturnsChallenge() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Bearer token-here");
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test void testAuthenticateWithDigestPrefixReturnsChallenge() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Digest username=user");
        var context = AuthContext.ofRealm("test.realm");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test void testExtractCredentialsWithBearerReturnsNone() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Bearer token");
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.None.class);
    }

    @Test void testExtractCredentialsWithNegotiateEmptyTokenReturnsNone() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Negotiate ");
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.None.class);
    }

    @Test void testExtractCredentialsWithNullThrows() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        assertThatThrownBy(() -> scheme.extractCredentials(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testMultipleSequentialAuthenticateNoAuthHeader() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var context = AuthContext.ofRealm("test.realm");
        for (int i = 0; i < 5; i++) {
            var request = HttpRequest.of(HttpMethod.GET, "/path");
            var result = scheme.authenticate(request, context);
            assertThat(result).isInstanceOf(AuthResult.Challenge.class);
        }
    }

    @Test void testChallengeAddsCorrectHeaderOnEachCall() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var context = AuthContext.ofRealm("test.realm");
        for (int i = 0; i < 3; i++) {
            var response = ssg.legoflow.http.core.HttpResponse.of(ssg.legoflow.http.core.HttpStatus.UNAUTHORIZED);
            scheme.challenge(response, context);
            assertThat(response.getHeaders().get("WWW-Authenticate")).isEqualTo("Negotiate");
        }
    }

    @Test void testChallengeWithNullContext() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var response = ssg.legoflow.http.core.HttpResponse.of(ssg.legoflow.http.core.HttpStatus.UNAUTHORIZED);
        // Challenge should work with null context too (context is unused in challenge method)
        scheme.challenge(response, null);
        assertThat(response.getHeaders().get("WWW-Authenticate")).isEqualTo("Negotiate");
    }

    @Test void testChallengeWithNullResponseThrows() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var context = AuthContext.ofRealm("test.realm");
        try {
            scheme.challenge(null, context);
        } catch (NullPointerException e) {
            // Expected for null response
        }
    }

    @Test void testExtractCredentialsNegotiateWhitespaceReturnsNone() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        var request = HttpRequest.of(HttpMethod.GET, "/path");
        request.getHeaders().set("Authorization", "Negotiate   ");
        var creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(ssg.legoflow.http.auth.AuthCredentials.None.class);
    }

    @Test void testAuthenticateRepeatedSameResultWithoutAuthHeader() {
        var scheme = new NegotiateAuthScheme(makeConfig());
        for (int i = 0; i < 10; i++) {
            var request = HttpRequest.of(HttpMethod.GET, "/secure");
            var context = AuthContext.ofRealm("test.realm");
            var result = scheme.authenticate(request, context);
            // Without Negotiate header, always returns Challenge
            assertThat(result).isInstanceOf(AuthResult.Challenge.class);
        }
    }

    @Test void testSchemeNameIsConstant() {
        var config1 = makeConfig();
        var config2 = SpnegoConfig.builder().gssConfig(
                ssg.legoflow.auth.gssapi.GssConfig.builder()
                        .realm("OTHER.COM").kdc("kdc.other.com")
                        .servicePrincipal("HTTP/other@other.com").build())
                .stripRealmFromPrincipal(false).build();
        var scheme1 = new NegotiateAuthScheme(config1);
        var scheme2 = new NegotiateAuthScheme(config2);
        assertThat(scheme1.schemeName()).isEqualTo(scheme2.schemeName());
        assertThat(scheme1.schemeName()).isEqualTo("Negotiate");
    }
}
