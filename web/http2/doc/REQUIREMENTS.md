# HTTP/2 Module — Requirements Evolution

## Module Timeline Overview

- **Start Date**: June 24, 2026
- **Total Tests**: 168
- **Purpose**: Full HTTP/2 (RFC 7540 / RFC 9113) implementation with HPACK, stream multiplexing, flow control, server push, and H2c upgrade

---

## Table of Contents

- [Module Timeline Overview](#module-timeline-overview)
- **Commits by date (newest → oldest)**
  - [Initial Commit — HTTP/2 Module Implementation](#initial-commit--http2-module-implementation)

---

## Initial Commit — HTTP/2 Module Implementation

### Original Request

> "Implement a full HTTP/2 module for the Lego Flow framework. The module should comply with RFC 7540 and RFC 9113, provide binary framing with all 10 frame types, HPACK header compression (static table, dynamic table, Huffman coding), stream multiplexing with connection-level and stream-level flow control, server push via PUSH_PROMISE, and H2c upgrade from HTTP/1.1. The module must bridge to the existing HttpRouter so existing handlers require no changes, and must plug into the http module's feature system via HttpFeatureCategory.HTTP2. Use virtual threads for stream processing."

### Reformulated Requirements

1. Full RFC 7540 / RFC 9113 compliance — binary framing, frame type handling, stream states, connection preface
2. All 10 frame types implemented: DATA, HEADERS, PRIORITY, RST_STREAM, SETTINGS, PUSH_PROMISE, PING, GOAWAY, WINDOW_UPDATE, CONTINUATION
3. HPACK header compression (RFC 7541) — static table (61 entries per Appendix A), dynamic table with configurable max size, Huffman encoding/decoding
4. Stream state machine per RFC 7540 §5: IDLE → OPEN → HALF_CLOSED_LOCAL / HALF_CLOSED_REMOTE → CLOSED
5. Stream multiplexing — concurrent streams over a single TCP connection with SETTINGS_MAX_CONCURRENT_STREAMS enforcement
6. Flow control at both connection and stream levels — WINDOW_UPDATE frames emitted automatically as receive buffers are consumed
7. Server push — PUSH_PROMISE frames, configurable per server profile
8. H2c upgrade — intercept HTTP/1.1 requests with `Upgrade: h2c` header, complete handshake, hand off to Http2Connection
9. Bridge to HttpRouter — Http2RequestAdapter converts HTTP/2 pseudo-headers to HttpRequest, routes through HttpRouter, serializes HttpResponse back to HEADERS + DATA frames
10. Http2Feature plugs into http module's feature system via HttpFeatureCategory.HTTP2
11. Virtual threads — one virtual thread per stream for data processing; frame-reading loop remains non-blocking
12. Standard profiles: SERVER_DEFAULT, SERVER_PUSH_ENABLED, CLIENT_DEFAULT
13. 31 source files across 9 packages; 168 tests

### Final Design Decisions

- **Self-contained framing layer** — `Http2FrameCodec` encodes/decodes raw bytes with no transport dependency, enabling isolated unit testing of all frame types
- **HPACK static table as singleton constant** — the 61-entry static table never changes (RFC 7541 Appendix A); it is shared across all connections; dynamic table is per-connection
- **Virtual threads per stream** — the frame-reading loop dispatches each stream's processing onto a new virtual thread; this avoids head-of-line blocking within the JVM while keeping the frame reader tight
- **Two-level flow control** — `Http2FlowControl` tracks connection window and per-stream window independently; WINDOW_UPDATE is emitted when consumed bytes cross 50% of the initial window to avoid stalling senders
- **HttpRouter bridge** — Http2RequestAdapter constructs a standard `HttpRequest` from `:method`, `:path`, `:authority`, `:scheme` pseudo-headers and regular headers, routes it through the unchanged `HttpRouter`, then serializes the `HttpResponse` as HEADERS (with status) + DATA (body) + END_STREAM
- **Http2Feature + HttpFeatureCategory.HTTP2** — follows the same `HttpFeature` SPI used by all other http module features; adding `HttpFeatureCategory.HTTP2` to a feature set is sufficient to enable HTTP/2 on an existing HTTP/1.1 server

### Implementation Details

- **31 source files** across 9 packages (frame, hpack, stream, connection, server, client, feature, config, demo)
- Binary frame codec with length-prefix (24-bit), type byte, flags byte, and 31-bit stream identifier
- HPACK static table: 61 pre-defined header name/value pairs; dynamic table: eviction-on-overflow FIFO with byte-size tracking
- Huffman codec: 257-symbol code table (256 octets + EOS); encode via lookup table, decode via bit-trie
- Stream manager: tracks all streams by ID, enforces odd/even ID convention (client/server), detects stream ID reuse
- Connection preface: validates the 24-byte client magic (`PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n`) and initial SETTINGS exchange
- Http2Config: wraps Http2Settings + push policy + profile name
- Demo programs covering the five key usage scenarios: simple server, multiplexing, server push, H2c upgrade, flow control

### Test Coverage

- **168 tests** — all passing
- frame=27: encode/decode round-trips for all 10 frame types, flag combinations, error codes
- hpack=37: static table lookups, dynamic table add/eviction, Huffman encode/decode, header block encode/decode
- stream=41: state machine transitions (all valid and invalid paths), flow control arithmetic, concurrent stream limits, stream reset
- connection=26: preface validation, settings negotiation, PING/PONG, GOAWAY, settings acknowledgement
- server=16: end-to-end request/response, server push, H2c upgrade, concurrent requests
- demo=21: functional tests for SimpleHttp2Server, MultiplexingDemo, ServerPushDemo, H2cUpgradeDemo, FlowControlDemo
