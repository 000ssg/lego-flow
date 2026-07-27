# HTTP Auth SSO — Architecture

## SSO Federation Flow

```mermaid
graph TD
    A["1. User authenticates at IdP"] --> B["2. SsoManager.login(principal, response)"]
    B --> C["Create SsoSession with unique ID"]
    C --> D["Generate JWT with session_id + roles"]
    D --> E["Set SSO cookie (Domain, HttpOnly, Secure, SameSite)"]

    F["3. User visits service"] --> G["4. SsoManager.validateSession(request)"]
    G --> H["Extract cookie → Validate JWT → Look up session"]
    H --> I["Check expiry → Touch session → Return session"]

    J["5. User logs out"] --> K["6. SsoManager.logout(request, response)"]
    K --> L["Validate JWT → Remove session → Invalidate → Clear cookie"]
    L --> M["Return set of authenticated services<br/>for logout propagation"]
```

## Reverse Proxy SSO Flow

```mermaid
graph LR
    Client --> Proxy["Reverse Proxy"]
    Proxy -->|"AuthHeaderInjector.stripHeaders()<br/>AuthHeaderInjector.injectHeaders()<br/>x-forwarded-user/roles/email"| Backend
```

## SAML Assertion Parsing

```mermaid
graph TD
    A["SAML Response XML"] --> B["SamlAssertionParser.parseResponse()"]
    B --> C["1. Extract Issuer → validate against SamlConfig.entityId"]
    C --> D["2. Extract NameID → subject identifier"]
    D --> E["3. Extract Conditions → NotBefore, NotOnOrAfter"]
    E --> F["4. Extract Attributes → name/value pairs"]
    F --> G["SamlAssertion record"]
    G --> H["toPrincipal() → AuthPrincipal<br/>(roles from Role/roles/memberOf attributes)"]
```

## Thread Safety

- SsoSession: ConcurrentHashMap for attributes, ConcurrentHashMap.newKeySet() for services, volatile for lastAccessedAt and invalidated
- SsoManager: ConcurrentHashMap for sessions

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../../doc/ARCHITECTURE.md) | [Root README](../../../../README.md)
