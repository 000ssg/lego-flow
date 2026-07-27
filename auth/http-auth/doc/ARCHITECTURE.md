# HTTP Auth — Architecture

## Module Purpose

Provides a complete, modular HTTP authentication and authorization framework. Designed as composable building blocks that can be used independently or together.

## Key Abstractions

### Core Framework
- **AuthenticationScheme** — Interface for pluggable auth schemes (Basic, Digest, Bearer)
- **AuthSchemeRegistry** — Case-insensitive registry mapping scheme names to implementations
- **AuthFilter** — Parses Authorization header and delegates to the appropriate scheme
- **AuthMiddleware** — HttpRequestHandler wrapper enforcing authentication with path exclusion and role checks
- **AuthResult** — Sealed interface: `Success(principal)`, `Failure(reason)`, `Challenge(schemeName)`
- **AuthCredentials** — Sealed interface: `Basic(username, password)`, `Bearer(token)`, `Digest(params)`, `None`
- **AuthPrincipal** — Authenticated user identity with name, roles, and attributes

### Session Management
- **SessionStore** — Interface for session persistence (create, get, remove, clean expired)
- **InMemorySessionStore** — ConcurrentHashMap-based implementation
- **SessionManager** — Orchestrates session lifecycle via cookies, implements AutoCloseable
- **SessionCookie** — Cookie builder with SameSite, Secure, HttpOnly support

### JWT (from scratch)
- **JwtTokenProvider** — Signs/verifies JWT using javax.crypto.Mac (HS256) or java.security.Signature (RS256)
- **JwtClaims** — Claims map with standard getters, JSON serialization/deserialization from scratch
- **JwtHeader** — JWT header record with algorithm and type

### OAuth 2.0
- **OAuth2Client** — Client-side flows: authorization code, client credentials, password, refresh token, PKCE
- **OAuth2AuthorizationServer** — Server-side: authorize, token, revoke endpoints
- **TokenStore** — Access and refresh token issuance and validation
- **AuthorizationCodeStore** — Single-use authorization codes with PKCE support
- **OAuth2ClientRegistry** — Registered client authentication

### OpenID Connect
- **OpenIdConnectClient** — OIDC layer on OAuth2Client: authentication flow with nonce, ID token, UserInfo
- **OidcDiscovery** — Provider metadata from .well-known/openid-configuration
- **IdToken** — JWT-based ID token with standard OIDC claims
- **UserInfo** — UserInfo endpoint response parsing

### SSO
- **SsoManager** — JWT-based federated SSO with login, validate, logout, session cleanup
- **ReverseProxySso** — Proxy-level auth extracting principals from forwarded headers
- **AuthHeaderInjector** — Injects/strips auth headers for backend forwarding
- **SamlAssertionParser** — SAML 2.0 response/assertion XML parsing

## Design Patterns

- **Strategy Pattern**: AuthenticationScheme implementations are interchangeable
- **Registry Pattern**: AuthSchemeRegistry provides runtime scheme discovery
- **Builder Pattern**: OAuth2Config, SessionCookie use builders
- **Decorator Pattern**: AuthMiddleware wraps HttpRequestHandler
- **Template Method**: OAuthProvider subclasses provide endpoint URLs

## Data Flow

```mermaid
graph LR
    Req["Request"] --> MW["AuthMiddleware"]
    MW --> AF["AuthFilter"]
    AF --> Reg["AuthSchemeRegistry"]
    Reg --> Scheme["AuthenticationScheme"]
    Scheme --> Result["AuthResult<br/>(Success/Failure/Challenge)"]
```

## Thread Safety

- All stores use ConcurrentHashMap
- Session attributes are stored in ConcurrentHashMap
- NonceManager uses SecureRandom and ConcurrentHashMap
- SsoSession uses volatile fields for last-access and invalidation state

## Extension Points

- Implement `AuthenticationScheme` for custom auth mechanisms
- Implement `SessionStore` for persistent session storage (Redis, DB)
- Implement `TokenProvider` for custom token formats
- Extend `OAuthProvider` for additional OAuth providers
- Implement `AuthContext.UserStore` for custom user storage

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md)
- [Root Architecture](../../../doc/ARCHITECTURE.md) | [Root README](../../../README.md)
