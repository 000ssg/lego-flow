# HTTP/2 Compliance Report

## Specifications Covered
- RFC 7540 — Hypertext Transfer Protocol Version 2 (HTTP/2) (original)
- RFC 9113 — HTTP/2 (revised)
- RFC 7541 — HPACK: Header Compression for HTTP/2

## Compliance Matrix

### RFC 9113 / RFC 7540 — HTTP/2

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §3.4 | Connection preface (client magic + SETTINGS) | ✅ Implemented | `Http2ConnectionPreface`; `Http2ConnectionTest` |
| §4 | Frame format (9-byte header: length, type, flags, stream ID) | ✅ Implemented | `Http2Frame` with HEADER_SIZE=9; `Http2FrameTest`, `Http2FrameCodecTest` |
| §4.1 | Frame size limits (default 16384, max 16777215) | ✅ Implemented | `Http2Settings.MAX_FRAME_SIZE` with validation; `Http2SettingsTest` |
| §5.1 | Stream states (idle, reserved, open, half-closed, closed) | ✅ Implemented | `Http2StreamState` enum with full state machine; `Http2StreamTest` |
| §5.1.1 | Stream identifiers (odd=client, even=server) | ✅ Implemented | `Http2StreamManager`; `Http2StreamManagerTest` |
| §5.1.2 | Stream concurrency (SETTINGS_MAX_CONCURRENT_STREAMS) | ✅ Implemented | `Http2StreamManager` enforces limit; `Http2StreamManagerTest` |
| §5.2 | Flow control (connection and stream level) | ✅ Implemented | `Http2FlowControl` with dual windows; `Http2FlowControlTest` |
| §5.2.1 | Flow control principles (receiver-controlled) | ✅ Implemented | Window tracking and WINDOW_UPDATE emission; `Http2FlowControlTest` |
| §5.3 | Stream priority (PRIORITY frame) | ✅ Implemented | `Http2StreamManager.setPriority()`, `getScheduleOrder()`, `allocateBandwidth()` with dependency tree and weight-proportional scheduling; `Http2StreamManagerTest` |
| §6.1 | DATA frame | ✅ Implemented | `Http2FrameType.DATA`; encode/decode in `Http2Frame`; `Http2FrameTest` |
| §6.2 | HEADERS frame | ✅ Implemented | `Http2FrameType.HEADERS`; `Http2FrameTest` |
| §6.3 | PRIORITY frame | ✅ Implemented | Frame codec + dependency tree + exclusive reparenting + weight-proportional scheduling; `Http2StreamManagerTest` |
| §6.4 | RST_STREAM frame | ✅ Implemented | `Http2FrameType.RST_STREAM`; `Http2FrameTest` |
| §6.5 | SETTINGS frame | ✅ Implemented | `Http2Settings` with all 6 parameters, encode/decode, validation; `Http2SettingsTest` |
| §6.5.1 | SETTINGS acknowledgement | ✅ Implemented | Settings negotiation in `Http2Connection`; `Http2ConnectionTest` |
| §6.5.2 | Defined SETTINGS parameters (6 params) | ✅ Implemented | HEADER_TABLE_SIZE, ENABLE_PUSH, MAX_CONCURRENT_STREAMS, INITIAL_WINDOW_SIZE, MAX_FRAME_SIZE, MAX_HEADER_LIST_SIZE; `Http2SettingsTest` |
| §6.6 | PUSH_PROMISE frame | ✅ Implemented | `Http2FrameType.PUSH_PROMISE`; `ServerPushDemoTest` |
| §6.7 | PING frame | ✅ Implemented | `Http2FrameType.PING`; `Http2FrameTest` |
| §6.8 | GOAWAY frame | ✅ Implemented | `Http2FrameType.GOAWAY`; `Http2ConnectionTest` |
| §6.9 | WINDOW_UPDATE frame | ✅ Implemented | `Http2FrameType.WINDOW_UPDATE`; `Http2FlowControlTest` |
| §6.10 | CONTINUATION frame | ✅ Implemented | `Http2FrameType.CONTINUATION`; `Http2FrameTest` |
| §7 | Error codes | ✅ Implemented | `Http2ErrorCode` enum; `Http2FrameTest` |
| §8.1 | HTTP request/response exchange over streams | ✅ Implemented | `Http2RequestAdapter` converts streams to HttpRequest/HttpResponse; `Http2RequestAdapterTest`, `Http2ServerTest` |
| §8.1.2 | Pseudo-header fields (:method, :scheme, :authority, :path, :status) | ✅ Implemented | `Http2RequestAdapter` handles pseudo-headers; `Http2RequestAdapterTest` |
| §8.2 | Server push | ✅ Implemented | PUSH_PROMISE + response on reserved stream; `ServerPushDemoTest` |
| §8.2.1 | Push requests | ✅ Implemented | `ServerPushDemoTest` |
| §3.2 | H2c upgrade (HTTP/1.1 Upgrade to h2c) | ✅ Implemented | `Http2UpgradeHandler`; `H2cUpgradeDemoTest` |

### RFC 7541 — HPACK Header Compression

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| §2.3 | Static table (61 entries) | ✅ Implemented | `HpackStaticTable`; `HpackStaticTableTest` |
| §2.3.2 | Dynamic table | ✅ Implemented | `HpackDynamicTable` with size limits and eviction; `HpackDecoderTest`, `HpackEncoderTest` |
| §2.3.3 | Dynamic table size update | ✅ Implemented | Size updates via SETTINGS; `HpackDecoderTest` |
| §4.1 | Indexed header field representation | ✅ Implemented | `HpackEncoder/HpackDecoder`; `HpackEncoderTest`, `HpackDecoderTest` |
| §4.2 | Literal header field with incremental indexing | ✅ Implemented | `HpackEncoder/HpackDecoder`; `HpackEncoderTest`, `HpackDecoderTest` |
| §4.3 | Literal header field without indexing | ✅ Implemented | `HpackEncoder/HpackDecoder`; `HpackEncoderTest` |
| §4.4 | Literal header field never indexed | ✅ Implemented | `HpackEncoder.isSensitive()`, `setSensitiveHeaders()`, `encodeNeverIndexed()` with 0x10 prefix; `HpackEncoderTest` |
| §5.1 | Integer representation (prefix-coded) | ✅ Implemented | `HpackEncoder/HpackDecoder` handle variable-length integers; `HpackEncoderTest`, `HpackDecoderTest` |
| §5.2 | Huffman encoding | ✅ Implemented | `HpackHuffman` with encode/decode; `HpackHuffmanTest` |

## Known Limitations
- Stream priority (RFC 9113 §5.3) supports dependency tree, exclusive dependencies, and weight-proportional scheduling; deprecated in RFC 9113 but implemented for backward compatibility
- No HTTP/2 ALPN negotiation (h2 over TLS) — only h2c (cleartext) upgrade is implemented
- No server-side flow control auto-tuning — uses fixed window sizes
- The CONTINUATION frame is defined but header blocks are assumed to fit in a single HEADERS frame in practice
- No HTTP/2 padding support for DATA or HEADERS frames
- Sensitive headers (Authorization, Cookie, etc.) are automatically encoded as "never indexed" per RFC 7541 Section 7.1.3; custom sensitive headers can be configured via `HpackEncoder.setSensitiveHeaders()`

## Test Coverage Summary
- Total compliance tests: 180 (per CLAUDE.md)
- Test breakdown: frame=27, hpack=42, stream=48, connection=26, server=16, demo=21
- Key unit test classes: `Http2FrameTest`, `Http2FrameCodecTest`, `HpackDecoderTest`, `HpackEncoderTest`, `HpackHuffmanTest`, `HpackStaticTableTest`, `Http2StreamTest`, `Http2StreamManagerTest`, `Http2FlowControlTest`, `Http2SettingsTest`, `Http2ConnectionTest`, `Http2RequestAdapterTest`, `Http2ServerTest`
- Key demo test classes: `SimpleHttp2DemoTest`, `MultiplexingDemoTest`, `ServerPushDemoTest`, `H2cUpgradeDemoTest`, `FlowControlDemoTest`
- Sections fully covered: Frame format/codec (§4-6), SETTINGS (§6.5), Flow control (§5.2), Stream states (§5.1), HPACK static/dynamic table and Huffman (RFC 7541)
- Key areas needing improvement: ALPN negotiation, padding, CONTINUATION frame splitting
