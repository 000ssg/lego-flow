# iot / coap — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `coap` module implements CoAP (RFC 7252) and its key extensions for constrained RESTful communication. It provides server and client implementations, built on the `service` module for UDP transport and `blocks` for data processing primitives.

## Key Interfaces

- `CoapServer` — resource server with URI routing, content negotiation, observe support
- `CoapClient` — request client with GET/PUT/POST/DELETE, observe, blockwise transfer
- `CoapMessage` — message model with type (CON/NON/ACK/RST), code, options, payload
- `CoapResource` — resource handler with content format negotiation
- `ObserveEngine` — RFC 7641 observer registry and notification delivery
- `BlockAssembler` — RFC 7959 Block1/Block2 payload assembly and size negotiation
- `MessageLayer` — reliability: retransmission, deduplication, congestion control

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `server` | CoAP server: resource registry, request dispatch, response building, multicast support |
| `client` | CoAP client: request construction, response handling, observe subscription management |
| `message` | Message model: Type (CON, NON, ACK, RST), Code (methods + response codes), Message ID, Token, Options |
| `resource` | Resource abstraction: URI path matching, handler interface, content format negotiation |
| `observe` | Observe engine (RFC 7641): observer registry, notification ordering, max-age expiry |
| `block` | Blockwise transfer (RFC 7959): Block1 (request payload), Block2 (response payload), size negotiation, assembly |
| `discovery` | Resource discovery: /.well-known/core endpoint, CoRE Link Format (RFC 6690) generation and parsing |
| `reliability` | Message layer: retransmission with exponential backoff, deduplication by Message ID, NSTART congestion control |
| `demo` | Demo applications: server, client requests, observe, block transfer, discovery |

## CoAP-Specific Coding Conventions

### Message Types
- **CON** (Confirmable): reliable, requires ACK
- **NON** (Non-confirmable): unreliable, no ACK needed
- **ACK** (Acknowledgement): confirms CON receipt, may carry piggybacked response
- **RST** (Reset): indicates inability to process message

### Response Codes (class.detail format)
- **2.xx Success**: 2.01 Created, 2.02 Deleted, 2.03 Valid, 2.04 Changed, 2.05 Content
- **4.xx Client Error**: 4.00 Bad Request, 4.01 Unauthorized, 4.04 Not Found, 4.05 Method Not Allowed
- **5.xx Server Error**: 5.00 Internal Server Error, 5.03 Service Unavailable

### Options
- Uri-Host (3), Uri-Port (7), Uri-Path (11), Uri-Query (15)
- Content-Format (12), Accept (17)
- Max-Age (14), ETag (4), If-Match (1), If-None-Match (5)
- Observe (6), Block1 (27), Block2 (23), Size1 (60), Size2 (28)

### Content Formats
- text/plain (0), application/link-format (40), application/xml (41)
- application/octet-stream (42), application/json (50), application/cbor (60)

## Testing Practices

- Unit tests for message codec: encode -> decode round-trip for all message types
- Option processing tests: critical vs elective, proxy-unsafe vs safe-to-forward
- Observe tests: registration, notification ordering, max-age expiry, deregistration
- Blockwise transfer tests: Block1/Block2 assembly with various block sizes
- Discovery tests: CoRE Link Format generation and parsing
- Reliability tests: retransmission timing, deduplication, congestion control
- All tests use loopback UDP (no external CoAP server required)
- Test count: 157
