# GSSAPI Module Requirements

## Commit: Initial — GSSAPI Shared Module (2026-06-26)

### Original Request
> Create a shared GSSAPI module under auth/gssapi/ that provides Kerberos V5 and SPNEGO authentication primitives for use by SSH (gssapi-with-mic) and HTTP (Negotiate/SPNEGO) modules.

### Reformulated Requirements
1. Provide OID constants for Kerberos V5, SPNEGO, and KRB5 principal name
2. Builder-pattern configuration for realm, KDC, service principal, keytab, useSubjectCredsOnly
3. Apply configuration as JVM system properties for krb5 subsystem
4. AutoCloseable wrapper around GSSContext for simplified token exchange (init, accept, wrap, unwrap, MIC)
5. Factory for creating client and server GSS contexts with Kerberos V5 or SPNEGO mechanisms
6. SPNEGO token handling: NegTokenInit/NegTokenResp creation, mechToken extraction, isSpnegoToken detection, Base64 encode/decode
7. ASN.1 DER encoding for SPNEGO tokens per RFC 4178
8. JAAS-based Kerberos credential management: keytab login, password login, service credential acquisition, validation, renewal
9. Exception wrapper for GSSException with major/minor status codes
10. All tests must run without an actual KDC

### Final Design Decisions
- Use JDK's built-in `org.ietf.jgss` API -- no external GSS-API library
- Use JAAS `LoginContext` with `Krb5LoginModule` for credential management
- Hand-code ASN.1 DER encoding for SPNEGO tokens to avoid ASN.1 library dependency
- Utility class pattern (private constructors) for stateless helpers (GssOids, GssContextFactory, SpnegoTokenHandler, KerberosCredentialManager)
- GssContextWrapper is the only stateful class, implementing AutoCloseable

### Implementation Details
- Files created: 7 source files, 6 test files
- Package: `ssg.legoflow.auth.gssapi`
- Parent POM: `lego-flow-auth`
- Dependencies: lego-flow-blocks, slf4j-api

### Test Coverage
- GssOidsTest: 13 tests -- OID string values, types, nullability
- GssConfigTest: 15 tests -- builder, defaults, system properties, required fields, immutability
- GssContextFactoryTest: 13 tests -- parameter validation, OID selection, null handling
- GssContextWrapperTest: 21 tests -- lifecycle, null checks, delegation verification
- SpnegoTokenHandlerTest: 30 tests -- base64, NegTokenInit/Resp ASN.1 format, isSpnegoToken, extractMechToken
- KerberosCredentialManagerTest: 13 tests -- parameter validation, null keytab, credential checking
- **Total: 105 tests**
