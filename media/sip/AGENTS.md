# media / sip — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `sip` module implements SIP (RFC 3261) from scratch. It provides protocol codec, transaction layer, registration, dialog management, dual transport (UDP + TCP), and user agent for VoIP signaling.

## Key Interfaces

- `SipCodec` — dual-mode codec: static methods for one-shot encode/decode, instance methods for stream-oriented ByteBuffer accumulation
- `SipMessage` — sealed interface: `SipRequest` + `SipResponse`
- `SipTransaction` / `ClientTransaction` / `ServerTransaction` — RFC 3261 transaction state machines
- `SipRegistrar` / `SipRegistrationClient` — registration binding management
- `SipDialog` — dialog lifecycle (EARLY/CONFIRMED/TERMINATED)
- `SipTransport` / `UdpSipTransport` / `TcpSipTransport` — pluggable transport
- `SipUserAgent` — high-level coordination

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | Codec, request/response, method/status enums, SIP URI parser |
| `header` | Typed header records: Via, CSeq, Address, generic SipHeaders |
| `transaction` | Client/server transaction state machines per RFC 3261 |
| `registration` | Registrar (server-side bindings), registration client |
| `dialog` | Dialog state management, route set |
| `transport` | UDP and TCP transport implementations |
| `agent` | SipUserAgent coordinating transport + transactions + dialogs |

## Stream-Oriented SipCodec

`SipCodec` supports two usage modes:
- **Static methods** (`encode`, `decode`, `decodeRequest`, `decodeResponse`) — stateless, thread-safe, for complete messages. Auto-detects request vs. response.
- **Instance methods** (`feedRequestData`, `feedResponseData`, `hasBufferedData`) — stateful stream-oriented decoding with internal `ByteBuffer` accumulator

The instance API handles TCP stream reassembly. Content-Length parsing supports both the full `Content-Length` header and the compact `l` form (RFC 3261 section 7.3.3). An instance is **not** thread-safe and should be owned by a single pipeline/connection.

This follows the same accumulator pattern as `RtspCodec`, `Http2FrameCodec`, and `LdapCodec`. The transport layer (`ProcessingThread`) passes raw read chunks; the codec handles message boundary detection and reassembly.

## SIP-Specific Conventions

### Header Folding
RFC 3261 section 7.3.1: lines starting with SP or HT are continuation lines folded into the previous header value.

### Compact Headers
RFC 3261 section 7.3.3 defines compact single-letter forms. The stream codec recognizes `l` as `Content-Length` for framing purposes.

### Dual Transport
SIP messages can travel over UDP (complete message per datagram) or TCP (byte stream requiring codec-level reassembly). The stream-oriented SipCodec is used for TCP; UDP does not need it.

## Testing Practices

- Protocol codec tests: encode/decode round-trips, auto-detection, compact headers
- Header tests: Via, CSeq, Address, folding, multi-value
- Transaction tests: client/server state machines
- Registration tests: registrar binding CRUD, client flows
- Dialog tests: state transitions, route set
- Test count: 163

## Dependencies
- media-common (shared SDP parser)
- slf4j-api (logging)
