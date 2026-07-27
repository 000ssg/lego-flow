# HTTP Auth SPNEGO — Requirements

## Original Request

> Implement HTTP Negotiate (SPNEGO) authentication per RFC 4559, bridging the shared GSSAPI module to HTTP authentication with realm stripping support.

## Requirements

1. NegotiateAuthScheme implementing AuthenticationScheme with scheme name "Negotiate"
2. Extract SPNEGO token from `Authorization: Negotiate <base64>` header
3. Case-insensitive scheme name matching in Authorization header
4. Base64 decode the token; return failure on invalid encoding
5. Detect SPNEGO token wrapper and extract inner mechanism token via SpnegoTokenHandler
6. Create server-side GSS context via GssContextFactory and process mechanism token
7. Return Success with AuthPrincipal on established context
8. Return Challenge with continuation token when context is not yet established
9. WWW-Authenticate: Negotiate challenge header generation
10. Credential extraction returning Bearer-style credentials with the SPNEGO token
11. SpnegoConfig with builder pattern combining GssConfig and stripRealmFromPrincipal (default: true)
12. SpnegoConfig.of(gssConfig) factory method for default configuration
13. Realm stripping: "user@EXAMPLE.COM" becomes "user" when enabled
14. All public classes with Javadoc @since 1.0.0

## Implementation

- 2 test classes, 29 tests total
- Delegates GSS-API context establishment to the shared gssapi module
- Uses GssContextWrapper (AutoCloseable) for proper resource cleanup
