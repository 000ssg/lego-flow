# HTTP Auth OAuth — Development Guide

## Module Purpose

Implements OAuth 2.0 (RFC 6749), Bearer Token (RFC 6750), PKCE (RFC 7636), OpenID Connect, and pre-configured OAuth providers.

## Key Classes

### OAuth 2.0 Client
- `OAuth2Client` — All grant flows: authorization code, client credentials, password, refresh token, PKCE
- `OAuth2Config` — Builder pattern configuration with all endpoints
- `AuthorizationRequest` — URL builder with state, PKCE, nonce
- `TokenRequest` — Form body encoder for token endpoint
- `OAuth2TokenResponse` — Token response with JSON serialization
- `OAuth2Error` — Standard error response
- `PkceChallenge` — S256/plain code challenge generation and verification

### OAuth 2.0 Server
- `OAuth2AuthorizationServer` — handleAuthorize, handleToken, handleRevoke, handleIntrospect, handleAuthorizeExtended (implicit/hybrid), handleRegister (dynamic registration)
- `TokenStore` — Access/refresh token issuance and validation
- `AuthorizationCodeStore` — Single-use codes with PKCE
- `OAuth2ClientRegistry` — RegisteredClient authentication

### OpenID Connect
- `OpenIdConnectClient` — OIDC layer on OAuth2Client
- `OidcDiscovery` — .well-known/openid-configuration metadata
- `IdToken` — JWT ID token with OIDC claims
- `UserInfo` — UserInfo response claims
- `JwkSet` — JWK Set parsing (RFC 7517), RSA key extraction, kid-based lookup
- `JwkSetFetcher` — JWK Set fetching with caching and key rotation support

### Bearer
- `BearerAuthScheme` — JWT validation and/or token store introspection, fallback chain

### Providers
- `OAuthProvider` — Abstract base with buildConfig()
- `GoogleOAuth`, `GitHubOAuth`, `MicrosoftOAuth`, `FacebookOAuth`, `TwitterOAuth`, `AppleOAuth`, `GenericOAuth`

## Tests: 205

Run: `mvn test -pl http-auth/oauth -am`
