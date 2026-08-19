package ssg.legoflow.http.auth.oauth2.demo;

import ssg.legoflow.http.auth.oauth2.OAuth2Config;
import ssg.legoflow.http.auth.oauth2.OAuth2Error;
import ssg.legoflow.http.auth.oauth2.OAuth2TokenResponse;
import ssg.legoflow.http.auth.oauth2.PkceChallenge;
import ssg.legoflow.http.auth.oauth2.server.AuthorizationCodeStore;
import ssg.legoflow.http.auth.oauth2.server.OAuth2ClientRegistry;
import ssg.legoflow.http.auth.oauth2.server.TokenStore;
import ssg.legoflow.http.auth.oidc.OidcDiscovery;
import ssg.legoflow.http.auth.oidc.UserInfo;
import ssg.legoflow.http.auth.provider.GoogleOAuth;
import ssg.legoflow.http.auth.provider.GitHubOAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
/**
 * Comprehensive demo of all OAuth 2.0 / OpenID Connect module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>OAuth2Config — builder pattern with all endpoint configurations</li>
 *   <li>PKCE (RFC 7636) — S256 and plain code challenge generation and verification</li>
 *   <li>Authorization server — client registry, token store, authorization code store</li>
 *   <li>Token response — JSON serialization and deserialization</li>
 *   <li>OAuth providers — pre-configured Google, GitHub templates</li>
 *   <li>OIDC discovery — metadata parsing, ID token, UserInfo</li>
 *   <li>OAuth2Error — standard error response handling</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoOAuthAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoOAuthAll.class);

    /** Set to {@code true} to test with external OAuth providers. */
    public static boolean USE_EXTERNAL = false;

    private DemoOAuthAll() {}

    /**
     * Results from running the full OAuth demo.
     *
     * @param oauthConfig          true if OAuth2Config builder works correctly
     * @param pkceChallenge        true if PKCE generation and verification works
     * @param authorizationServer  true if authorization server components work
     * @param tokenResponse        true if token response serialization works
     * @param oauthProviders       true if pre-configured provider templates work
     * @param oidcDiscovery        true if OIDC discovery metadata works
     * @param oauthError           true if error response handling works
     * @since 0.1.0
     */
    public record Results(
            boolean oauthConfig,
            boolean pkceChallenge,
            boolean authorizationServer,
            boolean tokenResponse,
            boolean oauthProviders,
            boolean oidcDiscovery,
            boolean oauthError
    ) {}

    /**
     * Runs the comprehensive demo covering all OAuth features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean config = demoOAuthConfig();
        boolean pkce = demoPkceChallenge();
        boolean server = demoAuthorizationServer();
        boolean token = demoTokenResponse();
        boolean providers = demoOAuthProviders();
        boolean oidc = demoOidcDiscovery();
        boolean error = demoOAuthError();

        return new Results(config, pkce, server, token, providers, oidc, error);
    }

    // ======================== 1. OAUTH2 CONFIG ===============================

    /**
     * Demonstrates OAuth2Config builder with all endpoint configurations.
     *
     * @return true if configuration is built correctly
     * @since 0.1.0
     */
    static boolean demoOAuthConfig() {
        LOG.info("=== 1. OAuth2Config Builder ===");

        var config = OAuth2Config.builder()
                .clientId("my-client-id")
                .clientSecret("my-client-secret")
                .redirectUri("http://localhost:8080/callback")
                .scopes(Set.of("openid", "profile", "email"))
                .authorizationEndpoint("https://auth.example.com/authorize")
                .tokenEndpoint("https://auth.example.com/token")
                .revocationEndpoint("https://auth.example.com/revoke")
                .userInfoEndpoint("https://auth.example.com/userinfo")
                .build();

        boolean clientIdOk = "my-client-id".equals(config.getClientId());
        boolean secretOk = "my-client-secret".equals(config.getClientSecret());
        boolean redirectOk = "http://localhost:8080/callback".equals(config.getRedirectUri());
        boolean scopesOk = config.getScopes().size() == 3
                && config.getScopes().contains("openid");
        boolean authEndpoint = config.getAuthorizationEndpoint().contains("authorize");
        boolean tokenEndpoint = config.getTokenEndpoint().contains("token");

        LOG.info("ClientId: {}, scopes: {}, endpoints: {}", clientIdOk, scopesOk, authEndpoint && tokenEndpoint);
        return clientIdOk && secretOk && redirectOk && scopesOk && authEndpoint && tokenEndpoint;
    }

    // ======================== 2. PKCE CHALLENGE ==============================

    /**
     * Demonstrates PKCE (Proof Key for Code Exchange) with S256 and plain
     * challenge methods, including generation and verification.
     *
     * @return true if PKCE operations work correctly
     * @since 0.1.0
     */
    static boolean demoPkceChallenge() {
        LOG.info("=== 2. PKCE (RFC 7636) ===");

        // S256 challenge
        PkceChallenge s256 = PkceChallenge.generateS256();
        String verifier = s256.getCodeVerifier();
        String challenge = s256.getCodeChallenge();
        LOG.info("S256 verifier: {} chars, challenge: {} chars", verifier.length(), challenge.length());

        boolean verifierLength = verifier.length() >= 43 && verifier.length() <= 128;
        boolean challengeNotEmpty = !challenge.isEmpty();
        boolean methodOk = "S256".equals(s256.getChallengeMethod());

        // Verify S256
        boolean s256Verified = PkceChallenge.verify(verifier, challenge, "S256");
        LOG.info("S256 verified: {}", s256Verified);

        // Wrong verifier
        boolean wrongRejected = !PkceChallenge.verify("wrong-verifier", challenge, "S256");
        LOG.info("Wrong verifier rejected: {}", wrongRejected);

        // Plain challenge
        PkceChallenge plain = PkceChallenge.generatePlain();
        boolean plainVerified = PkceChallenge.verify(
                plain.getCodeVerifier(), plain.getCodeChallenge(), "plain");
        boolean plainMatch = plain.getCodeVerifier().equals(plain.getCodeChallenge());
        LOG.info("Plain verified: {}, verifier==challenge: {}", plainVerified, plainMatch);

        // Custom length
        PkceChallenge custom = PkceChallenge.generateS256(128);
        boolean customLength = custom.getCodeVerifier().length() == 128;

        return verifierLength && challengeNotEmpty && methodOk && s256Verified
                && wrongRejected && plainVerified && plainMatch && customLength;
    }

    // ======================== 3. AUTHORIZATION SERVER =========================

    /**
     * Demonstrates OAuth 2.0 authorization server components: client registry,
     * token store with issuance and validation, and authorization code store.
     *
     * @return true if all server components work correctly
     * @since 0.1.0
     */
    static boolean demoAuthorizationServer() {
        LOG.info("=== 3. Authorization Server Components ===");

        // Client registry
        var registry = new OAuth2ClientRegistry();
        registry.register(new OAuth2ClientRegistry.RegisteredClient(
                "web-app", "web-secret",
                Set.of("http://localhost:8080/callback"),
                Set.of("openid", "profile"),
                Set.of("authorization_code", "refresh_token"),
                true));

        boolean clientFound = registry.get("web-app").isPresent();
        boolean clientAuth = registry.authenticate("web-app", "web-secret").isPresent();
        boolean wrongSecret = registry.authenticate("web-app", "wrong").isEmpty();
        boolean redirectAllowed = registry.isRedirectUriAllowed("web-app",
                "http://localhost:8080/callback");
        boolean redirectBlocked = !registry.isRedirectUriAllowed("web-app",
                "http://evil.com/callback");
        LOG.info("Client: found={}, auth={}, redirect={}", clientFound, clientAuth, redirectAllowed);

        // Token store
        var tokenStore = new TokenStore();
        var accessToken = tokenStore.issueAccessToken("web-app", "alice", Set.of("openid"));
        boolean tokenIssued = accessToken.token() != null && !accessToken.token().isEmpty();
        boolean tokenValid = tokenStore.validateAccessToken(accessToken.token()).isPresent();
        LOG.info("Token issued: {}, valid: {}", tokenIssued, tokenValid);

        // Refresh token
        var refreshToken = tokenStore.issueRefreshToken("web-app", "alice", Set.of("openid"));
        boolean refreshValid = tokenStore.validateRefreshToken(refreshToken.token()).isPresent();

        // Revoke
        boolean revoked = tokenStore.revokeAccessToken(accessToken.token());
        boolean afterRevoke = tokenStore.validateAccessToken(accessToken.token()).isEmpty();
        LOG.info("Revoked: {}, after revoke invalid: {}", revoked, afterRevoke);

        // Authorization code store
        var codeStore = new AuthorizationCodeStore();
        var authCode = codeStore.generate("web-app", "http://localhost:8080/callback",
                Set.of("openid"), "alice", null, null);
        String code = authCode.code();
        boolean codeIssued = code != null && !code.isEmpty();
        var codeData = codeStore.consume(code);
        boolean codeConsumed = codeData.isPresent();
        boolean codeReused = codeStore.consume(code).isEmpty(); // single-use
        LOG.info("Code: issued={}, consumed={}, reuse blocked={}", codeIssued, codeConsumed, codeReused);

        return clientFound && clientAuth && wrongSecret && redirectAllowed && redirectBlocked
                && tokenIssued && tokenValid && refreshValid && revoked && afterRevoke
                && codeIssued && codeConsumed && codeReused;
    }

    // ======================== 4. TOKEN RESPONSE ==============================

    /**
     * Demonstrates OAuth2TokenResponse JSON serialization, deserialization,
     * and expiration checking.
     *
     * @return true if token response operations work correctly
     * @since 0.1.0
     */
    static boolean demoTokenResponse() {
        LOG.info("=== 4. Token Response ===");

        var response = new OAuth2TokenResponse(
                "access-token-abc123", "Bearer", 3600, "refresh-token-xyz789", "openid profile");

        // Serialize to JSON
        String json = response.toJson();
        LOG.info("Token response JSON: {}", json);
        boolean hasAccessToken = json.contains("\"access_token\":\"access-token-abc123\"");
        boolean hasTokenType = json.contains("\"token_type\":\"Bearer\"");
        boolean hasExpiresIn = json.contains("\"expires_in\":3600");
        boolean hasRefresh = json.contains("\"refresh_token\":\"refresh-token-xyz789\"");

        // Deserialize from JSON
        OAuth2TokenResponse parsed = OAuth2TokenResponse.fromJson(json);
        boolean parsedOk = "access-token-abc123".equals(parsed.accessToken())
                && "Bearer".equals(parsed.tokenType())
                && parsed.expiresIn() == 3600
                && "refresh-token-xyz789".equals(parsed.refreshToken());
        LOG.info("Parsed OK: {}", parsedOk);

        // Expiration
        boolean notExpired = !response.isExpired();
        boolean expiresAtFuture = response.expiresAt().isAfter(response.issuedAt());
        LOG.info("Not expired: {}, expiresAt in future: {}", notExpired, expiresAtFuture);

        return hasAccessToken && hasTokenType && hasExpiresIn && hasRefresh
                && parsedOk && notExpired && expiresAtFuture;
    }

    // ======================== 5. OAUTH PROVIDERS =============================

    /**
     * Demonstrates pre-configured OAuth provider templates for Google and GitHub.
     *
     * @return true if provider configurations are valid
     * @since 0.1.0
     */
    static boolean demoOAuthProviders() {
        LOG.info("=== 5. OAuth Providers ===");

        // Google
        var google = new GoogleOAuth();
        var googleConfig = google.buildConfig("google-client-id", "google-secret",
                "http://localhost:8080/callback");
        boolean googleAuth = googleConfig.getAuthorizationEndpoint()
                .contains("accounts.google.com");
        boolean googleToken = googleConfig.getTokenEndpoint()
                .contains("oauth2.googleapis.com");
        boolean googleScopes = googleConfig.getScopes().contains("openid");
        boolean googleName = "Google".equals(google.getName());
        LOG.info("Google: auth={}, token={}, scopes={}", googleAuth, googleToken, googleScopes);

        // GitHub
        var github = new GitHubOAuth();
        var githubConfig = github.buildConfig("github-client-id", "github-secret",
                "http://localhost:8080/callback");
        boolean githubAuth = githubConfig.getAuthorizationEndpoint()
                .contains("github.com");
        boolean githubToken = githubConfig.getTokenEndpoint()
                .contains("github.com");
        boolean githubName = "GitHub".equals(github.getName());
        LOG.info("GitHub: auth={}, token={}", githubAuth, githubToken);

        return googleAuth && googleToken && googleScopes && googleName
                && githubAuth && githubToken && githubName;
    }

    // ======================== 6. OIDC DISCOVERY ==============================

    /**
     * Demonstrates OpenID Connect discovery metadata, ID token claims,
     * and UserInfo response parsing.
     *
     * @return true if OIDC components work correctly
     * @since 0.1.0
     */
    static boolean demoOidcDiscovery() {
        LOG.info("=== 6. OIDC Discovery ===");

        // OidcDiscovery metadata
        var discovery = new OidcDiscovery("https://auth.example.com",
                "https://auth.example.com/authorize",
                "https://auth.example.com/token",
                "https://auth.example.com/userinfo",
                "https://auth.example.com/.well-known/jwks.json",
                null, null, null);

        boolean issuerOk = "https://auth.example.com".equals(discovery.getIssuer());
        boolean authEndpoint = discovery.getAuthorizationEndpoint().contains("authorize");
        boolean jwksOk = discovery.getJwksUri().contains("jwks.json");
        LOG.info("Discovery: issuer={}, auth={}, jwks={}", issuerOk, authEndpoint, jwksOk);

        // Discovery URL construction
        String url = OidcDiscovery.discoveryUrl("https://auth.example.com");
        boolean urlOk = url.endsWith("/.well-known/openid-configuration");
        LOG.info("Discovery URL: {} (correct={})", url, urlOk);

        // JSON serialization
        String json = discovery.toJson();
        boolean jsonHasIssuer = json.contains("\"issuer\":\"https://auth.example.com\"");
        LOG.info("Discovery JSON: {} chars", json.length());

        // UserInfo
        var userInfo = new UserInfo(java.util.Map.of(
                "sub", "alice-subject",
                "email", "alice@example.com",
                "name", "Alice Smith",
                "given_name", "Alice",
                "family_name", "Smith"));
        boolean uiSubject = "alice-subject".equals(userInfo.getSubject());
        boolean uiEmail = "alice@example.com".equals(userInfo.getEmail());
        boolean uiName = "Alice Smith".equals(userInfo.getName());
        LOG.info("UserInfo: sub={}, email={}, name={}", uiSubject, uiEmail, uiName);

        return issuerOk && authEndpoint && jwksOk && urlOk && jsonHasIssuer
                && uiSubject && uiEmail && uiName;
    }

    // ======================== 7. OAUTH2 ERROR ================================

    /**
     * Demonstrates OAuth 2.0 standard error response handling.
     *
     * @return true if error handling works correctly
     * @since 0.1.0
     */
    static boolean demoOAuthError() {
        LOG.info("=== 7. OAuth2Error ===");

        boolean invalidClient = OAuth2Error.INVALID_CLIENT.equals("invalid_client");
        boolean invalidGrant = OAuth2Error.INVALID_GRANT.equals("invalid_grant");
        boolean invalidRequest = OAuth2Error.INVALID_REQUEST.equals("invalid_request");
        boolean invalidScope = OAuth2Error.INVALID_SCOPE.equals("invalid_scope");
        boolean unauthorizedClient = OAuth2Error.UNAUTHORIZED_CLIENT.equals("unauthorized_client");
        boolean unsupportedGrant = OAuth2Error.UNSUPPORTED_GRANT_TYPE.equals("unsupported_grant_type");

        LOG.info("Error codes: client={}, grant={}, request={}, scope={}",
                invalidClient, invalidGrant, invalidRequest, invalidScope);

        return invalidClient && invalidGrant && invalidRequest && invalidScope
                && unauthorizedClient && unsupportedGrant;
    }
}
