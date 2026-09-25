# ACL Module

Module-specific conventions. See [root AGENTS.md](../../AGENTS.md).

## Package Structure

- `ssg.legoflow.acl.model` — Domain model (AclDomain, User, Group, Role, AclRule, CertificateEntry)
- `ssg.legoflow.acl.cert` — Certificate generation (CertificateFactory, DomainCerts)
- `ssg.legoflow.acl.config` — Configuration loaders (Properties, JSON, YAML, XML)
- `ssg.legoflow.acl.ssl` — SSL/TLS utilities (SslContexts)
- `ssg.legoflow.acl` — Builder, TestDomain

## Testing

- Model tests: role resolution, group inheritance, ACL rule evaluation
- Certificate tests: self-signed, CA-signed, PKCS12 generation
- SSL tests: full handshake with SSLEngine (client + server)
- Config loader tests: Properties, JSON, YAML, XML round-trips
