# Lego Flow HTTP Auth

HTTP authentication, authorization, OAuth 2.0, OpenID Connect, and SSO module for the Lego Flow framework.

## Sub-Modules

| Module | Description | Tests |
|--------|-------------|-------|
| `core` | Authentication framework, sessions, JWT | 132 |
| `basic-digest` | HTTP Basic (RFC 7617) and Digest (RFC 7616) | 87 |
| `oauth` | OAuth 2.0, Bearer tokens, PKCE, OIDC, JWK Set, providers | 205 |
| `sso` | SSO federation, reverse proxy SSO, SAML 2.0 | 151 |

**Total: 575 tests**

## Features

- Pluggable authentication scheme registry
- HTTP Basic authentication (RFC 7617)
- HTTP Digest authentication (RFC 7616) with MD5, MD5-sess, SHA-256, SHA-256-sess
- Proxy authentication (407 Proxy-Authenticate) for digest auth
- Authentication-Info response header (RFC 7616 Section 3.8)
- JWT token generation and validation from scratch (HS256, RS256)
- Cookie-based session management
- OAuth 2.0 all grant types (authorization code, implicit, hybrid, client credentials, password, refresh token)
- PKCE extension (RFC 7636) with S256
- Token introspection endpoint (RFC 7662)
- Dynamic client registration (RFC 7591)
- OpenID Connect (discovery, ID token, UserInfo, JWK Set)
- Pre-configured OAuth providers (Google, GitHub, Microsoft, Facebook, Twitter, Apple)
- JWT-based SSO with federated sessions
- Reverse proxy SSO with header injection
- SAML 2.0 assertion parsing, AuthnRequest generation, XML signature validation
- SAML logout (LogoutRequest/LogoutResponse)
- SAML encrypted assertion decryption (RSA-OAEP + AES-CBC/GCM)
- Authentication middleware with path exclusion and role-based access

## Quick Start

```java
// Basic Auth
var userStore = new InMemoryUserStore().addUser("alice", "password", Set.of("admin"));
var basicScheme = new BasicAuthScheme();

// JWT
var jwt = JwtTokenProvider.hmac256("your-32-byte-secret-key-here!!!", "issuer", Duration.ofHours(1));
String token = jwt.generateToken("alice");

// OAuth 2.0 Client
var config = OAuth2Config.builder()
    .clientId("client-id")
    .clientSecret("client-secret")
    .redirectUri("http://localhost/callback")
    .authorizationEndpoint("https://auth.example.com/authorize")
    .tokenEndpoint("https://auth.example.com/token")
    .build();
var client = new OAuth2Client(config);

// Pre-configured Google OAuth
var googleConfig = GoogleOAuth.INSTANCE.buildConfig("client-id", "secret", "http://localhost/callback");
```

## Build

```bash
mvn compile -pl http-auth/core,http-auth/basic-digest,http-auth/oauth,http-auth/sso -am
mvn test -pl http-auth/core,http-auth/basic-digest,http-auth/oauth,http-auth/sso -am
```

## Version

- JDK: 24
- Version: 0.1.0-SNAPSHOT

## Documentation

- [Architecture](doc/ARCHITECTURE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)

### Sub-module Documentation

- **core/** — [README](core/README.md) | [Architecture](core/doc/ARCHITECTURE.md) | [Requirements](core/doc/REQUIREMENTS.md) | [Compliance](core/doc/COMPLIANCE.md)
- **basic-digest/** — [README](basic-digest/README.md) | [Architecture](basic-digest/doc/ARCHITECTURE.md) | [Requirements](basic-digest/doc/REQUIREMENTS.md) | [Compliance](basic-digest/doc/COMPLIANCE.md)
- **oauth/** — [README](oauth/README.md) | [Architecture](oauth/doc/ARCHITECTURE.md) | [Requirements](oauth/doc/REQUIREMENTS.md) | [Compliance](oauth/doc/COMPLIANCE.md)
- **sso/** — [README](sso/README.md) | [Architecture](sso/doc/ARCHITECTURE.md) | [Requirements](sso/doc/REQUIREMENTS.md) | [Compliance](sso/doc/COMPLIANCE.md)
- **spnego/** — [README](spnego/README.md) | [Architecture](spnego/doc/ARCHITECTURE.md) | [Requirements](spnego/doc/REQUIREMENTS.md) | [Compliance](spnego/doc/COMPLIANCE.md)
