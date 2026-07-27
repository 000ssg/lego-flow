# HTTP Auth SSO — Compliance

## Specifications Covered

- **SAML 2.0** — Security Assertion Markup Language 2.0
- **De facto standards** — Reverse proxy SSO header conventions (nginx, Traefik, Apache, Authelia, Authentik, Keycloak Gatekeeper)

## Compliance Matrix

### Reverse Proxy SSO (Header-Based)

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| Header propagation | Authentication headers forwarded to backend (`x-forwarded-user`, `x-forwarded-roles`, etc.) | ✅ Implemented | `ReverseProxySsoTest` |
| Spoofing prevention | Auth headers stripped from untrusted client requests before upstream headers are applied | ✅ Implemented | `AuthHeaderInjectorTest` |
| Configurable headers | Header names are configurable for different proxy implementations | ✅ Implemented | `ReverseProxySsoConfigTest` |
| Trusted proxy IPs | Only requests from configured trusted proxy IP addresses are accepted | ✅ Implemented | `ReverseProxySsoTest` |
| Principal extraction | Principal extracted from proxy headers with roles and attributes | ✅ Implemented | `ReverseProxySsoTest` |

### Reverse Proxy Compatibility

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| nginx | `auth_request` / `X-Forwarded-User` | ✅ Compatible | `ReverseProxySsoTest` |
| Traefik | `ForwardAuth` middleware | ✅ Compatible | `ReverseProxySsoTest` |
| Apache | `mod_auth_openidc` headers | ✅ Compatible | `ReverseProxySsoTest` |
| Authelia | Header-based auth propagation | ✅ Compatible | `ReverseProxySsoTest` |
| Authentik | Header-based auth propagation | ✅ Compatible | `ReverseProxySsoTest` |
| Keycloak Gatekeeper | Header-based auth propagation | ✅ Compatible | `ReverseProxySsoTest` |

### SAML 2.0 — Assertion Parsing

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| NameID | Parsing of `<saml:NameID>` element | ✅ Implemented | `SamlAssertionParserTest` |
| Issuer | Parsing of `<saml:Issuer>` element | ✅ Implemented | `SamlAssertionParserTest` |
| AttributeStatement | Parsing of `<saml:AttributeStatement>` with Name/AttributeValue pairs | ✅ Implemented | `SamlAssertionParserTest` |
| Conditions | `NotBefore` and `NotOnOrAfter` extraction from `<saml:Conditions>` | ✅ Implemented | `SamlAssertionParserTest` |
| Namespace support | `saml:`, `saml2:`, and unprefixed element names | ✅ Implemented | `SamlAssertionParserTest` |
| Issuer validation | Issuer checked against configured `entityId` | ✅ Implemented | `SamlAssertionParserTest` |
| Base64 decoding | Base64-encoded SAML Response decoding support | ✅ Implemented | `SamlAssertionParserTest` |
| Principal conversion | NameID as principal name; `Role`/`roles`/`memberOf` attributes as roles | ✅ Implemented | `SamlAssertionParserTest` |

### SAML 2.0 — AuthnRequest Generation

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| AuthnRequest XML | Generate AuthnRequest with ID, IssueInstant, ACS URL, Issuer, Destination | ✅ Implemented | `SamlAuthnRequestTest` |
| Redirect binding | Deflate + Base64 encoding for HTTP-Redirect binding | ✅ Implemented | `SamlAuthnRequestTest` |
| POST binding form | Auto-submitting HTML form with SAMLRequest hidden field | ✅ Implemented | `SamlAuthnRequestTest`, `SamlPostBindingTest` |
| NameIDPolicy | Optional NameID format in AuthnRequest | ✅ Implemented | `SamlAuthnRequestTest` |
| XML escaping | Proper XML entity escaping in generated XML | ✅ Implemented | `SamlAuthnRequestTest` |

### SAML 2.0 — XML Signature Validation

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| RSA-SHA256 signature verification | Verify `ds:SignatureValue` using X.509 public key | ✅ Implemented | `SamlSignatureValidatorTest` |
| SignedInfo extraction | Extract `ds:SignedInfo` element for verification | ✅ Implemented | `SamlSignatureValidatorTest` |
| DigestValue extraction | Extract `ds:DigestValue` for integrity check | ✅ Implemented | `SamlSignatureValidatorTest` |
| Algorithm detection | Parse `ds:SignatureMethod Algorithm` (RSA-SHA1, RSA-SHA256, RSA-SHA384, RSA-SHA512) | ✅ Implemented | `SamlSignatureValidatorTest` |
| PEM certificate parsing | Create validator from PEM-encoded X.509 certificate | ✅ Implemented | `SamlSignatureValidatorTest` |
| Namespace support | `ds:` prefixed and unprefixed element support | ✅ Implemented | `SamlSignatureValidatorTest` |

### SAML 2.0 — Logout

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| LogoutRequest generation | Generate LogoutRequest with Issuer, NameID, SessionIndex | ✅ Implemented | `SamlLogoutTest` |
| LogoutResponse parsing | Parse LogoutResponse with StatusCode, InResponseTo, Issuer | ✅ Implemented | `SamlLogoutTest` |
| Success/failure detection | Determine logout success from StatusCode value | ✅ Implemented | `SamlLogoutTest` |
| Config-based generation | Generate from SamlConfig | ✅ Implemented | `SamlLogoutTest` |

### SAML 2.0 — Encrypted Assertions

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| RSA-OAEP key unwrap | Decrypt AES key from `xenc:EncryptedKey` using RSA-OAEP | ✅ Implemented | `SamlEncryptedAssertionTest` |
| AES-CBC decryption | Decrypt assertion content using AES-128-CBC/AES-256-CBC | ✅ Implemented | `SamlEncryptedAssertionTest` |
| AES-GCM support | AES-GCM mode decryption support | ✅ Implemented | `SamlEncryptedAssertionTest` |
| Algorithm extraction | Parse key and data encryption algorithm URIs from XML | ✅ Implemented | `SamlEncryptedAssertionTest` |
| Namespace support | `xenc:` prefixed and unprefixed element support | ✅ Implemented | `SamlEncryptedAssertionTest` |

## Known Limitations

| Feature | Reason |
|---------|--------|
| Artifact binding | Complex SOAP backchannel implementation; out of scope for current lightweight approach |
| Full Single Logout (SLO) protocol | Requires front-channel + back-channel coordination with multiple SPs; LogoutRequest/Response generation and parsing are implemented for basic SLO |

### SSO Session Federation

| Section | Requirement | Status | Verification |
|---------|-------------|--------|--------------|
| JWT-based SSO | SSO session issued as JWT cookie | ✅ Implemented | `SsoManagerTest` |
| Login | Set SSO cookie on successful authentication | ✅ Implemented | `SsoManagerTest` |
| Session validation | Validate SSO cookie and return principal | ✅ Implemented | `SsoManagerTest` |
| Logout | Clear SSO cookie and propagate to trusted services | ✅ Implemented | `SsoManagerTest` |
| Federated tracking | `SsoSession` tracks authenticated services, attributes | ✅ Implemented | `SsoSessionTest` |
| Resource cleanup | `SsoManager` implements `AutoCloseable` | ✅ Implemented | `SsoManagerTest` |

## Known Limitations
- Artifact binding — complex SOAP backchannel implementation; out of scope for current lightweight approach
- Full Single Logout (SLO) protocol — requires front-channel + back-channel coordination with multiple SPs; LogoutRequest/Response generation and parsing are implemented for basic SLO
- SAML parser uses string-based XML extraction rather than a full XML parser; sufficient for assertion parsing in typical SSO integrations

## Test Coverage Summary
- Total compliance tests: 151
- Key test classes: `SsoManagerTest`, `SsoSessionTest`, `ReverseProxySsoTest`, `AuthHeaderInjectorTest`, `ReverseProxySsoConfigTest`, `SamlAssertionParserTest`, `SamlConfigTest`, `SamlAuthnRequestTest`, `SamlPostBindingTest`, `SamlSignatureValidatorTest`, `SamlLogoutTest`, `SamlEncryptedAssertionTest`
