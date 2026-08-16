
# Lego Flow Web — Web Protocol Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for HTTP/HTTPS and web service implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [http](http/) | `lego-flow-http` | HTTP/HTTPS protocol |
| [http2](http2/) | `lego-flow-http2` | HTTP/2 protocol |
| [http3](http3/) | `lego-flow-http3` | HTTP/3 (QUIC) protocol |
| [web-services](web-services/) | `lego-flow-web-services` | Web service components |
| [http-proxy](http-proxy/) | `lego-flow-http-proxy` | Forward/reverse HTTP proxy |

## Test Coverage

| Module | Test Files |
|--------|------------|
| http | 64 |
| http2 | 17 |
| http3 | 24 |
| web-services | 13 |
| http-proxy | 17 |
| **Total** | **135** |

## Build Commands

```bash
# Build all web modules
mvn test -pl web/http,web/http2,web/http3,web/web-services,web/http-proxy -am

# Gradle
./gradlew :web:http:test :web:http2:test
```
