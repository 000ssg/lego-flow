# HTTP Auth SSO — Requirements

## Original Request

> Implement SSO federation with JWT-based sessions, reverse proxy SSO with header injection, and SAML 2.0 assertion parsing.

## Requirements

1. SsoConfig with domain, cookie name, session timeout, trusted services, secure cookie flag
2. SsoSession tracking authenticated services and session attributes
3. SsoManager with login (JWT cookie), validateSession (cookie extraction + JWT validation), logout (invalidate + clear cookie + return services for propagation)
4. SsoManager implements AutoCloseable for cleanup
5. Expired session cleanup
6. ReverseProxySsoConfig with customizable header names and trusted proxies
7. AuthHeaderInjector for injecting user/roles/email/name headers
8. Header stripping to prevent header spoofing from untrusted sources
9. ReverseProxySso extracting AuthPrincipal from proxy headers
10. Backend request preparation: strip then inject
11. SamlConfig record with entityId, ssoUrl, certificate, nameIdFormat
12. SamlAssertionParser parsing NameID, Issuer, Attributes, Conditions from XML
13. Base64-encoded SAML response support
14. Issuer validation against config
15. Principal conversion from SAML assertions with role extraction
16. All public classes with Javadoc @since 1.0.0

## Implementation

- 8 test classes, 103 tests total
- SAML XML parsing uses simple string searching (no external XML library)
- Supports saml:, saml2:, and unprefixed namespace variants
