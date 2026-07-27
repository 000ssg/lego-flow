# LDAP Module — Development Guide

## Module Purpose

The `ldap` module implements LDAP v3 (RFC 4511) from scratch. It provides BER codec for all protocol operations, DN parsing (RFC 4514), search filter parsing (RFC 4515), client, and server with pluggable directory backend.

## Key Interfaces

- `LdapCodec` — dual-mode BER codec: static methods for one-shot encode/decode, instance methods for stream-oriented ByteBuffer accumulation
- `LdapProtocolOp` — sealed interface with 17 operation records (Bind, Search, Modify, Add, Delete, etc.)
- `LdapMessage` — top-level message: messageId + protocolOp + controls
- `DnParser` / `DistinguishedName` — RFC 4514 DN parsing
- `FilterParser` / `SearchFilter` — RFC 4515 search filter parsing
- `LdapClient` — client with bind, search, modify, add, delete, compare, extended
- `LdapServer` / `DirectoryBackend` — server with pluggable storage (InMemoryDirectoryBackend)

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `codec` | BER encoder/decoder for LDAP messages, filter BER codec |
| `protocol` | All 17 operation records, LdapMessage, LdapResult, result codes, attributes |
| `dn` | Distinguished name parsing per RFC 4514 |
| `filter` | Search filter sealed hierarchy and parser per RFC 4515 |
| `control` | LDAP controls (paged results, sort) |
| `client` | LDAP client operations |
| `server` | LDAP server, directory backend interface, in-memory backend |

## Stream-Oriented LdapCodec

`LdapCodec` supports two usage modes:
- **Static methods** (`encode`, `decode`, `tryDecode`, `encodeToBytes`) — stateless, thread-safe, for complete messages
- **Instance methods** (`decodeStream`, `hasBufferedData`) — stateful stream-oriented decoding with internal `ByteBuffer` accumulator

The instance API handles TCP stream reassembly for BER-encoded messages. Unlike text-based protocols, LDAP uses BER TLV framing where the Length field determines message boundaries. `decodeStream` returns a `List<LdapMessage>` because multiple messages may arrive in a single read. An empty list means more data is needed.

This follows the same accumulator pattern as `Http2FrameCodec`, `RtspCodec`, and `SipCodec`. The transport layer (`ProcessingThread`) passes raw read chunks; the codec handles BER boundary detection and reassembly. An instance is **not** thread-safe and should be owned by a single pipeline/connection.

## LDAP-Specific Conventions

### BER Encoding
All LDAP messages use APPLICATION-tagged BER structures as defined in the RFC 4511 ASN.1 schema. The codec depends on `network-common` for BER/ASN.1 primitives.

### Sealed Hierarchies
`LdapProtocolOp` is a sealed interface with 17 record implementations. `SearchFilter` is a sealed hierarchy of filter types.

## Testing Practices

- Codec tests: BER encode/decode round-trips for all 17 operations, tryDecode for partial data
- DN tests: parsing, formatting, RFC 4514 escaping edge cases
- Filter tests: all filter types, nested expressions, RFC 4515 syntax
- Protocol tests: LdapMessage construction, result codes
- Client-server integration: full workflows against in-memory backend
- Test count: 205

## Dependencies
- network-common (shared BER/ASN.1 codec)
- slf4j-api (logging)

## Commit Rules
- Update doc/REQUIREMENTS.md with commit section
- Update doc/ARCHITECTURE.md if architecture changed
- Update README.md for API changes

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
