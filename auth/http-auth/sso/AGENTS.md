# auth / http-auth / sso — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

Implements Single Sign-On federation, reverse proxy SSO with header injection, and SAML 2.0 (assertion parsing, AuthnRequest generation, XML signature validation, logout, encrypted assertions).

## Key Classes

### SSO Federation
- `SsoConfig` — Domain, cookie name, session timeout, trusted services, secure cookies
- `SsoSession` — Federated session tracking authenticated services, attributes
- `SsoManager` — JWT-based SSO: login (set cookie), validateSession (check cookie), logout (clear + propagate), AutoCloseable

### Reverse Proxy SSO
- `ReverseProxySsoConfig` — Header names (user, roles, email, name), trusted proxies
- `AuthHeaderInjector` — Inject/strip auth headers for backend forwarding
- `ReverseProxySso` — Extract principal from proxy headers, prepare backend requests

### SAML 2.0
- `SamlConfig` — Record(entityId, ssoUrl, certificate, nameIdFormat)
- `SamlAssertionParser` — Parse SAML Response XML: NameID, Issuer, Attributes, Conditions
- `SamlAssertion` — Record with toPrincipal() conversion
- `SamlAuthnRequest` — Generate AuthnRequest XML with ID, IssueInstant, ACS URL, Issuer; deflate+base64 for redirect binding
- `SamlPostBinding` — Generate auto-submitting HTML forms for POST binding (SAMLRequest/SAMLResponse)
- `SamlSignatureValidator` — Validate XML Signature on SAML Response/Assertion using X.509 certificate (RSA-SHA256)
- `SamlLogout` — Generate LogoutRequest, parse LogoutResponse with status
- `SamlEncryptedAssertion` — Decrypt EncryptedAssertion using RSA-OAEP key unwrap + AES-CBC/GCM decryption

## Tests: 151

Run: `mvn test -pl http-auth/sso -am`
