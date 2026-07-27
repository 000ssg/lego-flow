# RTSP Module — Development Guide

## Module Purpose

The `rtsp` module implements RTSP 2.0 (RFC 7826) from scratch. It provides protocol codec, server, client, and interleaved binary transport (RTP-over-TCP) for real-time media streaming control.

## Key Interfaces

- `RtspCodec` — dual-mode codec: static methods for one-shot encode/decode, instance methods for stream-oriented ByteBuffer accumulation
- `RtspServer` / `RtspHandler` — server with handler callback pattern
- `RtspClient` / `RtspClientSession` — client with DESCRIBE/SETUP/PLAY/PAUSE/TEARDOWN
- `InterleavedFrameCodec` — binary frame codec for RTP-over-TCP ($ prefix)
- `StreamController` / `StreamState` — media stream lifecycle management

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | Codec, request/response records, headers, methods, status codes, transport/range headers |
| `server` | RTSP server, handler, session, stream controller, media source |
| `client` | RTSP client, session tracking, setup result |
| `interleaved` | RTP-over-TCP binary frame interleaving |
| `demo` | Streaming server and client playback demos |

## Stream-Oriented RtspCodec

`RtspCodec` supports two usage modes:
- **Static methods** (`encodeRequest`, `decodeRequest`, etc.) — stateless, thread-safe, for complete messages
- **Instance methods** (`feedRequestData`, `feedResponseData`, `hasBufferedData`) — stateful stream-oriented decoding with internal `ByteBuffer` accumulator

The instance API handles TCP stream reassembly: partial messages are buffered internally, and complete messages are returned when enough data has arrived. Remainder bytes (from pipelined messages) are saved for subsequent calls. An instance is **not** thread-safe and should be owned by a single pipeline/connection.

This follows the same accumulator pattern as `Http2FrameCodec`, `SipCodec`, and `LdapCodec`. The transport layer (`ProcessingThread`) passes raw read chunks; the codec handles message boundary detection and reassembly.

## Testing Practices

- Protocol codec tests: encode/decode round-trips for requests and responses
- Header tests: case-insensitive lookup, multi-value, transport/range parsing
- Server tests: session management, stream controller state transitions
- Client tests: session tracking, setup results
- Interleaved tests: frame encode/decode, transport operations
- Demo functional tests: server demo runs against embedded server
- Test count: 241

## Dependencies
- media-common (shared SDP parser)
- slf4j-api (logging)

## Commit Rules
- Update doc/REQUIREMENTS.md with commit section
- Update doc/ARCHITECTURE.md if architecture changed
- Update README.md for API changes

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
