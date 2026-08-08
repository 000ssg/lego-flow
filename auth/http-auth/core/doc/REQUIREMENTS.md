# HTTP Auth Core — Requirements

## Original Request

> Create a complete http-auth module with core sub-module providing pluggable authentication framework, session management, and JWT from scratch using javax.crypto.

## Requirements

1. Pluggable authentication scheme interface with scheme name, authenticate, challenge, and extractCredentials methods
2. Case-insensitive scheme registry using ConcurrentHashMap
3. AuthFilter that parses Authorization header and delegates to registered schemes
4. AuthMiddleware wrapping HttpRequestHandler with path exclusion and role-based access control
5. Sealed AuthCredentials interface with Basic, Bearer, Digest, and None variants
6. Sealed AuthResult interface with Success (principal), Failure (reason), and Challenge (scheme name) variants
7. AuthPrincipal with name, roles (Set), and attributes (Map)
8. AuthContext with realm, UserStore interface, and optional SessionManager
9. Cookie-based session management with HttpSession, SessionStore interface, InMemorySessionStore
10. SessionCookie with SameSite, Secure, HttpOnly, Max-Age, Path, Domain support
11. JWT from scratch: HS256 (javax.crypto.Mac), RS256 (java.security.Signature)
12. JWT claims with JSON serialization/deserialization from scratch (no external JSON library)
13. All classes with Javadoc including @since 0.1.0
14. Thread-safe implementations using ConcurrentHashMap and volatile fields
15. AutoCloseable for SessionManager

## Implementation

- 14 test classes, 134 tests total
- Zero external dependencies beyond slf4j-api
- Uses JDK 24 sealed interfaces and records
