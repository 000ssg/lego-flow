# CoAP Compliance Report

## Specifications Covered
- RFC 7252 — The Constrained Application Protocol (CoAP)
- RFC 7641 — Observing Resources in the Constrained Application Protocol (CoAP)
- RFC 7959 — Block-Wise Transfers in the Constrained Application Protocol (CoAP)
- RFC 6690 — Constrained RESTful Environments (CoRE) Link Format

## Compliance Matrix

### RFC 7252 — CoAP Core

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §3 | Message format (4-byte header: version, type, token length, code, message ID) | ✅ Implemented | `CoapMessage`, `CoapCodec`; `CoapMessageTest`, `CoapCodecTest` |
| §3 | Message types (CON, NON, ACK, RST) | ✅ Implemented | `CoapType` enum with 4 types; `CoapMessageTest` |
| §3 | CoAP version (1) | ✅ Implemented | `CoapVersion`; `CoapCodecTest` |
| §3 | Token (0-8 bytes) | ✅ Implemented | `CoapMessage` with token; `CoapMessageTest` |
| §3.1 | Request/response codes (class.detail encoding) | ✅ Implemented | `CoapCode` with class/detail encoding; `CoapCodeTest` |
| §4.1 | Confirmable messages (CON) requiring ACK | ✅ Implemented | `CoapType.CONFIRMABLE`; `CoapServerTest`, `CoapClientTest` |
| §4.2 | Non-confirmable messages (NON) | ✅ Implemented | `CoapType.NON_CONFIRMABLE`; `CoapClientTest` |
| §4.3 | Acknowledgement messages (ACK, piggybacked response) | ✅ Implemented | `CoapType.ACKNOWLEDGEMENT`; `CoapServerTest` |
| §4.4 | Reset messages (RST) | ✅ Implemented | `CoapType.RESET`; `CoapMessageTest` |
| §4.5 | Message deduplication (by Message ID) | ✅ Implemented | Per CLAUDE.md: MessageLayer deduplication; server tests |
| §4.7 | Retransmission with exponential backoff | ✅ Implemented | Per CLAUDE.md: MessageLayer retransmission; reliability tests |
| §4.7 | NSTART congestion control | ✅ Implemented | Per CLAUDE.md: MessageLayer congestion control; reliability tests |
| §5.2 | Request methods | ✅ Implemented | | |
| §5.2 | GET method (0.01) | ✅ Implemented | `CoapCode.GET`; `CoapClientTest`, `SimpleServerDemoTest` |
| §5.2 | POST method (0.02) | ✅ Implemented | `CoapCode.POST`; `CoapRestDemoTest` |
| §5.2 | PUT method (0.03) | ✅ Implemented | `CoapCode.PUT`; `CoapRestDemoTest` |
| §5.2 | DELETE method (0.04) | ✅ Implemented | `CoapCode.DELETE`; `CoapRestDemoTest` |
| §5.9 | Response codes — Success (2.xx) | ✅ Implemented | `CoapCode.CREATED/DELETED/VALID/CHANGED/CONTENT/CONTINUE`; `CoapCodeTest` |
| §5.9 | Response codes — Client Error (4.xx) | ✅ Implemented | `CoapCode.BAD_REQUEST/UNAUTHORIZED/NOT_FOUND/METHOD_NOT_ALLOWED` etc.; `CoapCodeTest` |
| §5.9 | Response codes — Server Error (5.xx) | ✅ Implemented | `CoapCode.INTERNAL_SERVER_ERROR/NOT_IMPLEMENTED/BAD_GATEWAY` etc.; `CoapCodeTest` |
| §5.4 | Options processing | ✅ Implemented | `CoapOption`, `CoapOptionRegistry`; `CoapOptionTest`, `CoapOptionCodecTest` |
| §5.4.1 | Critical vs elective options | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionTest` |
| §5.4.2 | Proxy-unsafe vs safe-to-forward options | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionTest` |
| §5.4.5 | Uri-Host option (3) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4.6 | Uri-Port option (7) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4.4 | Uri-Path option (11) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4.7 | Uri-Query option (15) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4 | Content-Format option (12) | ✅ Implemented | `ContentFormat`; `ContentFormatTest` |
| §5.4 | Accept option (17) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4 | Max-Age option (14) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4 | ETag option (4) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4 | If-Match option (1) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.4 | If-None-Match option (5) | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §5.10 | Content-Format values (text/plain=0, link-format=40, xml=41, octet-stream=42, json=50, cbor=60) | ✅ Implemented | `ContentFormat` with standard formats; `ContentFormatTest` |
| §5.8 | Content negotiation (Accept option) | ✅ Implemented | `CoapResource` with content format negotiation; `CoapResourceTest` |
| §5.4.3 | Option delta encoding | ✅ Implemented | `CoapOptionCodec`; `CoapOptionCodecTest` |
| §7.1 | Separate response (ACK then later response) | ✅ Implemented | `CoapExchange.respondSeparate()`, `CoapServer` queues separate CON response after empty ACK; `CoapServerTest` |
| §7.2 | Proxying (forward-proxy, reverse-proxy) | ❌ Not Implemented | Intentionally skipped — no proxy support planned for this module |
| §8 | Multicast CoAP | ✅ Implemented | `CoapServer.joinMulticastGroup()`, NON enforcement for multicast responses, multicast address detection; `CoapServerTest` |

### RFC 7252 — CoAP Server/Client

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §5.7 | Resource handler abstraction | ✅ Implemented | `CoapResource`, `CoapExchange`; `CoapResourceTest` |
| §5.7 | URI path-based routing | ✅ Implemented | `CoapServer` with resource registry; `CoapServerTest` |
| §5.7 | Resource attributes | ✅ Implemented | `ResourceAttributes`; `CoapResourceTest` |
| §5 | Server configuration | ✅ Implemented | `CoapServerConfig`; server tests |
| §5 | Client request construction | ✅ Implemented | `CoapClient`; `CoapClientTest` |
| §5 | Client configuration | ✅ Implemented | `CoapClientConfig`; `CoapClientConfigTest` |
| §5 | Client response handling | ✅ Implemented | `CoapResponse`; `CoapClientTest` |

### RFC 7641 — Observing Resources

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §2 | Observe option (option number 6) | ✅ Implemented | Observe option in registry; `ObserveRegistryTest` |
| §3.1 | Register observation (GET with Observe=0) | ✅ Implemented | `ObserveRelation`; `ObserveRelationTest`, `ObserveDemoTest` |
| §3.2 | Deregister observation (GET with Observe=1 or RST) | ✅ Implemented | `ObserveRegistry`; `ObserveRegistryTest` |
| §3.3 | Notification delivery | ✅ Implemented | `ObserveRegistry` notification; `ObserveRegistryTest`, `ObserveDemoTest` |
| §3.4 | Notification ordering (observe sequence number) | ✅ Implemented | Per CLAUDE.md: notification ordering; `ObserveRegistryTest` |
| §3.5 | Max-Age expiry of observations | ✅ Implemented | Per CLAUDE.md: max-age expiry; `ObserveRegistryTest` |
| §4.5 | Client observe handler | ✅ Implemented | `CoapObserveHandler`; `CoapClientTest`, `ObserveDemoTest` |

### RFC 7959 — Block-Wise Transfers

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §2.1 | Block2 option (option number 23) — response payload | ✅ Implemented | `BlockOption`; `BlockOptionTest` |
| §2.2 | Block1 option (option number 27) — request payload | ✅ Implemented | `BlockOption`; `BlockOptionTest` |
| §2.3 | Block size negotiation (16 to 1024 bytes) | ✅ Implemented | `BlockOption` with SZX encoding; `BlockOptionTest` |
| §2 | Block number encoding | ✅ Implemented | `BlockOption`; `BlockOptionTest` |
| §2 | More flag (M bit) | ✅ Implemented | `BlockOption`; `BlockOptionTest` |
| §2 | Block assembly | ✅ Implemented | `BlockTransfer`; `BlockTransferTest`, `BlockTransferDemoTest` |
| §2 | Size1 option (60) — request size indication | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |
| §2 | Size2 option (28) — response size indication | ✅ Implemented | `CoapOptionRegistry`; `CoapOptionCodecTest` |

### RFC 6690 — CoRE Link Format

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §2 | Link format syntax (RFC 5988 relation) | ✅ Implemented | `LinkFormatParser`; `LinkFormatParserTest` |
| §2 | Resource type (rt) attribute | ✅ Implemented | `LinkFormatEntry`; `LinkFormatParserTest` |
| §2 | Interface description (if) attribute | ✅ Implemented | `LinkFormatEntry`; `LinkFormatParserTest` |
| §2 | Content-type (ct) attribute | ✅ Implemented | `LinkFormatEntry`; `LinkFormatParserTest` |
| §2 | Resource size (sz) attribute | ✅ Implemented | `LinkFormatEntry`; `LinkFormatParserTest` |
| §4 | /.well-known/core discovery endpoint | ✅ Implemented | `WellKnownCoreResource`; `WellKnownCoreResourceTest`, `DiscoveryDemoTest` |
| §4 | Discovery via multicast | ✅ Implemented | `CoapDiscovery`, `CoapServer.joinMulticastGroup()`; `DiscoveryDemoTest`, `CoapServerTest` |
| §2 | Link format generation from resource registry | ✅ Implemented | `WellKnownCoreResource`, `CoapDiscovery`; `WellKnownCoreResourceTest` |

## Known Limitations
- **No DTLS support** — CoAP security (CoAPS) over DTLS is not implemented; communication is unencrypted
- **No proxy support** — forward and reverse proxying (RFC 7252 §7.2) not implemented
- **Proxy support** — forward and reverse proxying (RFC 7252 §7.2) intentionally not implemented
- **No OSCORE** — Object Security for Constrained RESTful Environments (RFC 8613) not implemented
- **No CoAP over TCP/TLS** — only UDP transport (RFC 8323 not implemented)
- **No cross-proxy (CoAP-to-HTTP)** — no protocol translation proxy
- **Congestion control is simplified** — NSTART-based model present but not production-grade
- **No CoAP group communication** — RFC 7390 group requests not implemented
- **Block transfer is tested in isolation** — concurrent block transfers and recovery from lost blocks are simplified
- **FETCH, PATCH, iPATCH methods** — defined in `CoapCode` but not exercised by server routing (RFC 8132)
- **No resource observation with conditions** — simple notification model only

## Test Coverage Summary
- Total compliance tests: 156 (per CLAUDE.md)
- Key unit test classes: `CoapMessageTest`, `CoapCodeTest`, `CoapCodecTest`, `CoapOptionTest`, `CoapOptionCodecTest`, `ContentFormatTest`, `BlockOptionTest`, `BlockTransferTest`, `ObserveRegistryTest`, `ObserveRelationTest`, `LinkFormatParserTest`, `WellKnownCoreResourceTest`, `CoapResourceTest`, `CoapServerTest`, `CoapClientTest`, `CoapClientConfigTest`
- Key demo test classes: `SimpleServerDemoTest`, `CoapRestDemoTest`, `ObserveDemoTest`, `BlockTransferDemoTest`, `DiscoveryDemoTest`, `IoTGatewayDemoTest`
- Sections fully covered: Message format (§3), Message types CON/NON/ACK/RST (§4), Request methods GET/POST/PUT/DELETE (§5.2), Response codes (§5.9), Options processing (§5.4), Content-Format (§5.10), Observe (RFC 7641), Block-wise transfers (RFC 7959), CoRE Link Format (RFC 6690)
- Key areas needing improvement: DTLS security, proxy support, separate responses, CoAP over TCP, group communication, OSCORE
