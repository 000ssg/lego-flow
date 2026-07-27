# Lego Flow HTTP Auth Core

Core authentication framework for the Lego Flow HTTP Auth module. Provides the pluggable authentication scheme architecture, session management, and JWT token handling.

## Features

- Sealed `AuthCredentials` and `AuthResult` types for exhaustive pattern matching
- Pluggable `AuthenticationScheme` interface with case-insensitive registry
- `AuthFilter` for automated Authorization header parsing
- `AuthMiddleware` for protecting HTTP handlers with path exclusion and role-based access
- Cookie-based session management with configurable expiry and SameSite support
- JWT token generation/validation from scratch (HS256, RS256) with no external dependencies

## Usage

```java
// Register authentication schemes
var registry = new AuthSchemeRegistry();
registry.register(basicScheme);
registry.register(bearerScheme);

// Protect endpoints
var middleware = AuthMiddleware.builder(registry, handler)
    .realm("api")
    .excludePath("/public")
    .requireRole("admin")
    .build();

// JWT tokens
var jwt = JwtTokenProvider.hmac256("secret-key-32-bytes-minimum!!!!!", "issuer", Duration.ofHours(1));
String token = jwt.generateToken("alice", Map.of("role", "admin"));
Optional<Map<String, Object>> claims = jwt.validateToken(token);
```

## Tests: 134

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [HTTP Auth README](../README.md) | [Auth Module README](../../README.md) | [Root README](../../../README.md)
