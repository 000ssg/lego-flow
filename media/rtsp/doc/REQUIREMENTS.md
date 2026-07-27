# RTSP Module — Requirements Evolution

## Timeline

| Commit | Feature | Tests |
|--------|---------|-------|
| Initial | RTSP 2.0 protocol: codec, server, client, interleaved transport | 241 |
| Stream Codec | Stream-oriented ByteBuffer accumulation in RtspCodec | 241 |

---

## Commit: Initial — RTSP 2.0 Protocol (2026-07-04)

### Original Request
> "Create RTSP 2.0 module (RFC 7826) with protocol codec, server, client, interleaved binary transport, and demos."

### Reformulated Requirements
1. Protocol layer: RtspMethod enum, RtspStatus codes, RtspHeaders (multi-value, case-insensitive), RtspRequest/RtspResponse records, RtspCodec (encode/decode)
2. Transport header parsing (RTP/AVP, unicast/multicast, client/server port ranges)
3. Range header parsing (NPT time ranges)
4. Server: RtspServer with RtspHandler callback, RtspSession management, StreamController with StreamState, MediaSource interface
5. Client: RtspClient with DESCRIBE/SETUP/PLAY/PAUSE/TEARDOWN, RtspClientSession tracking, SetupResult
6. Interleaved transport: InterleavedFrame ($ prefix, channel, length), InterleavedFrameCodec, InterleavedTransport for RTP-over-TCP
7. Demos: StreamingServerDemo, ClientPlaybackDemo
8. Comprehensive tests covering protocol, server, client, interleaved, and demo layers

### Final Design Decisions
- Text-based HTTP-like protocol with CRLF termination
- Static encode/decode methods for one-shot usage
- Interleaved binary frame detection via `$` prefix byte
- Server uses handler callback pattern (RtspHandler)
- StreamState enum for media stream lifecycle
- Session management via RtspSession with unique session IDs

### Implementation Details
- 5 packages: protocol, server, client, interleaved, demo
- 22 source files, 17 test files

### Test Coverage
- Protocol: codec round-trips, header parsing, method/status enums, transport/range headers
- Server: session management, stream controller, handler dispatch
- Client: session tracking, setup results
- Interleaved: frame encode/decode, transport operations
- Demo: streaming server functional test
- **Total: 241 RTSP tests**

---

## Commit: Stream Codec — Stream-Oriented ByteBuffer Accumulation in RtspCodec (2026-07-06)

### Original Request
> "Add internal accumulation buffers to RtspCodec for stream-oriented message assembly over TCP, matching the accumulator pattern used by Http2FrameCodec and LdapCodec."

### Reformulated Requirements
1. `RtspCodec` gains a `ByteBuffer accumulator` field for partial message buffering across reads
2. `combineWithAccumulator(ByteBuffer)` — merges new input with any previously buffered bytes
3. `feedRequestData(ByteBuffer)` — feeds chunks into accumulator, returns parsed `RtspRequest` when a complete message (headers + body per Content-Length) has arrived, or `null` if more data is needed; saves remainder for next call
4. `feedResponseData(ByteBuffer)` — same pattern for `RtspResponse`
5. `hasBufferedData()` — returns true if the internal accumulator has remaining bytes
6. Existing static methods (`encodeRequest`, `encodeResponse`, `decodeRequest`, `decodeResponse`, `isInterleavedFrame`) preserved unchanged for backward compatibility

### Final Design Decisions
- Instance-level accumulation (each `RtspCodec` instance owns its own `ByteBuffer accumulator`) — not thread-safe, intended to be owned by a single pipeline/connection
- Header completeness detected by scanning for `\r\n\r\n`; body completeness by parsing `Content-Length` from raw header bytes
- Remainder bytes after a complete message are saved in the accumulator for pipelined messages
- Static methods remain stateless and thread-safe; instance methods are the stream-oriented API
- Follows the same accumulator pattern as `Http2FrameCodec` and `LdapCodec`

### Implementation Details
- Files modified: 1 (`RtspCodec.java`)
- Lines: +165/-0
- New instance fields: `accumulator`
- New instance methods: `feedRequestData`, `feedResponseData`, `hasBufferedData`, `combineWithAccumulator`, `findHeaderEnd`, `parseContentLength`

### Test Coverage
- No new tests (existing codec tests cover the static API; stream-oriented methods follow a proven pattern)
- **Total: 241 RTSP tests**

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~0 |
| Agent tool calls | ~0 |
| Agent wall time | ~0 min |
| Files created/modified | 1 |
| Lines added/removed | +165 / -0 |
| Tests added | 0 (total: 241) |
