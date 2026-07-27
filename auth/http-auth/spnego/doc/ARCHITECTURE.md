# HTTP Auth SPNEGO — Architecture

## SPNEGO Authentication Flow

```mermaid
graph TD
    A["Request"] --> B["NegotiateAuthScheme.authenticate()"]
    B --> C["1. Extract 'Negotiate base64' from Authorization header"]
    C --> D{"Header present?"}
    D -->|No| E["Return Challenge"]
    D -->|Yes| F["3. Base64 decode token"]
    F --> G{"SPNEGO wrapper?"}
    G -->|Yes| H["Extract inner mechanism token"]
    G -->|No| I["Use raw token bytes"]
    H --> J["5. Create server GSS context"]
    I --> J
    J --> K["6. gssContext.acceptSecContext(mechToken)"]
    K --> L{"Context established?"}
    L -->|Yes| M["Get source name, strip realm<br/>→ Return Success(AuthPrincipal)"]
    L -->|No| N["Return Challenge (continuation)"]
    K -->|GssException| O["Return Failure"]
```

## Credential Extraction Flow

```mermaid
graph TD
    A["Request"] --> B["NegotiateAuthScheme.extractCredentials()"]
    B --> C["1. Check Authorization header for 'Negotiate ' prefix"]
    C --> D{"Token present?"}
    D -->|Yes| E["Return Bearer(token)"]
    D -->|No| F["Return None"]
```

## Configuration Structure

```mermaid
graph TD
    SC["SpnegoConfig"] --> GC["gssConfig: GssConfig"]
    SC --> Strip["stripRealmFromPrincipal: boolean (default: true)"]
    GC --> Realm["realm — Kerberos realm"]
    GC --> KDC["kdc — Key Distribution Center hostname"]
    GC --> SP["servicePrincipal — e.g., HTTP/server@REALM"]
    GC --> KT["keytabPath — Path to keytab file"]
    GC --> USC["useSubjectCredsOnly — JDK GSS property"]
```

## Thread Safety

- NegotiateAuthScheme: immutable after construction, safe for concurrent use
- SpnegoConfig: immutable, safe for concurrent use
- GssContextWrapper: created per-request via try-with-resources, not shared between threads

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../../doc/ARCHITECTURE.md) | [Root README](../../../../README.md)
