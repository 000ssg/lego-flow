# Lego Flow HTTP Auth SPNEGO

HTTP Negotiate (SPNEGO) authentication per RFC 4559 for the Lego Flow framework.

## Features

- HTTP Negotiate authentication scheme (RFC 4559)
- SPNEGO token extraction from Authorization header
- Base64 token decoding and SPNEGO structure detection
- GSS-API context establishment via the shared gssapi module
- Multi-round-trip authentication support (continuation tokens)
- Realm stripping from Kerberos principal names (configurable)
- Case-insensitive scheme name matching
- WWW-Authenticate: Negotiate challenge generation

## Usage

```java
// Configure SPNEGO with underlying Kerberos settings
var gssConfig = GssConfig.builder()
    .realm("EXAMPLE.COM")
    .kdc("kdc.example.com")
    .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
    .keytabPath("/etc/krb5.keytab")
    .build();

var spnegoConfig = SpnegoConfig.builder()
    .gssConfig(gssConfig)
    .stripRealmFromPrincipal(true)
    .build();

// Or use factory method with defaults
var spnegoConfig = SpnegoConfig.of(gssConfig);

// Create the authentication scheme
var negotiateScheme = new NegotiateAuthScheme(spnegoConfig);

// Register with the auth framework
var registry = new AuthSchemeRegistry();
registry.register(negotiateScheme);
```

## Tests: 29

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [HTTP Auth README](../README.md) | [Auth Module README](../../README.md) | [Root README](../../../README.md)
