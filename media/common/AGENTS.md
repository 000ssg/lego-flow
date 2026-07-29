# media / common — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `media/common` module implements the Session Description Protocol (SDP) as defined in RFC 4566. It provides a shared SDP parser, writer, negotiator, and typed model used by both the RTSP and SIP modules. Pure JDK implementation with no external dependencies beyond `blocks`.

## Key Interfaces

- `SdpParser` -- parses SDP text into a `SessionDescription` model (all RFC 4566 line types)
- `SdpWriter` -- serializes `SessionDescription` back to spec-compliant SDP text with CRLF endings
- `SdpNegotiator` -- implements RFC 3264 offer/answer model for media capability intersection
- `SessionBuilder` -- fluent builder for constructing `SessionDescription` programmatically
- `MediaBuilder` -- fluent builder for constructing `MediaDescription` with automatic attribute management
- `PayloadRegistry` -- registry of static (RFC 3551) and dynamic RTP payload types

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `sdp` | SDP model: `SessionDescription`, `Origin`, `ConnectionInfo`, `Timing`, `MediaDescription`, `RtpMap`, `FormatParameters`, `Attribute`, `Direction`, `MediaType`, `TransportProtocol`, `Bandwidth`, `RepeatTime`, `IceCandidate`, `Fingerprint` |
| `codec` | SDP codec: `SdpParser` (text to model), `SdpWriter` (model to text), `SdpNegotiator` (offer/answer via RFC 3264) |
| `payload` | RTP payload types: `PayloadType` (static RFC 3551 definitions + dynamic), `PayloadRegistry` (lookup and registration) |
| `builder` | Fluent builders: `SessionBuilder` (session-level), `MediaBuilder` (media-level) |

## SDP-Specific Coding Conventions

### SDP Line Types (all 14)
- `v=` (version), `o=` (origin), `s=` (session name), `i=` (info), `u=` (URI), `e=` (email), `p=` (phone)
- `c=` (connection info), `b=` (bandwidth), `t=` (timing), `r=` (repeat time), `z=` (timezone), `k=` (encryption key)
- `a=` (attribute), `m=` (media description)

### Model Design
- **Records for value types**: `Origin`, `Timing`, `Bandwidth`, `RepeatTime`, `RtpMap`, `FormatParameters`, `Attribute`, `IceCandidate`, `Fingerprint`, `ConnectionInfo`, `PayloadType` are all Java records
- **Enums for fixed vocabularies**: `Direction` (sendrecv/sendonly/recvonly/inactive), `MediaType` (audio/video/text/application/message), `TransportProtocol` (RTP/AVP, RTP/SAVP, RTP/AVPF, RTP/SAVPF, UDP, TCP, TCP/RTP/AVP)
- **Final classes for complex objects**: `SessionDescription`, `MediaDescription` are final classes with immutable collections (List.copyOf)
- **Each model class has**: `parse(String)` static factory, `format()` for serialization, `toString()` with SDP prefix

### Parser Design
- `SdpParser.parse()` uses a single-pass line-by-line approach with a state machine tracking session-level vs media-level context
- Internal `MediaDescriptionBuilder` accumulates media fields during parsing
- Unknown line types are silently ignored (forward compatibility)
- Direction attributes (`sendrecv`, `sendonly`, etc.) are detected and parsed into the `Direction` enum
- Special attributes (`rtpmap`, `fmtp`, `candidate`, `fingerprint`) are parsed into typed objects AND preserved as generic `Attribute` instances

### Writer Design
- `SdpWriter.write()` outputs lines in RFC 4566 prescribed order: v, o, s, i, u, e, p, c, b, t, r, z, k, a, m
- Uses CRLF (`\r\n`) line endings per spec
- Media-level attributes are written from the generic `attributes` list to preserve original ordering

### Negotiator Design
- `SdpNegotiator.negotiate()` implements RFC 3264 offer/answer
- Matches media descriptions by type and protocol
- Intersects formats using codec name + clock rate as the matching key
- Direction reversal: sendonly becomes recvonly and vice versa
- Rejected media uses port 0 per RFC 3264
- Returns `Optional.empty()` if no media is compatible

### Builder Design
- `SessionBuilder` defaults version to 0, session name to " ", and adds `Timing.PERMANENT` if no timing specified
- `MediaBuilder.rtpMap()` automatically adds the format number and creates the corresponding attribute
- Both builders produce immutable objects

### Payload Registry
- Static types (RFC 3551): 18 well-known payload types (PCMU=0, PCMA=8, G722=9, H264=dynamic, etc.)
- Dynamic types (96-127): registered at runtime via `registerDynamic()`
- Lookup order: dynamic types first, then static

## Testing Practices

- Unit tests for each SDP model record: parse, format, round-trip, validation, edge cases
- Parser tests: full SDP documents, all line types, multicast, missing optional fields
- Writer tests: serialization output matches expected SDP format
- Negotiator tests: offer/answer with compatible/incompatible media, direction reversal, rejected media
- Builder tests: fluent API, default values, automatic attribute management
- Payload tests: static type lookup, dynamic registration, range validation
- All tests use AssertJ assertions
- Test count: 180
