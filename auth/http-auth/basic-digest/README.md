# Lego Flow HTTP Auth Basic/Digest

HTTP Basic Authentication (RFC 7617) and HTTP Digest Authentication (RFC 7616) for the Lego Flow framework.

## Features

- HTTP Basic Auth with Base64 credential decoding
- In-memory user store with plain and hashed password support
- SHA-256 password hashing with per-user salt
- HTTP Digest Auth with MD5 and SHA-256 algorithms
- Quality of Protection: auth and auth-int
- Nonce management with expiry and replay detection
- Nonce count validation to prevent replay attacks

## Usage

```java
// Basic Auth
var userStore = new InMemoryUserStore()
    .addUser("alice", "password", Set.of("admin"));
var basicScheme = new BasicAuthScheme();
var context = new AuthContext("realm", userStore, null);

// Digest Auth
var nonceManager = new NonceManager(Duration.ofMinutes(5));
var digestScheme = new DigestAuthScheme(nonceManager, "SHA-256", "auth");
```

## Tests: 73

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [HTTP Auth README](../README.md) | [Auth Module README](../../README.md) | [Root README](../../../README.md)
