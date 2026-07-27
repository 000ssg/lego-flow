# HTTP Auth Core — Architecture

## Module Purpose

Provides the foundational authentication abstractions that all other http-auth sub-modules build upon.

## Key Abstractions

- **AuthenticationScheme**: Interface for auth mechanisms (schemeName, authenticate, challenge, extractCredentials)
- **AuthSchemeRegistry**: ConcurrentHashMap<String, AuthenticationScheme> with case-insensitive lookup
- **AuthFilter**: Parses "Scheme credentials" from Authorization header, looks up scheme, delegates
- **AuthMiddleware**: HttpRequestHandler decorator with configurable path exclusions and required roles
- **AuthResult**: Sealed — Success(AuthPrincipal) | Failure(String reason) | Challenge(String scheme)
- **AuthCredentials**: Sealed — Basic(user, pass) | Bearer(token) | Digest(params) | None

## Session Architecture

```mermaid
graph LR
    Req["Request"] --> SM["SessionManager"]
    SM --> SC["SessionCookie.extractSessionId()"]
    SC --> SS["SessionStore.get()"]
    SS --> HS["HttpSession (touch)"]
```

SessionManager creates/retrieves sessions via cookies. InMemorySessionStore uses ConcurrentHashMap. Sessions auto-expire based on configurable timeout.

## JWT Architecture

JwtTokenProvider constructs tokens as `base64url(header).base64url(claims).base64url(signature)`. The JSON serializer/parser is built from scratch in JwtClaims. Signing uses javax.crypto.Mac for HS256 and java.security.Signature for RS256.

## Thread Safety

- AuthSchemeRegistry: ConcurrentHashMap
- InMemorySessionStore: ConcurrentHashMap
- HttpSession attributes: ConcurrentHashMap
- SessionManager: thread-safe via store delegation

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../../doc/ARCHITECTURE.md) | [Root README](../../../../README.md)
