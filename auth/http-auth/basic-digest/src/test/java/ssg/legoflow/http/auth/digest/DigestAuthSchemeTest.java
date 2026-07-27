package ssg.legoflow.http.auth.digest;

import ssg.legoflow.http.auth.*;
import ssg.legoflow.http.auth.basic.InMemoryUserStore;
import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DigestAuthSchemeTest {

    private DigestAuthScheme scheme;
    private NonceManager nonceManager;
    private AuthContext context;
    private InMemoryUserStore userStore;

    @BeforeEach
    void setUp() {
        nonceManager = new NonceManager();
        scheme = new DigestAuthScheme(nonceManager, "MD5", "auth");
        userStore = new InMemoryUserStore()
                .addUser("alice", "password", Set.of("admin"))
                .addUser("bob", "secret");
        context = new AuthContext("test-realm", userStore, null);
    }

    @Test
    void testSchemeName() {
        assertThat(scheme.schemeName()).isEqualTo("Digest");
    }

    @Test
    void testNoAuthHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testWrongSchemeHeader() {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    @Test
    void testSuccessfulDigestAuth() {
        String nonce = nonceManager.generateNonce();
        String nc = "00000001";
        String cnonce = "abc123";
        String uri = "/resource";
        String qop = "auth";

        String response = DigestAuthScheme.computeDigestResponse(
                "alice", "test-realm", "password", "GET", uri,
                nonce, nc, cnonce, qop, "MD5", null);

        var request = HttpRequest.of(HttpMethod.GET, uri);
        String authHeader = buildDigestAuthHeader("alice", "test-realm", nonce, uri,
                response, "MD5", cnonce, nc, qop, null);
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);

        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
        assertThat(((AuthResult.Success) result).principal().getName()).isEqualTo("alice");
    }

    @Test
    void testIncorrectDigestResponse() {
        String nonce = nonceManager.generateNonce();
        var request = HttpRequest.of(HttpMethod.GET, "/");
        String authHeader = buildDigestAuthHeader("alice", "test-realm", nonce, "/",
                "wrong-response", "MD5", "cnonce", "00000001", "auth", null);
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);

        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testUnknownUser() {
        String nonce = nonceManager.generateNonce();
        var request = HttpRequest.of(HttpMethod.GET, "/");
        String authHeader = buildDigestAuthHeader("unknown", "test-realm", nonce, "/",
                "anything", "MD5", "cnonce", "00000001", "auth", null);
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);

        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Failure.class);
    }

    @Test
    void testChallenge() {
        var response = HttpResponse.of(HttpStatus.UNAUTHORIZED);
        scheme.challenge(response, context);
        String wwwAuth = response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(wwwAuth).contains("Digest");
        assertThat(wwwAuth).contains("realm=\"test-realm\"");
        assertThat(wwwAuth).contains("algorithm=MD5");
        assertThat(wwwAuth).contains("qop=\"auth\"");
        assertThat(wwwAuth).contains("nonce=");
    }

    @Test
    void testComputeDigestResponseMd5() {
        String response = DigestAuthScheme.computeDigestResponse(
                "Mufasa", "testrealm@host.com", "Circle Of Life",
                "GET", "/dir/index.html",
                "dcd98b7102dd2f0e8b11d0f600bfb0c093", "00000001",
                "0a4f113b", "auth", "MD5", null);
        assertThat(response).isNotEmpty();
    }

    @Test
    void testComputeDigestResponseSha256() {
        String response = DigestAuthScheme.computeDigestResponse(
                "user", "realm", "pass", "GET", "/",
                "nonce", "00000001", "cnonce", "auth", "SHA-256", null);
        assertThat(response).isNotEmpty();
        // SHA-256 produces 64-char hex
        assertThat(response).hasSize(64);
    }

    @Test
    void testComputeDigestResponseAuthInt() {
        String response = DigestAuthScheme.computeDigestResponse(
                "user", "realm", "pass", "POST", "/api",
                "nonce", "00000001", "cnonce", "auth-int", "MD5", "{\"data\":1}");
        assertThat(response).isNotEmpty();
    }

    @Test
    void testComputeDigestResponseNoQop() {
        String response = DigestAuthScheme.computeDigestResponse(
                "user", "realm", "pass", "GET", "/",
                "nonce", null, null, null, "MD5", null);
        assertThat(response).isNotEmpty();
    }

    @Test
    void testHashMd5() {
        String hash = DigestAuthScheme.hash("hello", "MD5");
        assertThat(hash).hasSize(32);
        assertThat(hash).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    void testHashSha256() {
        String hash = DigestAuthScheme.hash("hello", "SHA-256");
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void testParseDigestParams() {
        var params = DigestAuthScheme.parseDigestParams(
                "username=\"alice\", realm=\"test\", nonce=\"abc\", uri=\"/\", response=\"xyz\"");
        assertThat(params).containsEntry("username", "alice");
        assertThat(params).containsEntry("realm", "test");
        assertThat(params).containsEntry("nonce", "abc");
    }

    @Test
    void testParseDigestParamsUnquoted() {
        var params = DigestAuthScheme.parseDigestParams("algorithm=MD5, nc=00000001");
        assertThat(params).containsEntry("algorithm", "MD5");
        assertThat(params).containsEntry("nc", "00000001");
    }

    @Test
    void testSha256Scheme() {
        var sha256Scheme = new DigestAuthScheme(nonceManager, "SHA-256", "auth");
        assertThat(sha256Scheme.getAlgorithm()).isEqualTo("SHA-256");
    }

    @Test
    void testGetQop() {
        assertThat(scheme.getQop()).isEqualTo("auth");
    }

    @Test
    void testGetNonceManager() {
        assertThat(scheme.getNonceManager()).isEqualTo(nonceManager);
    }

    // ---- MD5-sess algorithm tests (RFC 7616 §3.4.3) ----

    @Test
    void testMd5SessAlgorithm() {
        var sessScheme = new DigestAuthScheme(nonceManager, "MD5-sess", "auth");
        String nonce = nonceManager.generateNonce();
        String nc = "00000001";
        String cnonce = "abc123";
        String uri = "/resource";

        String response = DigestAuthScheme.computeDigestResponse(
                "alice", "test-realm", "password", "GET", uri,
                nonce, nc, cnonce, "auth", "MD5-sess", null);

        var request = HttpRequest.of(HttpMethod.GET, uri);
        String authHeader = buildDigestAuthHeader("alice", "test-realm", nonce, uri,
                response, "MD5-sess", cnonce, nc, "auth", null);
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);

        var result = sessScheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testMd5SessProducesDifferentHA1() {
        // MD5-sess HA1 = H(H(user:realm:pass):nonce:cnonce), must differ from MD5 HA1
        String ha1Standard = DigestAuthScheme.computeHA1("user", "realm", "pass", "nonce", "cnonce", "MD5");
        String ha1Sess = DigestAuthScheme.computeHA1("user", "realm", "pass", "nonce", "cnonce", "MD5-sess");
        assertThat(ha1Standard).isNotEqualTo(ha1Sess);
    }

    @Test
    void testBaseAlgorithm() {
        assertThat(DigestAuthScheme.baseAlgorithm("MD5")).isEqualTo("MD5");
        assertThat(DigestAuthScheme.baseAlgorithm("MD5-sess")).isEqualTo("MD5");
        assertThat(DigestAuthScheme.baseAlgorithm("SHA-256")).isEqualTo("SHA-256");
        assertThat(DigestAuthScheme.baseAlgorithm("SHA-256-sess")).isEqualTo("SHA-256");
        assertThat(DigestAuthScheme.baseAlgorithm(null)).isEqualTo("MD5");
    }

    @Test
    void testIsSessionAlgorithm() {
        assertThat(DigestAuthScheme.isSessionAlgorithm("MD5")).isFalse();
        assertThat(DigestAuthScheme.isSessionAlgorithm("MD5-sess")).isTrue();
        assertThat(DigestAuthScheme.isSessionAlgorithm("SHA-256")).isFalse();
        assertThat(DigestAuthScheme.isSessionAlgorithm("SHA-256-sess")).isTrue();
        assertThat(DigestAuthScheme.isSessionAlgorithm(null)).isFalse();
    }

    @Test
    void testSha256SessAlgorithm() {
        String response = DigestAuthScheme.computeDigestResponse(
                "user", "realm", "pass", "GET", "/",
                "nonce", "00000001", "cnonce", "auth", "SHA-256-sess", null);
        assertThat(response).isNotEmpty();
        assertThat(response).hasSize(64); // SHA-256 produces 64-char hex
    }

    // ---- Proxy authentication (407) tests ----

    @Test
    void testProxyModeChallenge() {
        var proxyScheme = new DigestAuthScheme(nonceManager, "MD5", "auth", true);
        var response = HttpResponse.of(HttpStatus.UNAUTHORIZED);
        proxyScheme.challenge(response, context);
        String proxyAuth = response.getHeaders().get("proxy-authenticate");
        assertThat(proxyAuth).isNotNull();
        assertThat(proxyAuth).contains("Digest");
        assertThat(proxyAuth).contains("realm=\"test-realm\"");
        // Should NOT have WWW-Authenticate
        assertThat(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    void testProxyModeExtractCredentials() {
        var proxyScheme = new DigestAuthScheme(nonceManager, "MD5", "auth", true);
        String nonce = nonceManager.generateNonce();
        String nc = "00000001";
        String cnonce = "abc123";
        String uri = "/resource";

        String digestResponse = DigestAuthScheme.computeDigestResponse(
                "alice", "test-realm", "password", "GET", uri,
                nonce, nc, cnonce, "auth", "MD5", null);

        var request = HttpRequest.of(HttpMethod.GET, uri);
        String authHeader = buildDigestAuthHeader("alice", "test-realm", nonce, uri,
                digestResponse, "MD5", cnonce, nc, "auth", null);
        request.getHeaders().set("proxy-authorization", authHeader);

        var result = proxyScheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    @Test
    void testProxyChallengeMethod() {
        var proxyScheme = new DigestAuthScheme(nonceManager, "MD5", "auth", true);
        var response = HttpResponse.of(HttpStatus.PROXY_AUTHENTICATION_REQUIRED);
        proxyScheme.proxyChallenge(response, context);
        String proxyAuth = response.getHeaders().get("proxy-authenticate");
        assertThat(proxyAuth).isNotNull();
        assertThat(proxyAuth).contains("Digest");
    }

    @Test
    void testIsProxyMode() {
        assertThat(scheme.isProxyMode()).isFalse();
        var proxyScheme = new DigestAuthScheme(nonceManager, "MD5", "auth", true);
        assertThat(proxyScheme.isProxyMode()).isTrue();
    }

    // ---- Authentication-Info response header tests (RFC 7616 §3.8) ----

    @Test
    void testAuthenticationInfoHeader() {
        String nonce = nonceManager.generateNonce();
        String nc = "00000001";
        String cnonce = "abc123";
        String uri = "/resource";
        String qop = "auth";

        String digestResponse = DigestAuthScheme.computeDigestResponse(
                "alice", "test-realm", "password", "GET", uri,
                nonce, nc, cnonce, qop, "MD5", null);

        var request = HttpRequest.of(HttpMethod.GET, uri);
        String authHeader = buildDigestAuthHeader("alice", "test-realm", nonce, uri,
                digestResponse, "MD5", cnonce, nc, qop, null);
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);

        var result = scheme.authenticate(request, context);
        assertThat(result).isInstanceOf(AuthResult.Success.class);

        var response = HttpResponse.of(HttpStatus.OK);
        scheme.addAuthenticationInfo(response, context);
        String authInfo = response.getHeaders().get("authentication-info");
        assertThat(authInfo).isNotNull();
        assertThat(authInfo).contains("rspauth=");
        assertThat(authInfo).contains("cnonce=\"abc123\"");
        assertThat(authInfo).contains("nc=00000001");
        assertThat(authInfo).contains("qop=auth");
    }

    @Test
    void testAuthenticationInfoRspAuthDiffersFromResponse() {
        String nonce = nonceManager.generateNonce();
        String nc = "00000001";
        String cnonce = "abc123";
        String uri = "/resource";

        String digestResponse = DigestAuthScheme.computeDigestResponse(
                "alice", "test-realm", "password", "GET", uri,
                nonce, nc, cnonce, "auth", "MD5", null);

        String rspauth = DigestAuthScheme.computeRspAuth(
                "alice", "test-realm", "password", uri,
                nonce, nc, cnonce, "auth", "MD5", null);

        // rspauth uses empty method, so it should differ from the response
        assertThat(rspauth).isNotEqualTo(digestResponse);
        assertThat(rspauth).hasSize(32); // MD5 hex
    }

    @Test
    void testAuthenticationInfoNotSetWithoutAuth() {
        var response = HttpResponse.of(HttpStatus.OK);
        scheme.addAuthenticationInfo(response, context);
        assertThat(response.getHeaders().get("authentication-info")).isNull();
    }

    @Test
    void testComputeRspAuth() {
        String rspauth = DigestAuthScheme.computeRspAuth(
                "user", "realm", "pass", "/",
                "nonce", "00000001", "cnonce", "auth", "MD5", null);
        assertThat(rspauth).isNotEmpty();
        assertThat(rspauth).hasSize(32);
    }

    @Test
    void testComputeRspAuthAuthInt() {
        String rspauth = DigestAuthScheme.computeRspAuth(
                "user", "realm", "pass", "/api",
                "nonce", "00000001", "cnonce", "auth-int", "MD5", "body");
        assertThat(rspauth).isNotEmpty();
    }

    @Test
    void testStaleNonce() {
        String nonce = "stale-nonce-not-in-manager";
        var request = HttpRequest.of(HttpMethod.GET, "/");
        String authHeader = buildDigestAuthHeader("alice", "test-realm", nonce, "/",
                "response", "MD5", "cnonce", "00000001", "auth", null);
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, authHeader);

        var result = scheme.authenticate(request, context);
        // Should return challenge for stale nonce
        assertThat(result).isInstanceOf(AuthResult.Challenge.class);
    }

    private String buildDigestAuthHeader(String username, String realm, String nonce, String uri,
                                          String response, String algorithm, String cnonce,
                                          String nc, String qop, String opaque) {
        var sb = new StringBuilder("Digest ");
        sb.append("username=\"").append(username).append("\", ");
        sb.append("realm=\"").append(realm).append("\", ");
        sb.append("nonce=\"").append(nonce).append("\", ");
        sb.append("uri=\"").append(uri).append("\", ");
        sb.append("response=\"").append(response).append("\", ");
        sb.append("algorithm=").append(algorithm).append(", ");
        sb.append("cnonce=\"").append(cnonce).append("\", ");
        sb.append("nc=").append(nc).append(", ");
        sb.append("qop=").append(qop);
        if (opaque != null) {
            sb.append(", opaque=\"").append(opaque).append("\"");
        }
        return sb.toString();
    }
}
