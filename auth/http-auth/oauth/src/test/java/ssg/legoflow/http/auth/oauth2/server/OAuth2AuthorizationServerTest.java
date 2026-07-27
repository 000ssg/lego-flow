package ssg.legoflow.http.auth.oauth2.server;

import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class OAuth2AuthorizationServerTest {

    private OAuth2AuthorizationServer server;
    private OAuth2ClientRegistry clientRegistry;
    private AuthorizationCodeStore codeStore;
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        clientRegistry = new OAuth2ClientRegistry();
        clientRegistry.register(new OAuth2ClientRegistry.RegisteredClient(
                "app1", "secret1",
                Set.of("http://localhost/callback"),
                Set.of("openid", "profile", "email"),
                Set.of("authorization_code", "client_credentials", "password", "refresh_token"),
                true
        ));
        clientRegistry.register(new OAuth2ClientRegistry.RegisteredClient(
                "public-app", null,
                Set.of("http://localhost/callback"),
                Set.of("openid"),
                Set.of("authorization_code"),
                false
        ));

        codeStore = new AuthorizationCodeStore();
        tokenStore = new TokenStore();
        server = new OAuth2AuthorizationServer(clientRegistry, codeStore, tokenStore, "https://auth.example.com");
    }

    @Test
    void testHandleAuthorize() {
        var request = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=code&client_id=app1&redirect_uri=http://localhost/callback&state=abc123");
        var response = server.handleAuthorize(request, AuthPrincipal.of("alice"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().get(HttpHeaders.LOCATION);
        assertThat(location).contains("code=");
        assertThat(location).contains("state=abc123");
    }

    @Test
    void testHandleAuthorizeUnknownClient() {
        var request = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=code&client_id=unknown");
        var response = server.handleAuthorize(request, AuthPrincipal.of("alice"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testHandleAuthorizeInvalidRedirectUri() {
        var request = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=code&client_id=app1&redirect_uri=http://evil.com/callback");
        var response = server.handleAuthorize(request, AuthPrincipal.of("alice"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testHandleTokenAuthorizationCode() {
        // First, get an authorization code
        var authReq = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=code&client_id=app1&redirect_uri=http://localhost/callback");
        var authResp = server.handleAuthorize(authReq, AuthPrincipal.of("alice"));
        String location = authResp.getHeaders().get(HttpHeaders.LOCATION);
        String code = location.split("code=")[1].split("&")[0];

        // Exchange code for tokens
        String body = "grant_type=authorization_code&code=" + code
                + "&client_id=app1&client_secret=secret1&redirect_uri=http://localhost/callback";
        var tokenReq = HttpRequest.of(HttpMethod.POST, "/token");
        tokenReq.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var tokenResp = server.handleToken(tokenReq);

        assertThat(tokenResp.getStatus()).isEqualTo(HttpStatus.OK);
        String respBody = tokenResp.getBodyAsString();
        assertThat(respBody).contains("access_token");
        assertThat(respBody).contains("refresh_token");
    }

    @Test
    void testHandleTokenClientCredentials() {
        String body = "grant_type=client_credentials&client_id=app1&client_secret=secret1";
        var request = HttpRequest.of(HttpMethod.POST, "/token");
        request.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var response = server.handleToken(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("access_token");
    }

    @Test
    void testHandleTokenPassword() {
        String body = "grant_type=password&username=alice&password=pass&client_id=app1";
        var request = HttpRequest.of(HttpMethod.POST, "/token");
        request.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var response = server.handleToken(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testHandleTokenRefresh() {
        // First get tokens
        String body1 = "grant_type=client_credentials&client_id=app1&client_secret=secret1";
        var req1 = HttpRequest.of(HttpMethod.POST, "/token");
        req1.setBody(ByteBuffer.wrap(body1.getBytes(StandardCharsets.UTF_8)));
        var resp1 = server.handleToken(req1);
        String refreshToken = extractJsonValue(resp1.getBodyAsString(), "refresh_token");

        // Refresh
        String body2 = "grant_type=refresh_token&refresh_token=" + refreshToken + "&client_id=app1";
        var req2 = HttpRequest.of(HttpMethod.POST, "/token");
        req2.setBody(ByteBuffer.wrap(body2.getBytes(StandardCharsets.UTF_8)));
        var resp2 = server.handleToken(req2);
        assertThat(resp2.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testHandleTokenMissingGrantType() {
        var request = HttpRequest.of(HttpMethod.POST, "/token");
        request.setBody(ByteBuffer.wrap("client_id=app1".getBytes(StandardCharsets.UTF_8)));
        var response = server.handleToken(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testHandleTokenUnsupportedGrantType() {
        var request = HttpRequest.of(HttpMethod.POST, "/token");
        request.setBody(ByteBuffer.wrap("grant_type=unsupported".getBytes(StandardCharsets.UTF_8)));
        var response = server.handleToken(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testHandleRevoke() {
        // Get a token first
        String body1 = "grant_type=client_credentials&client_id=app1&client_secret=secret1";
        var req1 = HttpRequest.of(HttpMethod.POST, "/token");
        req1.setBody(ByteBuffer.wrap(body1.getBytes(StandardCharsets.UTF_8)));
        var resp1 = server.handleToken(req1);
        String accessToken = extractJsonValue(resp1.getBodyAsString(), "access_token");

        // Revoke
        String body2 = "token=" + accessToken + "&token_type_hint=access_token";
        var req2 = HttpRequest.of(HttpMethod.POST, "/revoke");
        req2.setBody(ByteBuffer.wrap(body2.getBytes(StandardCharsets.UTF_8)));
        var resp2 = server.handleRevoke(req2);
        assertThat(resp2.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testHandleRevokeMissingToken() {
        var request = HttpRequest.of(HttpMethod.POST, "/revoke");
        request.setBody(ByteBuffer.wrap("token_type_hint=access_token".getBytes(StandardCharsets.UTF_8)));
        var response = server.handleRevoke(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testHandleTokenInvalidCode() {
        String body = "grant_type=authorization_code&code=invalid&client_id=app1&client_secret=secret1";
        var request = HttpRequest.of(HttpMethod.POST, "/token");
        request.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var response = server.handleToken(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testGetters() {
        assertThat(server.getClientRegistry()).isEqualTo(clientRegistry);
        assertThat(server.getCodeStore()).isEqualTo(codeStore);
        assertThat(server.getTokenStore()).isEqualTo(tokenStore);
        assertThat(server.getIssuer()).isEqualTo("https://auth.example.com");
    }

    // ---- Token Introspection (RFC 7662) ----

    @Test
    void testIntrospectActiveToken() {
        // Issue a token
        String body1 = "grant_type=client_credentials&client_id=app1&client_secret=secret1&scope=read write";
        var req1 = HttpRequest.of(HttpMethod.POST, "/token");
        req1.setBody(ByteBuffer.wrap(body1.getBytes(StandardCharsets.UTF_8)));
        var resp1 = server.handleToken(req1);
        String accessToken = extractJsonValue(resp1.getBodyAsString(), "access_token");

        // Introspect
        String body2 = "token=" + accessToken;
        var req2 = HttpRequest.of(HttpMethod.POST, "/introspect");
        req2.setBody(ByteBuffer.wrap(body2.getBytes(StandardCharsets.UTF_8)));
        var resp2 = server.handleIntrospect(req2);

        assertThat(resp2.getStatus()).isEqualTo(HttpStatus.OK);
        String respBody = resp2.getBodyAsString();
        assertThat(respBody).contains("\"active\":true");
        assertThat(respBody).contains("\"client_id\":\"app1\"");
        assertThat(respBody).contains("\"exp\":");
    }

    @Test
    void testIntrospectInvalidToken() {
        String body = "token=invalid-token";
        var request = HttpRequest.of(HttpMethod.POST, "/introspect");
        request.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var response = server.handleIntrospect(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).contains("\"active\":false");
    }

    @Test
    void testIntrospectMissingToken() {
        var request = HttpRequest.of(HttpMethod.POST, "/introspect");
        request.setBody(ByteBuffer.wrap("".getBytes(StandardCharsets.UTF_8)));
        var response = server.handleIntrospect(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testIntrospectWithTokenTypeHint() {
        // Issue a token
        String body1 = "grant_type=client_credentials&client_id=app1&client_secret=secret1";
        var req1 = HttpRequest.of(HttpMethod.POST, "/token");
        req1.setBody(ByteBuffer.wrap(body1.getBytes(StandardCharsets.UTF_8)));
        var resp1 = server.handleToken(req1);
        String refreshToken = extractJsonValue(resp1.getBodyAsString(), "refresh_token");

        // Introspect refresh token with hint
        String body2 = "token=" + refreshToken + "&token_type_hint=refresh_token";
        var req2 = HttpRequest.of(HttpMethod.POST, "/introspect");
        req2.setBody(ByteBuffer.wrap(body2.getBytes(StandardCharsets.UTF_8)));
        var resp2 = server.handleIntrospect(req2);
        assertThat(resp2.getBodyAsString()).contains("\"active\":true");
    }

    // ---- Implicit Flow ----

    @Test
    void testImplicitFlow() {
        var request = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=token&client_id=app1&redirect_uri=http://localhost/callback&state=xyz");
        var response = server.handleAuthorize(request, AuthPrincipal.of("alice"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().get(HttpHeaders.LOCATION);
        assertThat(location).contains("#");
        assertThat(location).contains("access_token=");
        assertThat(location).contains("token_type=Bearer");
        assertThat(location).contains("state=xyz");
    }

    // ---- Hybrid Flow ----

    @Test
    void testHybridFlowCodeToken() {
        var request = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=code+token&client_id=app1&redirect_uri=http://localhost/callback");
        var response = server.handleAuthorizeExtended(request, AuthPrincipal.of("alice"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().get(HttpHeaders.LOCATION);
        assertThat(location).contains("#");
        assertThat(location).contains("code=");
        assertThat(location).contains("access_token=");
    }

    @Test
    void testHybridFlowCodeIdToken() {
        var request = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=code+id_token&client_id=app1&redirect_uri=http://localhost/callback&nonce=test-nonce");
        var response = server.handleAuthorizeExtended(request, AuthPrincipal.of("alice"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().get(HttpHeaders.LOCATION);
        assertThat(location).contains("#");
        assertThat(location).contains("code=");
        assertThat(location).contains("id_token=");
    }

    @Test
    void testAuthorizeUnsupportedResponseType() {
        var request = HttpRequest.of(HttpMethod.GET,
                "/authorize?response_type=invalid&client_id=app1&redirect_uri=http://localhost/callback");
        var response = server.handleAuthorizeExtended(request, AuthPrincipal.of("alice"));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND);
        String location = response.getHeaders().get(HttpHeaders.LOCATION);
        assertThat(location).contains("error=unsupported_response_type");
    }

    // ---- Dynamic Client Registration (RFC 7591) ----

    @Test
    void testDynamicClientRegistration() {
        String body = """
                {"redirect_uris":["http://newapp.example.com/callback"],"client_name":"New App","grant_types":["authorization_code","refresh_token"]}
                """;
        var request = HttpRequest.of(HttpMethod.POST, "/register");
        request.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var response = server.handleRegister(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        String respBody = response.getBodyAsString();
        assertThat(respBody).contains("\"client_id\":");
        assertThat(respBody).contains("\"client_secret\":");
        assertThat(respBody).contains("\"client_name\":\"New App\"");
        assertThat(respBody).contains("\"redirect_uris\":");

        // Verify the client was actually registered
        String clientId = extractJsonValue(respBody, "client_id");
        assertThat(clientRegistry.get(clientId)).isPresent();
    }

    @Test
    void testDynamicClientRegistrationMissingRedirectUris() {
        String body = "{\"client_name\":\"No URIs\"}";
        var request = HttpRequest.of(HttpMethod.POST, "/register");
        request.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var response = server.handleRegister(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testDynamicClientRegistrationEmptyBody() {
        var request = HttpRequest.of(HttpMethod.POST, "/register");
        request.setBody(ByteBuffer.wrap("".getBytes(StandardCharsets.UTF_8)));
        var response = server.handleRegister(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testDynamicClientRegistrationDefaultGrantType() {
        String body = "{\"redirect_uris\":[\"http://app.example.com/cb\"]}";
        var request = HttpRequest.of(HttpMethod.POST, "/register");
        request.setBody(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        var response = server.handleRegister(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBodyAsString()).contains("authorization_code");
    }

    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf('"', start);
        return end >= 0 ? json.substring(start, end) : null;
    }
}
