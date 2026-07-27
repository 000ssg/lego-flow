# Network Common Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 192
- **Dependencies**: blocks (via `api`), SLF4J (logging)
- **Standards**: ITU-T X.680 (ASN.1), ITU-T X.690 (BER/DER)

---

## Requirements

### ASN.1 Type System
1. Provide a sealed type hierarchy (`Asn1Type`) for compile-time exhaustive pattern matching
2. Support all common universal types: BOOLEAN (0x01), INTEGER (0x02), BIT STRING (0x03), OCTET STRING (0x04), NULL (0x05), OBJECT IDENTIFIER (0x06), ENUMERATED (0x0A), UTF8String (0x0C), PrintableString (0x13), IA5String (0x16), GeneralizedTime (0x18)
3. Support constructed types: SEQUENCE (0x30, ordered), SET (0x31, unordered)
4. Support context-specific tagged values with both explicit (constructed) and implicit (primitive) encoding
5. All types must be immutable (records with defensive copying of mutable fields)
6. INTEGER must support arbitrary precision via BigInteger
7. SEQUENCE and SET must provide Builder pattern for incremental construction

### BER Encoding (BerEncoder)
1. Encode any `Asn1Type` to a `ByteBuffer` in TLV (Tag-Length-Value) format
2. Support short-form tags (number 0-30, single byte) and long-form tags (number 31+, multi-byte)
3. Support short-form lengths (0-127, single byte) and long-form lengths (128+, multi-byte up to 4 bytes)
4. Use definite length encoding for all produced output
5. Two-phase encoding: compute `encodedSize()` first, then write via `encodeTo()`
6. Stateless and thread-safe (static utility methods only)

### BER Decoding (BerDecoder)
1. Decode any BER-encoded `ByteBuffer` back to an `Asn1Type`
2. Handle definite and indefinite length encodings
3. Decode all 4 tag classes: UNIVERSAL, APPLICATION, CONTEXT_SPECIFIC, PRIVATE
4. Decode context-specific tags as explicit (constructed) or implicit (primitive)
5. Support indefinite length for SEQUENCE, SET, and constructed context-specific types (EOC terminator 0x00 0x00)
6. Throw `BerDecodingException` on malformed data with descriptive messages
7. Provide `decodeAll()` for reading multiple TLV elements from a buffer

### DER Encoding (DerEncoder)
1. Produce canonical DER encoding as a strict subset of BER
2. Sort SET elements by their encoded tag byte values
3. Use definite length only (no indefinite length in output)
4. Recursively normalize nested structures (SEQUENCE, SET, context-specific)
5. Delegate to BerEncoder after normalization (no duplicated encoding logic)

### OID Support
1. `ObjectIdentifier`: immutable value object with dotted-string parsing and integer-array construction
2. Validate OID constraints: minimum 2 arcs, first arc 0/1/2, second arc < 40 when first is 0 or 1
3. Support prefix matching (`startsWith`), child creation (`child`), and lexicographic comparison
4. `OidRegistry`: thread-safe singleton with bidirectional name-to-OID and OID-to-name lookup
5. Pre-register 30+ well-known OIDs: X.500 attribute types, SNMP MIB-2, LDAP controls, X.509 extensions, algorithms
6. `StandardOids`: public constants for all pre-registered OIDs
7. Runtime registration of custom OIDs

### Tag Handling (BerTag)
1. Encode/decode tag byte: class (2 bits), constructed (1 bit), number (5 bits) for short form
2. Long-form tag encoding: first byte with number=0x1F, followed by base-128 continuation bytes
3. Compute encoded tag length without allocating

### Length Handling (BerLength)
1. Encode/decode short-form length (0-127)
2. Encode/decode long-form length (128+, up to 4 bytes)
3. Decode indefinite length marker (0x80) returning sentinel value
4. Encode/decode end-of-contents octets (0x00 0x00)
5. Reject length encodings exceeding 4 bytes

### Utilities (BerUtils)
1. Peek at next tag without consuming buffer position
2. Detect and consume end-of-contents markers
3. Encode/decode base-128 variable-length integers (used in OID arcs and long-form tags)
4. Compute TLV size and base-128 length without encoding

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
