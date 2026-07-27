# HTTP Module — Requirements Evolution

## Module Timeline Overview

- **Start Date**: June 16, 2026
- **Total Tests**: 542
- **Purpose**: Full HTTP/1.1 (RFC 2616) implementation with pluggable feature system

---

## Table of Contents

- [Module Timeline Overview](#module-timeline-overview)
- **Commits by date (newest → oldest)**
  - [Stream-Oriented ByteBuffer Codec Fixes (2026-07-06)](#commit-tbd--stream-oriented-bytebuffer-codec-fixes-2026-07-06)
  - [Demo Expansion — Remaining Demo Sources and Tests (2026-06-16)](#commit-demo-expansion--remaining-demo-sources-and-tests-2026-06-16)
  - [Initial Commit — HTTP Module Implementation](#initial-commit--http-module-implementation)

---

## Initial Commit — HTTP Module Implementation

### Original Request

> "Implement a full HTTP/1.1 module with a pluggable feature system for the Lego Flow framework. The module should comply with RFC 2616, support SSL/TLS as a DataFilter, provide WebSocket upgrade capability, serve static content, handle content negotiation, caching, and byte ranges. Features should be organized into categories with standard profiles for common server and client configurations."

### Reformulated Requirements

1. Full RFC 2616 HTTP/1.1 compliance — request/response parsing, all standard methods and status codes
2. Pluggable feature system with categorized features (CORE, TRANSFER, CONTENT, CACHING, CONNECTION, ENTITY, METADATA, SECURITY, WEBSOCKET, STATIC)
3. SSL/TLS implemented as `DataFilter<byte[]>` from the blocks framework, composable in the filter pipeline
4. Standard profiles for server (MINIMAL, STANDARD, FULL) and client (MINIMAL, STANDARD, FULL)
5. WebSocket upgrade handshake support (RFC 6455)
6. Static content serving with directory listings
7. Content negotiation with q-factor weighting for Accept, Accept-Charset, Accept-Encoding, Accept-Language
8. Caching support: ETag generation, If-Modified-Since, If-None-Match, 304 Not Modified responses
9. Byte range request handling (Range, Content-Range headers)
10. HSTS support (RFC 6797) — Strict-Transport-Security header
11. Dual API: sync + async, procedural + functional (following service module convention)
12. Build on blocks (DP/DF) and service (lifecycle, scoped contexts) modules

### Final Design Decisions

- **Feature system with categories**: each feature belongs to exactly one `HttpFeatureCategory`, enabling selective composition without tight coupling between unrelated concerns
- **Standard profiles**: predefined `HttpFeatureSet` constants (SERVER_MINIMAL/STANDARD/FULL, CLIENT_MINIMAL/STANDARD/FULL) covering common use cases while allowing customization
- **SSL as DataFilter**: SSL/TLS wraps the byte stream at the remote boundary, consistent with the blocks framework's filter chain pattern — no special-case SSL handling in the HTTP layer
- **HSTS support**: Strict-Transport-Security header processing as part of the SECURITY feature category
- **SecurityExtension / AppSecurityExtension**: extension points for custom security logic beyond SSL/TLS

### Implementation Details

- **64+ source files** across 14 packages (core, header, transfer, content, caching, connection, websocket, security, staticcontent, feature, config, server, client, demo)
- All 10 feature categories fully implemented
- Server and client implementations with builder pattern and feature set configuration
- HTTP request routing by path, method, and content type
- Content negotiation with q-factor parsing and best-match selection
- ETag-based and time-based conditional request handling
- Byte range request support with multipart/byteranges for multiple ranges

### Test Coverage

- **226 tests** — all passing
- Unit tests for core message types, headers, and feature components
- Integration tests for server/client communication
- Demo functional tests covering minimal through full feature sets

---

## Commit: Demo Expansion — Remaining Demo Sources and Tests (2026-06-16)

### Original Request
> "Single commit adding remaining demo sources and tests across all 4 modules. HTTP: 11 new demo source files + 18 new test files, test count up from 231 to 378. Server demos: SecureServer, WebSocketServer, CachingServer, FullFeaturedServer. Client demos: AdaptiveClient, RangeClient, SecureClient, WebSocketClient. Multi demos: MultiServerDemo, ClientServerPairDemo, LoadBalancedDemo. New unit tests: FeatureConfigurerTest, UpgradeHandlerTest, SslFilterTest, SslHandshakeHandlerTest, StaticContentHandlerTest, DirectoryContentResolverTest, HttpClientTest, HttpServerTest, FixedLengthCodecTest, WebSocketSessionTest, ProductTokenTest. New demo tests: StaticContentDemoTest, ChunkedTransferDemoTest, ByteRangeDemoTest, KeepAliveDemoTest, SecureServerDemoTest, MultiServerDemoTest, ClientAdaptationDemoTest."

### Reformulated Requirements

1. **Server demo expansion** — four new server demo implementations covering security (SecureServer with SSL + HSTS), WebSocket upgrade (WebSocketServer), response caching (CachingServer), and all-feature-categories combined (FullFeaturedServer)
2. **Client demo expansion** — four new client demo implementations covering adaptive feature negotiation (AdaptiveClient), byte range requests (RangeClient), SSL/TLS client (SecureClient), and WebSocket client (WebSocketClient)
3. **Multi-component demos** — three demos showing server-client interaction: dual-server configuration (MultiServerDemo), matched client/server pair exercising full round-trip (ClientServerPairDemo), and load-balanced multi-server setup (LoadBalancedDemo)
4. **Feature unit tests** — eleven new unit test classes covering `FeatureConfigurer`, `UpgradeHandler`, `SslDataFilter` (SslFilterTest), SSL handshake handler, static content handler, directory content resolver, `HttpClient`, `HttpServer`, fixed-length codec, `WebSocketSession`, and `ProductToken`
5. **Demo functional tests** — seven new demo test classes: `StaticContentDemoTest`, `ChunkedTransferDemoTest`, `ByteRangeDemoTest`, `KeepAliveDemoTest`, `SecureServerDemoTest`, `MultiServerDemoTest`, `ClientAdaptationDemoTest`

### Final Design Decisions

- **Server demos** grouped in `demo/server/`, client demos in `demo/client/`, multi-component demos in `demo/multi/` — mirrors the established demo progression (simple → complex)
- **AdaptiveClient** demonstrates runtime profile switching (MINIMAL → STANDARD → FULL) based on server capability signals — highlights the feature set composability
- **LoadBalancedDemo** uses multiple `HttpServer` instances with a routing layer at the demo level; no new infrastructure class required
- **Unit tests for SSL components** use JDK `SSLEngine` mocks/stubs rather than real certificates, keeping tests fast and self-contained
- `HttpClientTest` and `HttpServerTest` are integration-style unit tests exercising builder construction, feature set validation, and basic request/response round-trips without network I/O

### Implementation Details

- **11 new demo source files** across `demo/server/`, `demo/client/`, `demo/multi/` packages
- **18 new test files**: 11 unit tests + 7 demo functional tests
- No new production packages — all additions fit existing package layout

### Test Coverage

- **New unit tests**: `FeatureConfigurerTest`, `UpgradeHandlerTest`, `SslFilterTest`, `SslHandshakeHandlerTest`, `StaticContentHandlerTest`, `DirectoryContentResolverTest`, `HttpClientTest`, `HttpServerTest`, `FixedLengthCodecTest`, `WebSocketSessionTest`, `ProductTokenTest`
- **New demo tests**: `StaticContentDemoTest`, `ChunkedTransferDemoTest`, `ByteRangeDemoTest`, `KeepAliveDemoTest`, `SecureServerDemoTest`, `MultiServerDemoTest`, `ClientAdaptationDemoTest`
- **Total: 378 HTTP tests (764 total project)**

---

## Commit: `TBD` — Stream-Oriented ByteBuffer Codec Fixes (2026-07-06)

### Original Request

> "Audit and fix ByteBuffer-based codecs in the HTTP module to correctly handle data split across multiple TCP reads. ByteBuffer flow should be treated as a stream: codecs must accumulate partial data internally, parse complete units when available, and save any remainder for the next call. Follow the pattern already established by Http2FrameCodec (accumulate, parse complete units, save remainder)."

### Reformulated Requirements

1. **ChunkedCodec** must accumulate partial chunk data across calls — emit individual chunks only when complete, buffer the remainder for the next `decodeChunks()` invocation
2. **WebSocketFrameCodec** must accumulate partial frame data across calls — return `null` on insufficient data instead of throwing `ArrayIndexOutOfBoundsException`, provide a new `decodeFrames(ByteBuffer...)` entry point for stream-oriented multi-frame decoding
3. **HttpProtocolCodec** must accumulate partial request/response data across calls — new `parseRequestStreaming(ByteBuffer)` and `parseResponseStreaming(ByteBuffer)` methods that return `null` until a complete message is accumulated; `findHeaderEnd()` returns -1 when delimiter not found
4. All three codecs must follow the same internal pattern: `ByteBuffer accumulator` field, `combineWithAccumulator()` helper, `hasBufferedData()` query method
5. Backward compatibility preserved — existing `parseRequest()`, `parseResponse()`, `decodeFrame()`, and `decodeChunks()` signatures remain unchanged

### Final Design Decisions

- **Accumulator pattern**: each codec holds a `ByteBuffer accumulator` field that retains unconsumed bytes between calls. `combineWithAccumulator()` merges the accumulator with new input; after parsing, any leftover is compacted back into the accumulator. This matches the established `Http2FrameCodec` design in the http2 module.
- **Stream semantics for ByteBuffer flow**: TCP delivers an arbitrary byte stream, not message-aligned buffers. Codecs are the layer responsible for reassembling protocol units from that stream. Callers no longer need to pre-assemble complete chunks, frames, or HTTP messages before invoking the codec.
- **Null-return convention**: streaming parse methods return `null` when insufficient data is available, rather than throwing exceptions. This lets callers simply loop: read from socket, call codec, act on non-null results.
- **No new public types**: all changes are internal to existing codec classes. The new streaming methods are additions, not replacements.

### Implementation Details

- **ChunkedCodec.java**: added `ByteBuffer accumulator`, `combineWithAccumulator()`, `hasBufferedData()`. Rewrote `decodeChunks()` to emit individual chunks as they become complete, saving partial chunk data for the next call.
- **WebSocketFrameCodec.java**: added `ByteBuffer accumulator`, `combineWithAccumulator()`, `hasBufferedData()`, new `decodeFrames(ByteBuffer...)` for stream-oriented multi-frame decoding. Fixed `decodeFrame()` to return `null` on insufficient data instead of throwing `ArrayIndexOutOfBoundsException`.
- **HttpProtocolCodec.java**: added `ByteBuffer accumulator`, `combineWithAccumulator()`, `hasBufferedData()`. Added `parseRequestStreaming(ByteBuffer)` and `parseResponseStreaming(ByteBuffer)` that return `null` until a complete message is accumulated. Changed `findHeaderEnd()` to return -1 when header delimiter not found. Backward compatibility preserved for existing `parseRequest()` / `parseResponse()`.

### Test Coverage

- **0 new tests added** — existing 542 tests verify backward compatibility of all modified codecs
- **Total: 542 HTTP tests**

### Cost Estimate

| Metric | Value |
|--------|-------|
| Background agents | 1 (HTTP codec fix) |
| Agent tokens | ~45K |
| Agent tool calls | ~20 |
| Agent wall time | ~3 min |
| Files created/modified | 3 |
| Lines added/removed | +458 / -20 |
| Tests added | 0 (total: 542) |
