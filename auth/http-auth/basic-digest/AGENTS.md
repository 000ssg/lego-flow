# HTTP Auth Basic/Digest — Development Guide

## Module Purpose

Implements HTTP Basic Authentication (RFC 7617) and HTTP Digest Authentication (RFC 7616).

## Key Classes

- `BasicAuthScheme` — RFC 7617: Base64 decode Authorization header, validate credentials
- `InMemoryUserStore` — ConcurrentHashMap user store implementing both BasicUserStore and AuthContext.UserStore
- `HashedPasswordStore` — SHA-256 with per-user salt for password hashing
- `DigestAuthScheme` — RFC 7616: MD5/MD5-sess/SHA-256/SHA-256-sess, qop=auth/auth-int, nonce counting, replay detection, proxy auth (407), Authentication-Info header
- `NonceManager` — SecureRandom nonce generation, expiry, replay detection via nonce count tracking
- `DigestChallenge` — WWW-Authenticate: Digest header builder

## Tests: 87

Run: `mvn test -pl http-auth/basic-digest -am`
