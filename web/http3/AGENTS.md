# web / http3 — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The **http3** module is a full HTTP/3 implementation compliant with RFC 9114, built on QUIC transport (RFC 9000) with QPACK header compression (RFC 9204). It covers QUIC connection management, packet encoding/decoding, loss recovery, congestion control, 0-RTT, connection migration, QPACK header compression, HTTP/3 framing, server push, and Alt-Svc discovery. The module plugs into the existing `http` module's feature system via `Http3Feature` and bridges to `HttpRouter` so existing request handlers require no changes.

## Key Interfaces

### QuicConnection / QuicTransport
QUIC connection lifecycle: handshake (TLS 1.3 via SSLEngine with `HandshakePhase` tracking: INITIAL/HANDSHAKE/ESTABLISHED/CLOSED), stream creation, packet send/receive, connection close. Stores negotiated ALPN ("h3"), cipher suite, protocol version, and peer certificates. `QuicTransport` wraps UDP I/O using the service module's `UdpDataChannel`.

### QuicStream
Bidirectional or unidirectional QUIC stream. Supports ordered byte delivery within a stream while allowing independent progress across streams (no head-of-line blocking).

### QuicPacket / QuicPacketCodec
QUIC packet types (Initial, Handshake, 0-RTT, 1-RTT, Retry, Version Negotiation). Codec handles packet protection (header + payload encryption), connection ID parsing, and packet number decoding.

### QuicLossRecovery / QuicCongestionControl
Loss detection via ACK-based timers and probe timeout (PTO). Congestion control using a Reno-like algorithm with slow start, congestion avoidance, and recovery phases.

### QuicConnectionMigration / QuicPathValidation
Detects network path changes via connection ID. PATH_CHALLENGE / PATH_RESPONSE frames validate new paths before migrating.

### QpackEncoder / QpackDecoder
QPACK header compression (RFC 9204). Uses a 99-entry static table (larger than HPACK's 61), per-connection dynamic table, and dedicated encoder/decoder streams to avoid head-of-line blocking during header compression. Encoder supports dynamic table insertion via `setUseDynamicTable(true)`, generates encoder instructions (Insert With Name Reference, Insert With Literal Name, Set Capacity, Duplicate), and writes proper Required Insert Count / Delta Base prefix. Decoder processes encoder instructions and generates decoder instructions (Section Acknowledgment, Stream Cancellation, Insert Count Increment).

### Http3Frame / Http3FrameCodec
HTTP/3 frame representation and codec for frame types: DATA, HEADERS, CANCEL_PUSH, SETTINGS, PUSH_PROMISE, GOAWAY, MAX_PUSH_ID.

### Http3Stream / Http3StreamManager
HTTP/3 stream wrapping a QUIC stream. `Http3StreamManager` manages request streams, push streams, and control streams (including QPACK encoder/decoder streams).

### Http3Connection
Central coordinator for a single HTTP/3 connection: manages QUIC connection, HTTP/3 control streams, settings negotiation, GOAWAY, and request dispatch.

### Http3Server / Http3Client
Standalone HTTP/3 server and client. `Http3Server` listens on a UDP port and accepts QUIC connections. `Http3Client` supports multiplexed requests with 0-RTT and session resumption. `Http3Client.send()` QPACK-encodes request headers into HEADERS frames, sends DATA frames for body, decodes response HEADERS+DATA frames via QPACK, and returns `Http3Response`. Exposes `negotiatedAlpn()`, `negotiatedCipherSuite()`, and `handshakePhase()` from the underlying QUIC connection.

### Http3RequestAdapter
Converts HTTP/3 headers + data into an `HttpRequest` and routes through `HttpRouter`, then serializes the `HttpResponse` back as HEADERS + DATA frames on a QUIC stream.

### Http3Feature / AltSvcHandler
`Http3Feature` implements `HttpFeature` with category `HttpFeatureCategory.HTTP3`. `AltSvcHandler` injects `Alt-Svc` headers into HTTP/1.1 and HTTP/2 responses to advertise HTTP/3 availability.

## Package Structure

```
ssg.legoflow.http3/
  quic/              — QuicConnection, QuicStream, QuicPacket, QuicPacketCodec, QuicTransport
  quic/crypto/       — QuicTls, QuicPacketProtection, QuicRetryToken
  quic/recovery/     — QuicLossRecovery, QuicCongestionControl, QuicRttEstimator
  quic/migration/    — QuicConnectionMigration, QuicPathValidation
  qpack/             — QpackEncoder, QpackDecoder, QpackStaticTable, QpackDynamicTable
  frame/             — Http3FrameType, Http3Frame, Http3FrameCodec
  stream/            — Http3Stream, Http3StreamManager, Http3StreamType
  connection/        — Http3Settings, Http3Connection
  server/            — Http3Server, Http3ServerHandler, Http3RequestAdapter
  client/            — Http3Client, QuicSessionCache
  feature/           — Http3Feature, AltSvcHandler
  config/            — Http3Config, Http3Profiles
  demo/              — SimpleHttp3Server, MultiplexingDemo, ZeroRttDemo, MigrationDemo, ServerPushDemo
```

## Key Design Decisions

- **QUIC transport is self-contained** — the `quic/` package handles all transport-level concerns (packets, streams, crypto, loss recovery, congestion control) independently of HTTP/3 framing
- **QPACK encoder/decoder streams** — dedicated unidirectional QUIC streams carry dynamic table updates, avoiding head-of-line blocking that HPACK suffers in HTTP/2
- **Virtual threads per stream** — each HTTP/3 request stream runs on a dedicated virtual thread; the QUIC packet receive loop remains non-blocking
- **Separate module from http** — QUIC uses UDP (fundamentally different from TCP byte streams), requiring its own connection lifecycle, loss recovery, and congestion control; this justifies a separate module rather than extending http inline
- **HttpRouter bridge** — same pattern as http2: `Http3RequestAdapter` reconstructs `HttpRequest` from pseudo-headers, routes through the unchanged `HttpRouter`, and serializes the response
- **0-RTT with session cache** — `QuicSessionCache` stores TLS session tickets; on resumption, early data is sent with the first packet flight

## Dependencies

- **blocks** — DP/DF data processing framework
- **service** — service lifecycle, scoped contexts, UdpDataChannel for QUIC's UDP transport
- **http** — `HttpFeature`, `HttpFeatureCategory`, `HttpRouter`, `HttpRequest`, `HttpResponse`

## Testing

- **Framework**: JUnit 5 + AssertJ
- **313 tests passing** (quic=121, qpack=45, frame=22, stream=35, connection=30, server=24, client=18, demo=18)
- QUIC tests: packet encode/decode, stream state machine, loss recovery, congestion control, connection migration, 0-RTT handshake
- QPACK tests: static table lookups, dynamic table operations, encoder/decoder stream protocol, header block encode/decode
- Frame tests: encode/decode round-trips for all HTTP/3 frame types
- Stream tests: stream type management, request/push/control stream lifecycle
- Connection tests: settings negotiation, GOAWAY, Alt-Svc
- Server tests: end-to-end request/response, server push, 0-RTT
- Client tests: multiplexed requests, session resumption, connection migration
- Demo tests: functional tests exercising each demo scenario end-to-end
