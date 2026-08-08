# auth / http-auth — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

`http-auth` is the authentication, authorization, and SSO module for Lego Flow. It provides pluggable HTTP authentication schemes (Basic, Digest), OAuth 2.0, OpenID Connect, JWT, SSO, reverse proxy auth, and SAML 2.0 assertion parsing.

## Module Structure

```
http-auth/                    ← parent POM (packaging=pom)
├── core/                     ← Authentication framework, sessions, JWT
├── basic-digest/             ← HTTP Basic (RFC 7617) and Digest (RFC 7616)
├── oauth/                    ← OAuth 2.0 (RFC 6749), Bearer (RFC 6750), PKCE (RFC 7636), OIDC, providers
└── sso/                      ← SSO federation, reverse proxy SSO, SAML 2.0
```

## Key Design Decisions

- **JWT from scratch**: Uses `javax.crypto.Mac` (HMAC-SHA256) and `java.security.Signature` (RSA-SHA256), no external JWT library
- **JSON from scratch**: Simple parser in `JwtClaims.fromJson()`, no external JSON library
- **SAML XML from scratch**: String-based XML element extraction, no external XML parser
- **Sealed interfaces**: `AuthCredentials` and `AuthResult` are sealed for exhaustive pattern matching
- **Thread safety**: `ConcurrentHashMap` for session stores, nonce managers, token stores
- **AutoCloseable**: `SessionManager`, `SsoManager` implement proper resource cleanup

## Package Layout

```
ssg.legoflow.http.auth/          — AuthCredentials, AuthResult, AuthPrincipal, AuthContext, AuthSchemeRegistry, AuthFilter, AuthMiddleware
ssg.legoflow.http.auth.session/   — HttpSession, SessionStore, InMemorySessionStore, SessionManager, SessionCookie
ssg.legoflow.http.auth.token/      — JwtTokenProvider, JwtHeader, JwtClaims, TokenProvider
ssg.legoflow.http.auth.basic/      — BasicAuthScheme, InMemoryUserStore, HashedPasswordStore
ssg.legoflow.http.auth.digest/     — DigestAuthScheme, NonceManager, DigestChallenge
ssg.legoflow.http.auth.oauth2/     — OAuth2Client, OAuth2Config, TokenRequest, AuthorizationRequest, PkceChallenge
ssg.legoflow.http.auth.oauth2.server/ — OAuth2AuthorizationServer, TokenStore, AuthorizationCodeStore, OAuth2ClientRegistry
ssg.legoflow.http.auth.oidc/       — OpenIdConnectClient, OidcDiscovery, IdToken, UserInfo
ssg.legoflow.http.auth.bearer/     — BearerAuthScheme
ssg.legoflow.http.auth.provider/   — OAuthProvider, GoogleOAuth, GitHubOAuth, MicrosoftOAuth, FacebookOAuth, TwitterOAuth, AppleOAuth, GenericOAuth
ssg.legoflow.http.auth.sso/        — SsoManager, SsoSession, SsoConfig
ssg.legoflow.http.auth.reverse/    — ReverseProxySso, AuthHeaderInjector, ReverseProxySsoConfig
ssg.legoflow.http.auth.saml/       — SamlAssertionParser, SamlConfig, SamlAuthnRequest, SamlPostBinding, SamlSignatureValidator, SamlLogout, SamlEncryptedAssertion
```

## Testing

- **Total tests**: 575 (core: 132, basic-digest: 87, oauth: 205, sso: 151)
- Run all tests: `mvn test -pl http-auth/core,http-auth/basic-digest,http-auth/oauth,http-auth/sso -am`
- Run single module: `mvn test -pl http-auth/core -am`

## Dependencies

- `lego-flow-blocks` — core framework
- `lego-flow-http` — HTTP types (HttpRequest, HttpResponse, HttpHeaders, etc.)
- `slf4j-api` — logging facade
- JUnit 5 + AssertJ for tests
