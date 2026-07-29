# auth / http-auth / core — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

Core authentication framework providing pluggable scheme registration, session management, and JWT token handling. All other http-auth sub-modules depend on this.

## Key Classes

- `AuthCredentials` — Sealed interface: Basic, Bearer, Digest, None
- `AuthResult` — Sealed interface: Success, Failure, Challenge
- `AuthPrincipal` — Authenticated identity (name, roles, attributes)
- `AuthContext` — Realm + UserStore + SessionManager
- `AuthSchemeRegistry` — Case-insensitive ConcurrentHashMap of schemes
- `AuthFilter` — Parses Authorization header, delegates to scheme
- `AuthMiddleware` — Request handler wrapper with path exclusion and role checks
- `HttpSession` — Session with attributes, expiry, touch
- `SessionManager` — Cookie-based session orchestration (AutoCloseable)
- `InMemorySessionStore` — ConcurrentHashMap session store
- `SessionCookie` — Set-Cookie builder with SameSite
- `JwtTokenProvider` — JWT from scratch (HS256 via javax.crypto, RS256 via java.security)
- `JwtClaims` — Claims with JSON parser from scratch
- `JwtHeader` — Record(alg, typ)

## Tests: 134

Run: `mvn test -pl http-auth/core -am`
