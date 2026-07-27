# HTTP Auth Basic/Digest — RFC Compliance

## Specifications Covered

- **RFC 7617** — The 'Basic' HTTP Authentication Scheme
- **RFC 7616** — HTTP Digest Access Authentication

## Compliance Matrix

### RFC 7617 — HTTP Basic Authentication

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §2 | `Authorization: Basic base64(user:pass)` header parsing and decoding | ✅ Implemented | `BasicAuthSchemeTest` |
| §2 | `WWW-Authenticate: Basic realm="..."` challenge generation | ✅ Implemented | `BasicAuthSchemeTest` |
| §2.1 | UTF-8 credential encoding | ✅ Implemented | `BasicAuthSchemeTest` |
| §2 | Case-insensitive scheme name matching | ✅ Implemented | `BasicAuthSchemeTest` |

### RFC 7616 — HTTP Digest Authentication

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| §3.4 | Response computation: `H(H(A1):nonce:nc:cnonce:qop:H(A2))` | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.4.1 | A1 computation: `username:realm:password` | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.4.2 | A2 computation (auth): `method:uri` | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.4.2 | A2 computation (auth-int): `method:uri:H(body)` | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.4.3 | MD5 hash algorithm | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.4.3 | SHA-256 hash algorithm | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.4.3 | MD5-sess algorithm variant: `H(A1) = H(H(user:realm:pass):nonce:cnonce)` | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.4.3 | SHA-256-sess algorithm variant | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.5 | `qop=auth` quality-of-protection | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.5 | `qop=auth-int` quality-of-protection | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.6 | Nonce and nonce-count for replay detection | ✅ Implemented | `NonceManagerTest` |
| §3.7 | Stale nonce detection and re-challenge with `stale=true` | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.8 | `Authentication-Info` response header with rspauth, cnonce, nc, qop | ✅ Implemented | `DigestAuthSchemeTest` |
| §3.9 | Proxy authentication (407 + `Proxy-Authenticate` header) | ✅ Implemented | `DigestAuthSchemeTest` |
| §4 | `WWW-Authenticate: Digest` with realm, nonce, algorithm, qop, opaque | ✅ Implemented | `DigestChallengeTest` |

## Test Coverage Summary
- Total compliance tests: 87
- Key test classes: `BasicAuthSchemeTest`, `InMemoryUserStoreTest`, `HashedPasswordStoreTest`, `DigestAuthSchemeTest`, `NonceManagerTest`, `DigestChallengeTest`
