# network / common — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `network/common` module provides a shared BER (Basic Encoding Rules) and DER (Distinguished Encoding Rules) codec for ASN.1 types. It is the foundation layer used by LDAP, SNMP, and X.509-related modules for binary encoding and decoding of structured data.

## Key Interfaces

- `Asn1Type` -- sealed interface for all 14 ASN.1 types, enabling exhaustive pattern matching in `switch`
- `BerEncoder` -- stateless encoder: `Asn1Type` -> `ByteBuffer` (TLV format)
- `BerDecoder` -- stateless decoder: `ByteBuffer` -> `Asn1Type` (definite + indefinite length)
- `DerEncoder` -- canonical encoder with sorted SET elements and definite lengths only
- `ObjectIdentifier` -- immutable OID value object with dotted-string parsing, prefix matching, comparison
- `OidRegistry` -- thread-safe singleton mapping OIDs to/from human-readable names (30+ pre-registered)

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `asn1` | Sealed ASN.1 type hierarchy: 14 types (`Asn1Boolean`, `Asn1Integer`, `Asn1BitString`, `Asn1OctetString`, `Asn1Null`, `Asn1ObjectIdentifier`, `Asn1Enumerated`, `Asn1Utf8String`, `Asn1PrintableString`, `Asn1IA5String`, `Asn1GeneralizedTime`, `Asn1Sequence`, `Asn1Set`, `Asn1ContextSpecific`) + `Asn1Tag` record |
| `ber` | BER encoder/decoder: `BerEncoder` (encode), `BerDecoder` (decode), `BerTag` (short/long form tag parsing), `BerLength` (short/long/indefinite length), `BerUtils` (peek, EOC, base-128), `BerDecodingException` |
| `der` | DER encoder: `DerEncoder` normalizes ASN.1 trees (sorts SETs by tag), then delegates to `BerEncoder` |
| `oid` | OID support: `ObjectIdentifier` (immutable value object), `OidRegistry` (name<->OID lookup), `StandardOids` (30+ well-known constants for X.500, SNMP, LDAP, X.509) |

## ASN.1 Type Hierarchy

All 14 types are `record` classes implementing the `sealed interface Asn1Type`:
- Primitive: `Asn1Boolean`, `Asn1Integer` (BigInteger), `Asn1BitString`, `Asn1OctetString`, `Asn1Null`, `Asn1ObjectIdentifier`, `Asn1Enumerated`, `Asn1Utf8String`, `Asn1PrintableString`, `Asn1IA5String`, `Asn1GeneralizedTime`
- Constructed: `Asn1Sequence` (ordered), `Asn1Set` (unordered), both with `Builder`
- Tagged: `Asn1ContextSpecific` (explicit/constructed or implicit/primitive)

## BER TLV Encoding Details

### Tag Encoding (BerTag)
- **Short form**: tag number 0-30, single byte: `[class(2) | constructed(1) | number(5)]`
- **Long form**: tag number 31+, first byte has `number=0x1F`, followed by base-128 continuation bytes

### Length Encoding (BerLength)
- **Short form**: 0-127, single byte
- **Long form**: first byte = `0x80 | N`, followed by N bytes of big-endian length
- **Indefinite form**: single byte `0x80`, content terminated by `0x00 0x00` (end-of-contents)
- Maximum long-form length: 4 bytes (32-bit)

### OID Encoding
- First two arcs combined: `40 * arc[0] + arc[1]`, then base-128 encoded
- Remaining arcs: each individually base-128 encoded
- First arc must be 0, 1, or 2; if 0 or 1, second arc must be < 40

## Module-Specific Coding Conventions

- All encoder/decoder classes are `final` with private constructors (utility classes, stateless)
- `Asn1Type` implementations are `record` types with compact canonical constructors for validation
- Defensive copying on `byte[]` fields (`Asn1OctetString`, `Asn1BitString`, `Asn1ContextSpecific`)
- `Asn1Sequence` and `Asn1Set` use `List.copyOf()` for immutability
- `DerEncoder` normalizes the ASN.1 tree (sorts SET elements by encoded tag bytes) then delegates to `BerEncoder`
- `OidRegistry` uses `ConcurrentHashMap` for thread-safe runtime registration

## Dependencies

- `blocks` module (via `api` dependency)
- SLF4J for logging (implementation dependency)
- No other external dependencies (JDK-only codec)

## Testing Practices

- Round-trip encode/decode for every ASN.1 type (`BerEncoderDecoderTest`: 57 tests)
- Tag encoding: short form, long form, all 4 tag classes (`BerTagTest`: 18 tests)
- Length encoding: short, long, indefinite, edge cases (`BerLengthTest`: 17 tests)
- Utility methods: peek, EOC, base-128 (`BerUtilsTest`: 10 tests)
- Real-world protocol structures: LDAP bind/search/entry, SNMP get/response/trap, X.509 RDN/AlgorithmIdentifier (`RealWorldProtocolTest`: 11 tests)
- DER canonical encoding: SET sorting, nested normalization (`DerEncoderTest`: 11 tests)
- ASN.1 type validation and factory methods (`Asn1TypeTest`: 40 tests)
- OID parsing, validation, prefix matching, child creation (`ObjectIdentifierTest`: 20 tests)
- OID registry lookup, registration, display names (`OidRegistryTest`: 8 tests)
- All tests use in-memory ByteBuffers (no external dependencies)
- Test count: 192
