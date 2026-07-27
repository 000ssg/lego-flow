# HTTP Module — Development Guide

## Module Overview

The **http** module is a full RFC 2616 HTTP/1.1 implementation with a pluggable feature system. It supports SSL/TLS, WebSocket upgrade, static content serving, content negotiation, caching, and byte ranges. Features are organized into categories and assembled into standard profiles for common server and client configurations.

## Key Interfaces

### HttpRequest / HttpResponse
Core message types representing HTTP request and response. Carry headers, body, method, status, and URI information.

### HttpService
Service abstraction for handling HTTP requests — extends the service module's `Service<I,O>` pattern.

### HttpFeature
Pluggable unit of HTTP functionality. Each feature belongs to an `HttpFeatureCategory` and can be independently enabled/disabled.

### HttpFeatureSet
Immutable collection of features assembled for a specific use case. Created via `HttpFeatureRegistry` or standard profiles.

### HttpRouter
Request routing to handlers based on path, method, and content type.

### HttpServer / HttpClient
Server and client implementations built on the service framework with configurable feature sets.

## Feature Categories

| Category       | Purpose                                      |
|----------------|----------------------------------------------|
| CORE           | Request/response parsing, status codes        |
| TRANSFER       | Chunked transfer encoding, content-length     |
| CONTENT        | Content negotiation, media types, charset     |
| CACHING        | ETag, If-Modified-Since, 304 Not Modified     |
| CONNECTION     | Keep-alive, connection management             |
| ENTITY         | Entity body handling, byte ranges             |
| METADATA       | Date, Server, User-Agent headers              |
| SECURITY       | SSL/TLS, HSTS (RFC 6797)                     |
| WEBSOCKET      | WebSocket upgrade (RFC 6455)                  |
| STATIC         | Static file serving, directory listings       |

## Standard Profiles

| Profile            | Description                                    |
|--------------------|------------------------------------------------|
| SERVER_MINIMAL     | Core + Transfer — bare-bones HTTP server       |
| SERVER_STANDARD    | + Content + Caching + Connection + Entity      |
| SERVER_FULL        | + Metadata + Security + WebSocket + Static     |
| CLIENT_MINIMAL     | Core + Transfer — bare-bones HTTP client       |
| CLIENT_STANDARD    | + Content + Connection + Entity                |
| CLIENT_FULL        | + Caching + Metadata + Security + WebSocket    |

## Key Design Decisions

- **SSL as DataFilter<byte[]>** — SSL/TLS is implemented as a `DataFilter<byte[]>` from the blocks framework, composable with other filters in the pipeline
- **HSTS (RFC 6797)** — Strict-Transport-Security header support for enforcing HTTPS connections
- **Feature pluggability** — features can be independently added, removed, or replaced without modifying core logic

## Stream-Oriented Codec Design (Accumulator Pattern)

ByteBuffer-based codecs (`ChunkedCodec`, `WebSocketFrameCodec`, `HttpProtocolCodec`) treat TCP input as a stream, not as message-aligned buffers. Each codec maintains an internal `ByteBuffer accumulator` that retains unconsumed bytes between calls. The pattern:

1. `combineWithAccumulator(newInput)` merges leftover bytes with new input
2. Parse as many complete protocol units as possible
3. Save remainder back into the accumulator
4. Return parsed units, or `null` if data is incomplete

This matches the `Http2FrameCodec` pattern from the http2 module. Streaming entry points: `decodeChunks()` (ChunkedCodec), `decodeFrames(ByteBuffer...)` (WebSocketFrameCodec), `parseRequestStreaming(ByteBuffer)` / `parseResponseStreaming(ByteBuffer)` (HttpProtocolCodec). Original non-streaming methods are preserved for backward compatibility.

## Dependencies

- **blocks** — core DP/DF data processing framework
- **service** — service lifecycle, scoped contexts, dual API support

## Dual API Convention

- **Sync procedural**: `httpService.handle(ctx, request)` returns `HttpResponse`
- **Async procedural**: `asyncHttpService.handle(ctx, request)` returns `CompletableFuture<HttpResponse>`
- **Functional**: lambda-friendly builders and pipeline composition

## Package Structure

```
ssg.legoflow.http/
  core/           — request, response, status codes, method
  header/         — HTTP header parsing and representation
  transfer/       — chunked encoding, content-length
  content/        — content negotiation, media types, charset
  caching/        — ETag, conditional requests, 304
  connection/     — keep-alive, connection management
  websocket/      — WebSocket upgrade support
  security/       — SSL/TLS, HSTS, SecurityExtension
  staticcontent/  — static file serving, directory listings
  feature/        — HttpFeature, HttpFeatureCategory, HttpFeatureSet, HttpFeatureRegistry
  config/         — server/client configuration
  server/         — HTTP server implementation
  client/         — HTTP client implementation
  demo/           — demo servers and clients
```

## Testing

- **Framework**: JUnit 5 + AssertJ
- **No Mockito**: use `DefaultContext` from blocks for test contexts
- **542 tests passing**
- Test demos progress from simplest to complex, covering common and specific usage variants

## Commit Rules

- Update doc/REQUIREMENTS.md with commit section
- Update doc/ARCHITECTURE.md if architecture changed
- Update README.md for API changes
- Include `Co-Authored-By: AI assistant` in commit messages
