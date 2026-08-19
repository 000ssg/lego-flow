package ssg.legoflow.http.auth.oauth2.server;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.oauth2.*;
import ssg.legoflow.http.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
/**
 * OAuth 2.0 authorization server implementing /authorize, /token, and /revoke endpoints.
 * Supports authorization code, client credentials, password, and refresh token grants.
 *
 * @since 0.1.0
 */
public class OAuth2AuthorizationServer {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2AuthorizationServer.class);

    private final OAuth2ClientRegistry clientRegistry;
    private final AuthorizationCodeStore codeStore;
    private final TokenStore tokenStore;
    private final String issuer;

    /**
     * Creates an authorization server.
     *
     * @param clientRegistry the client registry
     * @param codeStore      the authorization code store
     * @param tokenStore     the token store
     * @param issuer         the issuer identifier
     * @since 0.1.0
     */
    public OAuth2AuthorizationServer(OAuth2ClientRegistry clientRegistry,
                                     AuthorizationCodeStore codeStore,
                                     TokenStore tokenStore,
                                     String issuer) {
        this.clientRegistry = Objects.requireNonNull(clientRegistry);
        this.codeStore = Objects.requireNonNull(codeStore);
        this.tokenStore = Objects.requireNonNull(tokenStore);
        this.issuer = issuer;
    }

    /**
     * Handles an authorization request (GET /authorize).
     *
     * @param request   the HTTP request
     * @param principal the authenticated user (must be authenticated before calling this)
     * @return the HTTP response (redirect with authorization code)
     * @since 0.1.0
     */
    public HttpResponse handleAuthorize(HttpRequest request, AuthPrincipal principal) {
        var params = request.getQueryParams();
        String responseType = params.get("response_type");
        String clientId = params.get("client_id");
        String redirectUri = params.get("redirect_uri");
        String scope = params.get("scope");
        String state = params.get("state");
        String codeChallenge = params.get("code_challenge");
        String challengeMethod = params.getOrDefault("code_challenge_method", "plain");

        // Validate client
        var client = clientRegistry.get(clientId);
        if (client.isEmpty()) {
            return errorResponse(OAuth2Error.INVALID_CLIENT, "Unknown client");
        }

        // Validate redirect URI
        if (redirectUri != null && !clientRegistry.isRedirectUriAllowed(clientId, redirectUri)) {
            return errorResponse(OAuth2Error.INVALID_REQUEST, "Invalid redirect_uri");
        }

        // Support code, token, and hybrid response types
        if (!"code".equals(responseType)) {
            // Delegate to extended handler for implicit and hybrid flows
            return handleAuthorizeExtended(request, principal);
        }

        // Parse scopes
        Set<String> scopes = scope != null ? Set.of(scope.split(" ")) : Set.of();

        // Generate authorization code
        var authCode = codeStore.generate(clientId, redirectUri, scopes,
                principal.getName(), codeChallenge, challengeMethod);

        // Build redirect URL
        String redirect = redirectUri + "?code=" + encode(authCode.code());
        if (state != null) {
            redirect += "&state=" + encode(state);
        }

        LOG.info("Issued authorization code for client {} and user {}", clientId, principal.getName());

        var response = HttpResponse.of(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, redirect);
        return response;
    }

    /**
     * Handles a token request (POST /token).
     *
     * @param request the HTTP request with form-encoded body
     * @return the HTTP response with token or error
     * @since 0.1.0
     */
    public HttpResponse handleToken(HttpRequest request) {
        Map<String, String> params = parseFormBody(request.getBodyAsString());
        String grantType = params.get("grant_type");

        if (grantType == null) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing grant_type");
        }

        return switch (grantType) {
            case "authorization_code" -> handleAuthorizationCodeGrant(params);
            case "client_credentials" -> handleClientCredentialsGrant(params);
            case "password" -> handlePasswordGrant(params);
            case "refresh_token" -> handleRefreshTokenGrant(params);
            default -> tokenError(OAuth2Error.UNSUPPORTED_GRANT_TYPE,
                    "Unsupported grant type: " + grantType);
        };
    }

    /**
     * Handles a revocation request (POST /revoke).
     *
     * @param request the HTTP request
     * @return the HTTP response
     * @since 0.1.0
     */
    public HttpResponse handleRevoke(HttpRequest request) {
        Map<String, String> params = parseFormBody(request.getBodyAsString());
        String token = params.get("token");
        String tokenTypeHint = params.get("token_type_hint");

        if (token == null) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing token");
        }

        // Try to revoke based on hint, or try both
        boolean revoked = false;
        if ("refresh_token".equals(tokenTypeHint)) {
            revoked = tokenStore.revokeRefreshToken(token);
        } else if ("access_token".equals(tokenTypeHint)) {
            revoked = tokenStore.revokeAccessToken(token);
        }

        if (!revoked) {
            // Try both
            revoked = tokenStore.revokeAccessToken(token) || tokenStore.revokeRefreshToken(token);
        }

        LOG.debug("Token revocation: {}", revoked ? "success" : "token not found");
        return HttpResponse.of(HttpStatus.OK);
    }

    // ---- Grant type handlers ----

    private HttpResponse handleAuthorizationCodeGrant(Map<String, String> params) {
        String code = params.get("code");
        String redirectUri = params.get("redirect_uri");
        String clientId = params.get("client_id");
        String clientSecret = params.get("client_secret");
        String codeVerifier = params.get("code_verifier");

        if (code == null || clientId == null) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing required parameters");
        }

        // Consume the authorization code
        var authCode = codeStore.consume(code);
        if (authCode.isEmpty()) {
            return tokenError(OAuth2Error.INVALID_GRANT, "Invalid or expired authorization code");
        }

        var ac = authCode.get();

        // Verify client
        if (!ac.clientId().equals(clientId)) {
            return tokenError(OAuth2Error.INVALID_GRANT, "Client ID mismatch");
        }

        // Verify redirect URI
        if (ac.redirectUri() != null && !ac.redirectUri().equals(redirectUri)) {
            return tokenError(OAuth2Error.INVALID_GRANT, "Redirect URI mismatch");
        }

        // Verify PKCE
        if (ac.codeChallenge() != null) {
            if (codeVerifier == null) {
                return tokenError(OAuth2Error.INVALID_GRANT, "Missing code_verifier");
            }
            if (!PkceChallenge.verify(codeVerifier, ac.codeChallenge(), ac.challengeMethod())) {
                return tokenError(OAuth2Error.INVALID_GRANT, "PKCE verification failed");
            }
        }

        // Authenticate confidential client
        var client = clientRegistry.get(clientId);
        if (client.isPresent() && client.get().confidential()) {
            if (clientRegistry.authenticate(clientId, clientSecret).isEmpty()) {
                return tokenError(OAuth2Error.INVALID_CLIENT, "Client authentication failed");
            }
        }

        // Issue tokens
        return issueTokens(clientId, ac.subject(), ac.scopes());
    }

    private HttpResponse handleClientCredentialsGrant(Map<String, String> params) {
        String clientId = params.get("client_id");
        String clientSecret = params.get("client_secret");

        if (clientId == null || clientSecret == null) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing client credentials");
        }

        var client = clientRegistry.authenticate(clientId, clientSecret);
        if (client.isEmpty()) {
            return tokenError(OAuth2Error.INVALID_CLIENT, "Client authentication failed");
        }

        String scope = params.get("scope");
        Set<String> scopes = scope != null ? Set.of(scope.split(" ")) : Set.of();

        return issueTokens(clientId, clientId, scopes);
    }

    private HttpResponse handlePasswordGrant(Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        String clientId = params.get("client_id");

        if (username == null || password == null || clientId == null) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing required parameters");
        }

        // Client validation only (user validation would be delegated to a user store)
        var client = clientRegistry.get(clientId);
        if (client.isEmpty()) {
            return tokenError(OAuth2Error.INVALID_CLIENT, "Unknown client");
        }

        String scope = params.get("scope");
        Set<String> scopes = scope != null ? Set.of(scope.split(" ")) : Set.of();

        return issueTokens(clientId, username, scopes);
    }

    private HttpResponse handleRefreshTokenGrant(Map<String, String> params) {
        String refreshToken = params.get("refresh_token");
        String clientId = params.get("client_id");

        if (refreshToken == null) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing refresh_token");
        }

        var stored = tokenStore.validateRefreshToken(refreshToken);
        if (stored.isEmpty()) {
            return tokenError(OAuth2Error.INVALID_GRANT, "Invalid or expired refresh token");
        }

        // Revoke old refresh token
        tokenStore.revokeRefreshToken(refreshToken);

        return issueTokens(stored.get().clientId(), stored.get().subject(), stored.get().scopes());
    }

    // ---- Helpers ----

    private HttpResponse issueTokens(String clientId, String subject, Set<String> scopes) {
        var accessToken = tokenStore.issueAccessToken(clientId, subject, scopes);
        var refreshToken = tokenStore.issueRefreshToken(clientId, subject, scopes);

        long expiresIn = tokenStore.getAccessTokenLifetime().toSeconds();
        String scopeStr = scopes.isEmpty() ? null : String.join(" ", scopes);
        var tokenResponse = new OAuth2TokenResponse(
                accessToken.token(), "Bearer", expiresIn, refreshToken.token(), scopeStr);

        LOG.info("Issued tokens for client {} subject {}", clientId, subject);

        var response = HttpResponse.of(HttpStatus.OK);
        byte[] body = tokenResponse.toJson().getBytes(StandardCharsets.UTF_8);
        response.setBody(ByteBuffer.wrap(body));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        response.getHeaders().set("cache-control", "no-store");
        response.getHeaders().set("pragma", "no-cache");
        return response;
    }

    private HttpResponse tokenError(String error, String description) {
        var oauthError = new OAuth2Error(error, description);
        var response = HttpResponse.of(HttpStatus.BAD_REQUEST);
        byte[] body = oauthError.toJson().getBytes(StandardCharsets.UTF_8);
        response.setBody(ByteBuffer.wrap(body));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        return response;
    }

    private HttpResponse errorResponse(String error, String description) {
        return tokenError(error, description);
    }

    private HttpResponse redirectError(String redirectUri, String error, String description, String state) {
        if (redirectUri == null) {
            return tokenError(error, description);
        }
        String redirect = redirectUri + "?error=" + encode(error);
        if (description != null) {
            redirect += "&error_description=" + encode(description);
        }
        if (state != null) {
            redirect += "&state=" + encode(state);
        }
        var response = HttpResponse.of(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, redirect);
        return response;
    }

    private static Map<String, String> parseFormBody(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        if (body == null || body.isBlank()) return params;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ---- Token Introspection (RFC 7662) ----

    /**
     * Handles a token introspection request (POST /introspect) per RFC 7662.
     * Returns a JSON object with {@code active}, {@code scope}, {@code client_id},
     * and {@code exp} fields.
     *
     * @param request the HTTP request with form-encoded body containing "token" parameter
     * @return the HTTP response with introspection result
     * @since 0.1.0
     */
    public HttpResponse handleIntrospect(HttpRequest request) {
        Map<String, String> params = parseFormBody(request.getBodyAsString());
        String token = params.get("token");
        String tokenTypeHint = params.get("token_type_hint");

        if (token == null) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing token");
        }

        // Try to find the token
        Optional<TokenStore.StoredToken> stored = Optional.empty();
        if (!"refresh_token".equals(tokenTypeHint)) {
            stored = tokenStore.validateAccessToken(token);
        }
        if (stored.isEmpty() && !"access_token".equals(tokenTypeHint)) {
            stored = tokenStore.validateRefreshToken(token);
        }

        if (stored.isEmpty()) {
            // Inactive token
            var response = HttpResponse.of(HttpStatus.OK);
            byte[] body = "{\"active\":false}".getBytes(StandardCharsets.UTF_8);
            response.setBody(ByteBuffer.wrap(body));
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
            return response;
        }

        var st = stored.get();
        var sb = new StringBuilder("{\"active\":true");
        sb.append(",\"client_id\":\"").append(st.clientId()).append("\"");
        sb.append(",\"sub\":\"").append(st.subject()).append("\"");
        if (!st.scopes().isEmpty()) {
            sb.append(",\"scope\":\"").append(String.join(" ", st.scopes())).append("\"");
        }
        sb.append(",\"exp\":").append(st.expiresAt().getEpochSecond());
        sb.append(",\"iat\":").append(st.issuedAt().getEpochSecond());
        if (issuer != null) {
            sb.append(",\"iss\":\"").append(issuer).append("\"");
        }
        sb.append("}");

        LOG.debug("Token introspection: active for client {}", st.clientId());

        var response = HttpResponse.of(HttpStatus.OK);
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        response.setBody(ByteBuffer.wrap(body));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        return response;
    }

    // ---- Implicit and Hybrid Flows ----

    /**
     * Handles an authorization request supporting code, implicit, and hybrid flows.
     * In addition to {@code response_type=code}, this supports:
     * <ul>
     *   <li>{@code response_type=token} — Implicit flow, returns access_token in fragment</li>
     *   <li>{@code response_type=code token} — Hybrid flow with code and token</li>
     *   <li>{@code response_type=code id_token} — Hybrid flow with code and id_token placeholder</li>
     * </ul>
     *
     * @param request   the HTTP request
     * @param principal the authenticated user
     * @return the HTTP response (redirect with code/token in query or fragment)
     * @since 0.1.0
     */
    public HttpResponse handleAuthorizeExtended(HttpRequest request, AuthPrincipal principal) {
        var params = request.getQueryParams();
        String responseType = params.get("response_type");
        String clientId = params.get("client_id");
        String redirectUri = params.get("redirect_uri");
        String scope = params.get("scope");
        String state = params.get("state");
        String codeChallenge = params.get("code_challenge");
        String challengeMethod = params.getOrDefault("code_challenge_method", "plain");
        String nonce = params.get("nonce");

        // Validate client
        var client = clientRegistry.get(clientId);
        if (client.isEmpty()) {
            return errorResponse(OAuth2Error.INVALID_CLIENT, "Unknown client");
        }

        // Validate redirect URI
        if (redirectUri != null && !clientRegistry.isRedirectUriAllowed(clientId, redirectUri)) {
            return errorResponse(OAuth2Error.INVALID_REQUEST, "Invalid redirect_uri");
        }

        Set<String> scopes = scope != null ? Set.of(scope.split(" ")) : Set.of();
        Set<String> responseTypes = Set.of(responseType.split("\\s+"));

        boolean hasCode = responseTypes.contains("code");
        boolean hasToken = responseTypes.contains("token");
        boolean hasIdToken = responseTypes.contains("id_token");

        if (!hasCode && !hasToken && !hasIdToken) {
            return redirectError(redirectUri, OAuth2Error.UNSUPPORTED_RESPONSE_TYPE,
                    "Unsupported response_type", state);
        }

        // Build fragment parameters for implicit/hybrid
        var fragment = new StringBuilder();
        var query = new StringBuilder();

        if (hasCode) {
            var authCode = codeStore.generate(clientId, redirectUri, scopes,
                    principal.getName(), codeChallenge, challengeMethod);
            if (hasToken || hasIdToken) {
                // Hybrid: code goes in fragment
                appendParam(fragment, "code", authCode.code());
            } else {
                // Pure code flow: code goes in query
                appendParam(query, "code", authCode.code());
            }
        }

        if (hasToken) {
            var accessToken = tokenStore.issueAccessToken(clientId, principal.getName(), scopes);
            appendParam(fragment, "access_token", accessToken.token());
            appendParam(fragment, "token_type", "Bearer");
            appendParam(fragment, "expires_in", String.valueOf(tokenStore.getAccessTokenLifetime().toSeconds()));
            if (!scopes.isEmpty()) {
                appendParam(fragment, "scope", String.join(" ", scopes));
            }
        }

        if (hasIdToken) {
            // Minimal id_token placeholder (real implementation would need JWT signing)
            String idTokenValue = "eyJ" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(("{\"sub\":\"" + principal.getName() + "\",\"iss\":\"" + issuer
                            + "\",\"aud\":\"" + clientId + "\",\"nonce\":\"" + (nonce != null ? nonce : "")
                            + "\"}").getBytes(StandardCharsets.UTF_8));
            appendParam(fragment, "id_token", idTokenValue);
        }

        if (state != null) {
            if (fragment.isEmpty()) {
                appendParam(query, "state", state);
            } else {
                appendParam(fragment, "state", state);
            }
        }

        String redirect = redirectUri;
        if (!query.isEmpty()) {
            redirect += "?" + query;
        }
        if (!fragment.isEmpty()) {
            redirect += "#" + fragment;
        }

        LOG.info("Issued authorization response ({}) for client {} and user {}",
                responseType, clientId, principal.getName());

        var response = HttpResponse.of(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, redirect);
        return response;
    }

    // ---- Dynamic Client Registration (RFC 7591) ----

    /**
     * Handles a dynamic client registration request (POST /register) per RFC 7591.
     * Accepts client metadata and returns a registered client with client_id and client_secret.
     *
     * <p>Expected JSON body fields:</p>
     * <ul>
     *   <li>{@code redirect_uris} — array of redirect URIs (required)</li>
     *   <li>{@code grant_types} — array of grant types (default: authorization_code)</li>
     *   <li>{@code client_name} — human-readable client name</li>
     *   <li>{@code token_endpoint_auth_method} — auth method (default: client_secret_basic)</li>
     * </ul>
     *
     * @param request the HTTP request with JSON body
     * @return the HTTP response with client_id, client_secret, and metadata
     * @since 0.1.0
     */
    public HttpResponse handleRegister(HttpRequest request) {
        String body = request.getBodyAsString();
        if (body == null || body.isBlank()) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "Missing registration body");
        }

        // Parse redirect_uris
        Set<String> redirectUris = parseJsonArray(body, "redirect_uris");
        if (redirectUris.isEmpty()) {
            return tokenError(OAuth2Error.INVALID_REQUEST, "redirect_uris is required");
        }

        // Parse optional fields
        Set<String> grantTypes = parseJsonArray(body, "grant_types");
        if (grantTypes.isEmpty()) {
            grantTypes = Set.of("authorization_code");
        }

        String clientName = extractJsonString(body, "client_name");

        // Generate client credentials
        byte[] idBytes = new byte[16];
        byte[] secretBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(idBytes);
        new java.security.SecureRandom().nextBytes(secretBytes);
        String clientId = Base64.getUrlEncoder().withoutPadding().encodeToString(idBytes);
        String clientSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        // Register the client
        var registeredClient = new OAuth2ClientRegistry.RegisteredClient(
                clientId, clientSecret, redirectUris, Set.of(), grantTypes, true);
        clientRegistry.register(registeredClient);

        // Build response
        var sb = new StringBuilder("{");
        sb.append("\"client_id\":\"").append(clientId).append("\"");
        sb.append(",\"client_secret\":\"").append(clientSecret).append("\"");
        sb.append(",\"redirect_uris\":[");
        int i = 0;
        for (String uri : redirectUris) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(uri).append("\"");
        }
        sb.append("]");
        sb.append(",\"grant_types\":[");
        i = 0;
        for (String gt : grantTypes) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(gt).append("\"");
        }
        sb.append("]");
        if (clientName != null) {
            sb.append(",\"client_name\":\"").append(clientName).append("\"");
        }
        sb.append(",\"token_endpoint_auth_method\":\"client_secret_basic\"");
        sb.append("}");

        LOG.info("Dynamically registered client {} with {} redirect URIs", clientId, redirectUris.size());

        var response = HttpResponse.of(HttpStatus.CREATED);
        byte[] respBody = sb.toString().getBytes(StandardCharsets.UTF_8);
        response.setBody(ByteBuffer.wrap(respBody));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(respBody.length));
        return response;
    }

    // ---- Helpers ----

    private void appendParam(StringBuilder sb, String key, String value) {
        if (!sb.isEmpty()) sb.append("&");
        sb.append(encode(key)).append("=").append(encode(value));
    }

    private static Set<String> parseJsonArray(String json, String key) {
        String search = "\"" + key + "\"";
        int keyStart = json.indexOf(search);
        if (keyStart < 0) return Set.of();

        int arrayStart = json.indexOf('[', keyStart);
        if (arrayStart < 0) return Set.of();

        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayEnd < 0) return Set.of();

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        Set<String> result = new LinkedHashSet<>();
        int pos = 0;
        while (pos < arrayContent.length()) {
            int quoteStart = arrayContent.indexOf('"', pos);
            if (quoteStart < 0) break;
            int quoteEnd = arrayContent.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) break;
            result.add(arrayContent.substring(quoteStart + 1, quoteEnd));
            pos = quoteEnd + 1;
        }
        return result;
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf('"', start);
        return end >= 0 ? json.substring(start, end) : null;
    }

    /** Returns the client registry. */
    public OAuth2ClientRegistry getClientRegistry() { return clientRegistry; }
    /** Returns the code store. */
    public AuthorizationCodeStore getCodeStore() { return codeStore; }
    /** Returns the token store. */
    public TokenStore getTokenStore() { return tokenStore; }
    /** Returns the issuer. */
    public String getIssuer() { return issuer; }
}
