# auth — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `auth` module is a parent POM (packaging=pom) that groups all authentication-related sub-modules under a single build hierarchy. It sits between the root `lego-flow` POM and the individual auth modules.

## Module Structure

```
auth/                          <- parent POM (lego-flow-auth)
  gssapi/                      <- GSS-API / Kerberos V5 / SPNEGO primitives
  http-auth/                   <- HTTP authentication (parent POM for sub-modules)
    core/                      <- Auth framework, sessions, JWT
    basic-digest/              <- HTTP Basic (RFC 7617) + Digest (RFC 7616)
    oauth/                     <- OAuth 2.0, PKCE, OpenID Connect, provider templates
    sso/                       <- Reverse proxy SSO, SAML assertion parsing
    spnego/                    <- HTTP Negotiate (SPNEGO) authentication scheme
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-auth (auth/pom.xml)
      -> lego-flow-gssapi (auth/gssapi/pom.xml)
      -> lego-flow-http-auth (auth/http-auth/pom.xml)
          -> lego-flow-http-auth-core
          -> lego-flow-http-auth-basic-digest
          -> lego-flow-http-auth-oauth
          -> lego-flow-http-auth-sso
          -> lego-flow-http-auth-spnego
```

## Test Counts

| Module | Tests |
|--------|-------|
| gssapi | 105 |
| http-auth/core | 134 |
| http-auth/basic-digest | 87 |
| http-auth/oauth | 205 |
| http-auth/sso | 151 |
| http-auth/spnego | 29 |

## Build Commands

```bash
# Build all auth modules
mvn test -pl auth/gssapi,auth/http-auth/core,auth/http-auth/basic-digest,auth/http-auth/oauth,auth/http-auth/sso -am

# Build single module
mvn test -pl auth/gssapi -am
```
