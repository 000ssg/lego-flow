
# Lego Flow Media — Media Protocol Modules

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)

Aggregator module for media and streaming protocol implementations in the Lego Flow framework.

## Sub-modules

| Module | Artifact | Description |
|--------|----------|-------------|
| [common](common/) | `lego-flow-media-common` | SDP parser (RFC 4566) |
| [rtsp](rtsp/) | `lego-flow-rtsp` | RTSP 2.0 (RFC 7826) |
| [rtp](rtp/) | `lego-flow-rtp` | RTP (RFC 3550) |
| [sip](sip/) | `lego-flow-sip` | SIP (RFC 3261) |

## Test Coverage

| Module | Test Files |
|--------|------------|
| common | 20 |
| rtsp | 20 |
| rtp | 9 |
| sip | 17 |
| **Total** | **66** |

## Build Commands

```bash
# Build all media modules
mvn test -pl media/common,media/rtsp,media/rtp,media/sip -am

# Gradle
./gradlew :media:common:test :media:rtsp:test
```
