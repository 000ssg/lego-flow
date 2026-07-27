# GSSAPI Module -- Development Guide

## Module Purpose

The `gssapi` module provides a shared GSS-API / Kerberos V5 / SPNEGO authentication layer for Lego Flow. It wraps the JDK's `org.ietf.jgss` and JAAS APIs behind simpler abstractions used by both the SSH module (gssapi-with-mic) and the HTTP Auth SPNEGO sub-module (Negotiate scheme).

## Key Classes

- `GssOids` -- OID constants: KERBEROS_V5 (1.2.840.113554.1.2.2), SPNEGO (1.3.6.1.5.5.2), KRB5_PRINCIPAL_NAME (1.2.840.113554.1.2.2.1)
- `GssConfig` -- Builder-pattern config: realm, kdc, servicePrincipal, keytabPath, useSubjectCredsOnly; `applyAsSystemProperties()` sets JVM krb5 props
- `GssException` -- Checked exception wrapping `org.ietf.jgss.GSSException` with major/minor codes
- `GssContextWrapper` -- AutoCloseable wrapper around `GSSContext`: initSecContext, acceptSecContext, isEstablished, getSrcName, getTargName, wrap, unwrap, getMIC, verifyMIC, dispose
- `GssContextFactory` -- Creates client/server/SPNEGO GSSContext instances via GSSManager
- `SpnegoTokenHandler` -- SPNEGO token processing: createNegTokenInit, createNegTokenResp, extractMechToken, isSpnegoToken, encodeBase64, decodeBase64; uses ASN.1 DER encoding per RFC 4178
- `KerberosCredentialManager` -- JAAS-based login: loginWithKeytab, loginWithPassword, getServiceCredential, isCredentialValid, renewCredential

## Package Layout

```
ssg.legoflow.auth.gssapi/
    GssOids.java                   -- OID constants
    GssConfig.java                 -- Builder-pattern configuration
    GssException.java              -- Exception wrapper
    GssContextWrapper.java         -- GSSContext wrapper (AutoCloseable)
    GssContextFactory.java         -- Factory for client/server contexts
    SpnegoTokenHandler.java        -- SPNEGO ASN.1 DER token handling
    KerberosCredentialManager.java -- JAAS credential lifecycle
```

## Dependencies

- `lego-flow-blocks` -- core framework
- `slf4j-api` -- logging facade
- JDK GSS-API (`org.ietf.jgss`) -- no external crypto libraries
- JDK JAAS (`javax.security.auth`) -- for Kerberos login
- JUnit 5 + Mockito + AssertJ for tests

## Testing

- **Total tests**: 105
- Tests run WITHOUT an actual KDC -- they verify parameter validation, OID values, SPNEGO token format, config building, and ASN.1 encoding
- Key test classes: `GssOidsTest` (13), `GssConfigTest` (15), `GssContextFactoryTest` (13), `GssContextWrapperTest` (21), `SpnegoTokenHandlerTest` (30), `KerberosCredentialManagerTest` (13)

Run: `mvn test -pl auth/gssapi -am`

## Consumers

- `ssh` module -- `GssApiAuth` uses `GssContextWrapper` for SSH gssapi-with-mic authentication (RFC 4462)
- `auth/http-auth/spnego` module -- `NegotiateAuthScheme` uses `GssContextWrapper` and `SpnegoTokenHandler` for HTTP Negotiate (SPNEGO) authentication

---

**Last Updated**: 2026-06-26
**For AI assistant versions**
