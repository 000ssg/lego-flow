
# web — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The `web` module is a parent POM (packaging=pom) that groups all HTTP/HTTPS and web service sub-modules under a single build hierarchy.

## Module Structure

```
web/                             <- parent POM (lego-flow-web)
  http/                          <- HTTP/1.1 and HTTPS protocol
  http2/                         <- HTTP/2 (h2, h2c) protocol
  http3/                         <- HTTP/3 over QUIC protocol
  web-services/                  <- Web service components
  http-proxy/                    <- Forward and reverse HTTP proxy
```

## Parent Chain

```
lego-flow (root)
  -> lego-flow-web (web/pom.xml)
      -> lego-flow-http (web/http/pom.xml)
      -> lego-flow-http2 (web/http2/pom.xml)
      -> lego-flow-http3 (web/http3/pom.xml)
      -> lego-flow-web-services (web/web-services/pom.xml)
      -> lego-flow-http-proxy (web/http-proxy/pom.xml)
```

## Test Counts

| Module | Test Files |
|--------|------------|
| http | 64 |
| http2 | 17 |
| http3 | 24 |
| web-services | 13 |
| http-proxy | 17 |

## Build Commands

```bash
# Build all web modules
mvn test -pl web/http,web/http2,web/http3,web/web-services,web/http-proxy -am

# Build single module
mvn test -pl web/http -am
```
