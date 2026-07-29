# web / http2 — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Overview

The **http2** module is a full HTTP/2 implementation compliant with RFC 7540 and RFC 9113. It covers binary framing, HPACK header compression, stream multiplexing, flow control, server push, and H2c upgrade. The module plugs into the existing `http` module's feature system via `Http2Feature` and bridges to `HttpRouter` so existing request handlers require no changes.

## Key Interfaces

### Http2Frame / Http2FrameCodec
Binary frame representation and codec for all 10 frame types: DATA, HEADERS, PRIORITY, RST_STREAM, SETTINGS, PUSH_PROMISE, PING, GOAWAY, WINDOW_UPDATE, CONTINUATION.

### HpackEncoder / HpackDecoder
HPACK header compression (RFC 7541). Encodes and decodes header blocks using a static table (61 entries), dynamic table (with size limits), and Huffman coding.

### Http2Stream / Http2StreamManager
Individual HTTP/2 stream with state machine (IDLE → OPEN → HALF_CLOSED → CLOSED). `Http2StreamManager` owns all streams on a connection and enforces SETTINGS_MAX_CONCURRENT_STREAMS.

### Http2FlowControl
Connection-level and stream-level flow control. Tracks send/receive windows and emits WINDOW_UPDATE frames when receive buffers are consumed.

### Http2Connection
Central coordinator for a single HTTP/2 connection: reads/writes frames, manages streams, handles settings negotiation, and delegates request processing to `Http2ServerHandler`.

### Http2Settings / Http2Config / Http2Profiles
`Http2Settings` maps the 6 SETTINGS parameters. `Http2Config` is the module-level configuration bean. `Http2Profiles` provides predefined profiles (SERVER_DEFAULT, SERVER_PUSH_ENABLED, CLIENT_DEFAULT).

### Http2Feature
Implements `HttpFeature` from the http module. Category: `HttpFeatureCategory.HTTP2`. Registers itself with the existing feature system so HTTP/1.1 → HTTP/2 upgrade is transparent to callers.

### Http2Server / Http2Client
Standalone HTTP/2 server and client. `Http2Server` accepts connections and spawns one virtual thread per connection. `Http2Client` supports multiplexed requests over a single connection.

### Http2RequestAdapter
Converts an HTTP/2 headers+data stream into an `HttpRequest` and routes it through `HttpRouter`, then serializes the `HttpResponse` back into HEADERS + DATA frames.

## Package Structure

```
ssg.legoflow.http2/
  frame/         — Http2FrameType, Http2Frame, Http2FrameCodec, Http2Flags, Http2ErrorCode
  hpack/         — HpackEncoder, HpackDecoder, HpackStaticTable, HpackDynamicTable, HpackHuffman
  stream/        — Http2Stream, Http2StreamState, Http2StreamManager, Http2FlowControl
  connection/    — Http2Settings, Http2Connection, Http2ConnectionPreface
  server/        — Http2Server, Http2ServerHandler, Http2RequestAdapter
  client/        — Http2Client
  feature/       — Http2Feature, Http2UpgradeHandler
  config/        — Http2Config, Http2Profiles
  demo/          — SimpleHttp2Server, MultiplexingDemo, ServerPushDemo, H2cUpgradeDemo, FlowControlDemo
```

## Key Design Decisions

- **Binary framing layer is self-contained** — `Http2FrameCodec` encodes/decodes raw bytes independently of any transport, making it testable in isolation
- **HPACK static + dynamic table** — static table is a singleton constant (61 entries per RFC 7541 Appendix A); dynamic table is per-connection and updated on each HEADERS frame
- **Virtual threads per stream** — each stream's data processing runs on a dedicated virtual thread, keeping the frame-reading loop non-blocking
- **Flow control at two levels** — connection window and per-stream window are tracked separately; WINDOW_UPDATE is emitted automatically as data is consumed
- **HttpRouter bridge** — `Http2RequestAdapter` reconstructs a standard `HttpRequest` from pseudo-headers, calls the existing router, and serializes the response; no handler changes required
- **H2c upgrade** — `Http2UpgradeHandler` intercepts HTTP/1.1 requests with `Upgrade: h2c`, completes the upgrade handshake, then hands the connection to `Http2Connection`

## Dependencies

- **blocks** — DP/DF data processing framework
- **service** — service lifecycle and scoped contexts
- **http** — `HttpFeature`, `HttpFeatureCategory`, `HttpRouter`, `HttpRequest`, `HttpResponse`

## Testing

- **Framework**: JUnit 5 + AssertJ
- **180 tests passing** (frame=27, hpack=42, stream=48, connection=26, server=16, demo=21)
- Frame tests: encode/decode round-trips for all 10 frame types
- HPACK tests: static table lookups, dynamic table eviction, Huffman encode/decode
- Stream tests: state machine transitions, flow control arithmetic, concurrent stream limits
- Connection tests: settings negotiation, preface validation, GOAWAY handling
- Server tests: end-to-end request/response, server push, H2c upgrade
- Demo tests: functional tests exercising each demo scenario end-to-end
