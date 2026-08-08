# HTTP Auth OAuth — Requirements

## Original Request

> Implement OAuth 2.0 (all grant types), PKCE, Bearer tokens, OpenID Connect, and pre-configured providers (Google, GitHub, Microsoft, Facebook, Twitter, Apple, Generic).

## Requirements

1. OAuth2Config builder with all endpoints (authorization, token, userinfo, revocation, JWKS)
2. Authorization Code flow with state parameter for CSRF protection
3. Client Credentials grant
4. Resource Owner Password grant
5. Refresh Token grant
6. PKCE (S256) code challenge generation and verification
7. Bearer token authentication scheme with JWT validation
8. Bearer scheme fallback: try JWT first, then token store introspection
9. OAuth 2.0 server: authorization endpoint, token endpoint, revocation endpoint
10. Authorization code store with single-use codes and PKCE support
11. Token store with access and refresh token issuance/validation
12. Client registry with secret authentication and redirect URI validation
13. OpenID Connect: discovery metadata, ID token parsing, UserInfo parsing
14. OIDC authentication flow with nonce for ID token binding
15. Pre-configured providers: Google, GitHub, Microsoft (with tenant), Facebook, Twitter, Apple
16. Generic fully-configurable provider
17. All public classes with Javadoc @since 0.1.0

## Implementation

- 17 test classes, 175 tests total
- OAuth providers use singleton pattern (INSTANCE) except Microsoft (configurable tenant)
- Apple provider has null userInfoEndpoint (user info in ID token)
