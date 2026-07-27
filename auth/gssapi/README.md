# Lego Flow GSSAPI

![Java](https://img.shields.io/badge/Java-24%2B-blue)
![Maven](https://img.shields.io/badge/Maven-3.9%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Tests](https://img.shields.io/badge/Tests-105-brightgreen)
![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-orange)

Shared GSS-API / Kerberos V5 / SPNEGO authentication module for the Lego Flow framework. Wraps the JDK's `org.ietf.jgss` and JAAS APIs behind simpler abstractions used by SSH (gssapi-with-mic) and HTTP (Negotiate/SPNEGO) modules.

## Features

- **OID Constants**: Kerberos V5, SPNEGO, and KRB5 principal name OIDs
- **Builder-Pattern Configuration**: Realm, KDC, service principal, keytab, system property application
- **Context Wrapper**: AutoCloseable wrapper for GSSContext with simplified token exchange
- **Context Factory**: Client and server GSS context creation with Kerberos V5 or SPNEGO mechanisms
- **SPNEGO Token Handling**: ASN.1 DER encoding/decoding for NegTokenInit/NegTokenResp (RFC 4178)
- **Credential Management**: JAAS-based keytab and password login, credential validation and renewal
- **No External Dependencies**: Uses only JDK GSS-API and JAAS (no third-party crypto)

## Quick Start

### Configuration

```java
GssConfig config = GssConfig.builder()
        .realm("EXAMPLE.COM")
        .kdc("kdc.example.com")
        .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
        .keytabPath("/etc/krb5.keytab")
        .useSubjectCredsOnly(true)
        .build();

config.applyAsSystemProperties();
```

### Client Context

```java
try (GssContextWrapper ctx = GssContextFactory.createClientContext(config, "host/server.example.com@EXAMPLE.COM")) {
    byte[] token = ctx.initSecContext(new byte[0]);
    // send token to server, receive response...
    byte[] response = ctx.initSecContext(serverToken);
    if (ctx.isEstablished()) {
        String sourcePrincipal = ctx.getSrcName();
    }
}
```

### SPNEGO Tokens

```java
byte[] spnegoInit = SpnegoTokenHandler.createNegTokenInit(mechToken);
String base64Token = SpnegoTokenHandler.encodeBase64(spnegoInit);

// On server side
byte[] decoded = SpnegoTokenHandler.decodeBase64(base64Token);
if (SpnegoTokenHandler.isSpnegoToken(decoded)) {
    byte[] innerToken = SpnegoTokenHandler.extractMechToken(decoded);
}
```

### Kerberos Login

```java
Subject subject = KerberosCredentialManager.loginWithKeytab(
        "HTTP/server.example.com@EXAMPLE.COM", "/etc/krb5.keytab");
GSSCredential cred = KerberosCredentialManager.getServiceCredential(subject, config);
```

## Module Structure

| Class | Purpose |
|-------|---------|
| `GssOids` | Standard OID constants for GSS-API mechanisms |
| `GssConfig` | Builder-pattern Kerberos configuration |
| `GssException` | Checked exception wrapping GSSException |
| `GssContextWrapper` | Simplified GSSContext lifecycle management |
| `GssContextFactory` | Client/server context creation |
| `SpnegoTokenHandler` | SPNEGO ASN.1 DER token processing |
| `KerberosCredentialManager` | JAAS-based credential lifecycle |

## Testing

All 105 tests run without an actual KDC, verifying parameter validation, OID values, SPNEGO token format, and configuration building.

```bash
mvn test -pl auth/gssapi -am
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Auth Module README](../README.md) | [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
