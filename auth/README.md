
# Lego Flow Auth — Authentication Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for authentication and security implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [gssapi](gssapi/) | `lego-flow-gssapi` | GSS-API / Kerberos V5 / SPNEGO primitives |
| [http-auth](http-auth/) | `lego-flow-http-auth` | HTTP authentication (parent POM) |
|   └ [core](http-auth/core/) | `lego-flow-http-auth-core` | Auth framework, sessions, JWT |
|   └ [basic-digest](http-auth/basic-digest/) | `lego-flow-http-auth-basic-digest` | Basic (RFC 7617) + Digest (RFC 7616) |
|   └ [oauth](http-auth/oauth/) | `lego-flow-http-auth-oauth` | OAuth 2.0, PKCE, OpenID Connect |
|   └ [sso](http-auth/sso/) | `lego-flow-http-auth-sso` | Reverse proxy SSO, SAML |
|   └ [spnego](http-auth/spnego/) | `lego-flow-http-auth-spnego` | HTTP Negotiate (SPNEGO) |

## Test Coverage

| Module | Test Files |
|--------|------------|
| gssapi | 105 |
| http-auth/core | 134 |
| http-auth/basic-digest | 87 |
| http-auth/oauth | 205 |
| http-auth/sso | 151 |
| http-auth/spnego | 29 |
| **Total** | **711** |

## Build Commands

```bash
# Build all auth modules
mvn test -pl auth/gssapi,auth/http-auth/core,auth/http-auth/basic-digest,auth/http-auth/oauth,auth/http-auth/sso,auth/http-auth/spnego -am

# Gradle
./gradlew :auth:gssapi:test :auth:http-auth:core:test
```
