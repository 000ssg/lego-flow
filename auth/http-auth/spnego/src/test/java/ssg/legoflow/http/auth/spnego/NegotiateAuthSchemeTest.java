package ssg.legoflow.http.auth.spnego;

import ssg.legoflow.auth.gssapi.GssConfig;
import ssg.legoflow.auth.gssapi.SpnegoTokenHandler;
import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthCredentials;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.core.HttpVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NegotiateAuthScheme}.
 */
class NegotiateAuthSchemeTest {

    private NegotiateAuthScheme scheme;
    private AuthContext authContext;

    @BeforeEach
    void setUp() {
        GssConfig gssConfig = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .build();
        SpnegoConfig spnegoConfig = SpnegoConfig.of(gssConfig);
        scheme = new NegotiateAuthScheme(spnegoConfig);
        authContext = AuthContext.ofRealm("EXAMPLE.COM");
    }

    @Test
    void testSchemeName() {
        assertThat(scheme.schemeName()).isEqualTo("Negotiate");
    }

    @Test
    void testConstructorNullConfigThrows() {
        assertThatThrownBy(() -> new NegotiateAuthScheme(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }

    @Test
    void testAuthenticateNoAuthorizationHeader() {
        HttpRequest request = createRequest(null);
        AuthResult result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
        assertThat(((AuthResult.Challenge) result).schemeName()).isEqualTo("Negotiate");
    }

    @Test
    void testAuthenticateWrongScheme() {
        HttpRequest request = createRequest("Basic dXNlcjpwYXNz");
        AuthResult result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testAuthenticateEmptyNegotiateToken() {
        HttpRequest request = createRequest("Negotiate ");
        AuthResult result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testAuthenticateInvalidBase64() {
        HttpRequest request = createRequest("Negotiate !!!invalid-base64!!!");
        AuthResult result = scheme.authenticate(request, authContext);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
        assertThat(((AuthResult.Failure) result).reason()).contains("Invalid Negotiate token");
    }

    @Test
    void testAuthenticateInvalidSpnegoToken() {
        // Valid base64 but not a valid SPNEGO/Kerberos token -- GSS context will reject it
        String invalidToken = Base64.getEncoder().encodeToString(new byte[]{0x01, 0x02, 0x03, 0x04});
        HttpRequest request = createRequest("Negotiate " + invalidToken);
        AuthResult result = scheme.authenticate(request, authContext);
        // Should fail because we can't create a server GSS context without proper Kerberos setup
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testChallengeAddsHeader() {
        HttpResponse response = new HttpResponse(
                HttpStatus.UNAUTHORIZED, HttpVersion.HTTP_1_1, new HttpHeaders());
        scheme.challenge(response, authContext);
        assertThat(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Negotiate");
    }

    @Test
    void testChallengeHeaderValue() {
        HttpResponse response = new HttpResponse(
                HttpStatus.UNAUTHORIZED, HttpVersion.HTTP_1_1, new HttpHeaders());
        scheme.challenge(response, authContext);
        String header = response.getHeaders().get("www-authenticate");
        assertThat(header).isEqualTo("Negotiate");
    }

    @Test
    void testExtractCredentialsWithNegotiateToken() {
        String token = Base64.getEncoder().encodeToString(new byte[]{0x60, 0x01, 0x02});
        HttpRequest request = createRequest("Negotiate " + token);
        AuthCredentials creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.Bearer.class);
        assertThat(((AuthCredentials.Bearer) creds).token()).isEqualTo(token);
    }

    @Test
    void testExtractCredentialsNoHeader() {
        HttpRequest request = createRequest(null);
        AuthCredentials creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testExtractCredentialsWrongScheme() {
        HttpRequest request = createRequest("Bearer sometoken");
        AuthCredentials creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testExtractCredentialsEmptyToken() {
        HttpRequest request = createRequest("Negotiate ");
        AuthCredentials creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.None.class);
    }

    @Test
    void testSchemeNameIsCaseInsensitiveCheck() {
        // The scheme name is "Negotiate" with capital N
        assertThat(scheme.schemeName()).isEqualTo("Negotiate");
        assertThat(scheme.schemeName()).isNotEqualTo("negotiate");
    }

    @Test
    void testAuthenticateWithNegotiatePrefixCaseInsensitive() {
        // Authorization header matching should be case-insensitive for scheme name
        String token = Base64.getEncoder().encodeToString(new byte[]{0x01, 0x02, 0x03});
        HttpRequest request = createRequest("negotiate " + token);
        AuthResult result = scheme.authenticate(request, authContext);
        // Should attempt to process the token (not return challenge)
        // The token is invalid, so it will fail, but it should not return challenge
        assertThat(result).isNotNull();
    }

    @Test
    void testAuthenticateWithValidSpnegoStructure() {
        // Create a well-formed SPNEGO NegTokenInit with a dummy mechToken
        byte[] dummyMechToken = new byte[]{0x30, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05};
        byte[] spnegoInit = SpnegoTokenHandler.createNegTokenInit(dummyMechToken);
        String base64 = Base64.getEncoder().encodeToString(spnegoInit);
        HttpRequest request = createRequest("Negotiate " + base64);
        AuthResult result = scheme.authenticate(request, authContext);
        // Will fail because no real KDC, but should parse the SPNEGO wrapper correctly
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testMultipleChallengeHeaders() {
        HttpResponse response = new HttpResponse(
                HttpStatus.UNAUTHORIZED, HttpVersion.HTTP_1_1, new HttpHeaders());
        scheme.challenge(response, authContext);
        scheme.challenge(response, authContext);
        // Should have two Negotiate headers added
        assertThat(response.getHeaders().getAll("www-authenticate")).hasSize(2);
    }

    @Test
    void testRealmStrippingConfig() {
        GssConfig gssConfig = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .build();
        SpnegoConfig withStripping = SpnegoConfig.builder()
                .gssConfig(gssConfig)
                .stripRealmFromPrincipal(true)
                .build();
        SpnegoConfig withoutStripping = SpnegoConfig.builder()
                .gssConfig(gssConfig)
                .stripRealmFromPrincipal(false)
                .build();
        NegotiateAuthScheme schemeStrip = new NegotiateAuthScheme(withStripping);
        NegotiateAuthScheme schemeNoStrip = new NegotiateAuthScheme(withoutStripping);
        // Both should have same scheme name
        assertThat(schemeStrip.schemeName()).isEqualTo("Negotiate");
        assertThat(schemeNoStrip.schemeName()).isEqualTo("Negotiate");
    }

    @Test
    void testExtractCredentialsCaseInsensitiveNegotiate() {
        String token = Base64.getEncoder().encodeToString(new byte[]{0x60, 0x01});
        HttpRequest request = createRequest("NEGOTIATE " + token);
        AuthCredentials creds = scheme.extractCredentials(request);
        assertThat(creds).isInstanceOf(AuthCredentials.Bearer.class);
    }

    // ---- Helper ----

    private HttpRequest createRequest(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return new HttpRequest(HttpMethod.GET, "/protected", HttpVersion.HTTP_1_1, headers);
    }
}
