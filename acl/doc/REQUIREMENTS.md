# ACL Requirements

Append-only history.

## Commit: (acl) — ACL Module (2026-09-02)

### Request
> "create new module 'acl' under root with generic ACL management engine: users, groups, roles, domains, certificates, ACL rules (named resource access with control/accessLevel), config loaders (properties, YAML, JSON, XML), pre-built TestDomain with 10-year certs, SSL testing utilities"

### Implementation
- Domain model: AclDomain, User, Group, Role, AclRule, CertificateEntry
- Certificate generation: self-signed, CA-signed, PKCS12 keystores
- SSL utilities: SSLContext/SSLEngine creation from domain certs
- 4 config loaders: Properties, JSON, YAML, XML (JDK-only parsers)
- AclDomainBuilder: fluent API
- TestDomain: pre-built with 4 roles, 4 groups, 9 users, ACL rules, CA-signed certs
- Comprehensive tests: model, resolution, ACL rules, certs, SSL handshake, config loaders
