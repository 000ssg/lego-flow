# HTTP Auth Core — RFC Compliance

## Specifications Covered

- **RFC 7235** — HTTP/1.1 Authentication (scheme parsing, WWW-Authenticate, credential extraction)
- **RFC 7519** — JSON Web Token (JWT) claims structure
- **RFC 7515** — JSON Web Signature (JWS) — signing and verification

## Compliance Matrix

### RFC 7235 — HTTP/1.1 Authentication

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §2 | `Authorization` header parsed in `scheme credentials` format | ✅ Implemented | `AuthFilterTest` |
| §2 | Scheme name matching is case-insensitive | ✅ Implemented | `AuthFilterTest` |
| §2.1 | Challenge response via `WWW-Authenticate` header | ✅ Implemented | `AuthFilterTest` |
| §4.1 | Multiple authentication schemes registered via `AuthSchemeRegistry` | ✅ Implemented | `AuthSchemeRegistryTest` |
| §4.2 | Scheme-specific credential extraction delegated to registered scheme | ✅ Implemented | `AuthFilterTest` |

### RFC 7519 — JSON Web Token (JWT)

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §3 | Header: `{"alg":"HS256","typ":"JWT"}` or `{"alg":"RS256","typ":"JWT"}` | ✅ Implemented | `JwtTokenProviderTest` |
| §4.1.1 | `iss` (Issuer) claim | ✅ Implemented | `JwtClaimsTest` |
| §4.1.2 | `sub` (Subject) claim | ✅ Implemented | `JwtClaimsTest` |
| §4.1.3 | `aud` (Audience) claim | ✅ Implemented | `JwtClaimsTest` |
| §4.1.4 | `exp` (Expiration Time) claim with validation | ✅ Implemented | `JwtTokenProviderTest` |
| §4.1.5 | `nbf` (Not Before) claim with validation | ✅ Implemented | `JwtTokenProviderTest` |
| §4.1.6 | `iat` (Issued At) claim | ✅ Implemented | `JwtClaimsTest` |
| §4.1.7 | `jti` (JWT ID) claim | ✅ Implemented | `JwtClaimsTest` |
| §7.2 | Base64url encoding without padding | ✅ Implemented | `JwtTokenProviderTest` |

### RFC 7515 — JSON Web Signature (JWS)

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §3.1 | HMAC-SHA256 (`HS256`) signing via `javax.crypto.Mac` | ✅ Implemented | `JwtTokenProviderTest` |
| §3.1 | RSA-SHA256 (`RS256`) signing via `java.security.Signature` | ✅ Implemented | `JwtTokenProviderTest` |
| §5.2 | Signature verification during JWT validation | ✅ Implemented | `JwtTokenProviderTest` |
| §5.2 | Expiration check during JWT validation | ✅ Implemented | `JwtTokenProviderTest` |
| §5.2 | Not-before check during JWT validation | ✅ Implemented | `JwtTokenProviderTest` |
| §5.2 | Issuer validation during JWT validation | ✅ Implemented | `JwtTokenProviderTest` |

### Session Management

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| Sessions | Cookie-based session management with `Set-Cookie` + `SameSite` | ✅ Implemented | `SessionManagerTest` |
| Sessions | Session attributes, expiry, and touch support | ✅ Implemented | `HttpSessionTest` |
| Sessions | Thread-safe `ConcurrentHashMap` session store | ✅ Implemented | `InMemorySessionStoreTest` |
| Sessions | `SessionManager` implements `AutoCloseable` for resource cleanup | ✅ Implemented | `SessionManagerTest` |

## Test Coverage Summary
- Total compliance tests: 134
- Key test classes: `AuthFilterTest`, `AuthSchemeRegistryTest`, `AuthMiddlewareTest`, `JwtTokenProviderTest`, `JwtClaimsTest`, `SessionManagerTest`, `HttpSessionTest`, `InMemorySessionStoreTest`, `SessionCookieTest`
