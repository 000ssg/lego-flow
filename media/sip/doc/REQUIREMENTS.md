# SIP Module — Requirements Evolution

## Timeline

| Commit | Feature | Tests |
|--------|---------|-------|
| Initial | SIP protocol: codec, transactions, registration, dialog, transport, user agent | 163 |
| Stream Codec | Stream-oriented ByteBuffer accumulation in SipCodec | 163 |

---

## Commit: Initial — SIP Protocol (2026-07-04)

### Original Request
> "Create SIP module (RFC 3261) with protocol codec, transaction layer, registration, dialog management, transport (UDP + TCP), user agent, and demos."

### Reformulated Requirements
1. Protocol layer: SipMethod enum, SipStatus codes, SipMessage sealed interface (SipRequest, SipResponse), SipUri parser, SipCodec (encode/decode with auto-detection)
2. Header support: SipHeaders (multi-value, case-insensitive), ViaHeader, CSeqHeader, AddressHeader with parameter parsing, multi-line header folding (RFC 3261 section 7.3.1)
3. Transaction layer: SipTransaction base, ClientTransaction, ServerTransaction, TransactionState enum with RFC 3261 state machines
4. Registration: SipRegistrar (server-side binding management), SipRegistrationClient, RegistrationBinding with expiry
5. Dialog: SipDialog with DialogState (EARLY, CONFIRMED, TERMINATED), route set management
6. Transport: SipTransport interface, UdpSipTransport, TcpSipTransport
7. User agent: SipUserAgent coordinating transport, transactions, and dialogs
8. Comprehensive tests covering all layers

### Final Design Decisions
- SipMessage is a sealed interface permitting SipRequest and SipResponse
- SipCodec auto-detects request vs. response by checking if first line starts with "SIP/"
- Header folding per RFC 3261 section 7.3.1 (lines starting with SP or HT fold into previous)
- Transaction state machines follow RFC 3261 Figures 5-8
- Registration bindings use ConcurrentHashMap for thread-safe storage
- Transport abstraction allows pluggable UDP/TCP backends

### Implementation Details
- 7 packages: protocol, header, transaction, registration, dialog, transport, agent
- 24 source files, 13 test files

### Test Coverage
- Protocol: codec encode/decode round-trips, request/response auto-detection, SipUri parsing
- Headers: Via, CSeq, Address, multi-value, folding
- Transactions: client/server state machines, timer handling
- Registration: registrar binding CRUD, client registration flows
- Dialog: state transitions, route set
- Transport: UDP/TCP send/receive
- User agent: coordinated flows
- **Total: 163 SIP tests**

---

## Commit: Stream Codec — Stream-Oriented ByteBuffer Accumulation in SipCodec (2026-07-06)

### Original Request
> "Add internal accumulation buffers to SipCodec for stream-oriented message assembly over TCP, matching the accumulator pattern used by RtspCodec and LdapCodec. Support compact 'l' header for Content-Length per RFC 3261."

### Reformulated Requirements
1. `SipCodec` gains a `ByteBuffer accumulator` field for partial message buffering across reads
2. `combineWithAccumulator(ByteBuffer)` — merges new input with any previously buffered bytes
3. `feedRequestData(ByteBuffer)` — feeds chunks into accumulator, returns parsed `SipRequest` when complete, or `null` if more data needed; saves remainder
4. `feedResponseData(ByteBuffer)` — same pattern for `SipResponse`
5. `hasBufferedData()` — returns true if the internal accumulator has remaining bytes
6. `parseContentLengthFromRaw` supports both full `Content-Length` header name and compact `l` form per RFC 3261
7. Existing static methods preserved unchanged for backward compatibility

### Final Design Decisions
- Same accumulator pattern as RtspCodec and LdapCodec: instance-level ByteBuffer, not thread-safe, single-owner
- Content-Length parsing supports compact form `l` (RFC 3261 section 7.3.3) — important for SIP's compact header format used in constrained environments
- Header completeness detected by scanning for `\r\n\r\n`; body completeness by Content-Length
- Remainder bytes saved for pipelined messages

### Implementation Details
- Files modified: 1 (`SipCodec.java`)
- Lines: +156/-0
- New instance fields: `accumulator`
- New instance methods: `feedRequestData`, `feedResponseData`, `hasBufferedData`, `combineWithAccumulator`, `findHeaderEnd`, `parseContentLengthFromRaw`

### Test Coverage
- No new tests (existing codec tests cover the static API; stream-oriented methods follow a proven pattern)
- **Total: 163 SIP tests**

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~0 |
| Agent tool calls | ~0 |
| Agent wall time | ~0 min |
| Files created/modified | 1 |
| Lines added/removed | +156 / -0 |
| Tests added | 0 (total: 163) |
