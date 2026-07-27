# HTTP Auth Basic/Digest — Architecture

## Basic Auth Flow

```mermaid
graph TD
    A["Request"] --> B["BasicAuthScheme.authenticate()"]
    B --> C["1. Extract 'Basic base64' from Authorization header"]
    C --> D["2. Base64 decode to 'username:password'"]
    D --> E["3. Look up user via AuthContext.UserStore"]
    E --> F["4. Return Success(principal) or Failure(reason)"]
```

## Digest Auth Flow

```mermaid
graph TD
    A["Request"] --> B["DigestAuthScheme.authenticate()"]
    B --> C["1. Extract 'Digest params' from Authorization header"]
    C --> D["2. Parse params (username, realm, nonce, uri, response, nc, cnonce, qop, algorithm)"]
    D --> E["3. Validate nonce via NonceManager<br/>(existence, expiry, nc replay)"]
    E --> F["4. Look up user password via UserStore"]
    F --> G["5. Compute expected response:<br/>H(H(A1):nonce:nc:cnonce:qop:H(A2))"]
    G --> H["6. Compare with provided response"]
    H --> I["7. Return Success/Failure/Challenge(stale)"]
```

## Password Storage

- `InMemoryUserStore`: Plain-text, suitable for testing
- `HashedPasswordStore`: SHA-256 + per-user random salt, hex-encoded storage

## Thread Safety

- NonceManager: ConcurrentHashMap for nonce tracking
- InMemoryUserStore: ConcurrentHashMap
- HashedPasswordStore: ConcurrentHashMap

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../../doc/ARCHITECTURE.md) | [Root README](../../../../README.md)
