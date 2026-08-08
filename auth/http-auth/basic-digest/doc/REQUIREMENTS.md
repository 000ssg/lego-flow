# HTTP Auth Basic/Digest — Requirements

## Original Request

> Implement HTTP Basic (RFC 7617) and Digest (RFC 7616) authentication with MD5/SHA-256, qop=auth/auth-int, nonce counting, and replay detection.

## Requirements

1. Basic Auth: Base64 decode "username:password" from Authorization header
2. Basic Auth: encodeCredentials() static helper
3. InMemoryUserStore with plain-text and hashed password support
4. HashedPasswordStore with SHA-256 and per-user salt
5. Digest Auth: Support MD5 and SHA-256 algorithms
6. Digest Auth: qop=auth (protect method + URI) and auth-int (+ body)
7. Digest Auth: Nonce generation using SecureRandom (24 bytes = 48 hex chars)
8. Digest Auth: Nonce count (nc) validation for replay detection
9. Digest Auth: Stale nonce detection and re-challenge
10. Digest Auth: Opaque parameter support
11. DigestChallenge for building WWW-Authenticate header
12. All public classes with Javadoc @since 0.1.0

## Implementation

- 6 test classes, 73 tests total
- computeDigestResponse() and hash() exposed as static utility methods
- parseDigestParams() handles both quoted and unquoted values
