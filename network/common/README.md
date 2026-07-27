# Lego Flow Network Common -- BER/ASN.1 Codec

Shared BER (Basic Encoding Rules) and DER (Distinguished Encoding Rules) codec for ASN.1 types, used by the LDAP, SNMP, and X.509-related modules.

## Features

- **Sealed ASN.1 type hierarchy** -- 14 types with exhaustive pattern matching support
- **BER encoder/decoder** -- full TLV encoding with short and long form tags/lengths, indefinite length
- **DER encoder** -- canonical encoding with sorted SET elements and definite lengths only
- **OID support** -- value object, dotted string parsing, registry with 30+ well-known OIDs
- **JDK-only** -- zero external dependencies (except SLF4J for logging)

## Package Structure

```
ssg.legoflow.network.common/
+-- asn1/           Sealed ASN.1 type hierarchy
|   +-- Asn1Type              Sealed interface for all types
|   +-- Asn1Tag               Tag class, number, constructed flag
|   +-- Asn1Boolean           BOOLEAN (0x01)
|   +-- Asn1Integer           INTEGER (0x02), arbitrary precision
|   +-- Asn1BitString         BIT STRING (0x03) with unused bits
|   +-- Asn1OctetString       OCTET STRING (0x04), raw bytes
|   +-- Asn1Null              NULL (0x05)
|   +-- Asn1ObjectIdentifier  OBJECT IDENTIFIER (0x06)
|   +-- Asn1Enumerated        ENUMERATED (0x0A)
|   +-- Asn1Utf8String        UTF8String (0x0C)
|   +-- Asn1PrintableString   PrintableString (0x13)
|   +-- Asn1IA5String         IA5String (0x16)
|   +-- Asn1GeneralizedTime   GeneralizedTime (0x18)
|   +-- Asn1Sequence          SEQUENCE (0x30), ordered
|   +-- Asn1Set               SET (0x31), unordered
|   +-- Asn1ContextSpecific   Context-specific [0], [1], ...
+-- ber/            BER encoding/decoding
|   +-- BerEncoder            Encode Asn1Type -> ByteBuffer
|   +-- BerDecoder            Decode ByteBuffer -> Asn1Type
|   +-- BerTag                TLV tag parsing (short + long form)
|   +-- BerLength             Length encoding (short, long, indefinite)
|   +-- BerUtils              Helpers: peek tag, base-128, EOC
|   +-- BerDecodingException  Malformed data exception
+-- der/            DER-specific encoding
|   +-- DerEncoder            Canonical encoding, sorted SETs
+-- oid/            Object Identifier support
    +-- ObjectIdentifier      OID value object, dotted string parsing
    +-- OidRegistry           Name <-> OID lookup registry
    +-- StandardOids          Well-known OID constants
```

## Usage

### Encoding

```java
// Build an ASN.1 structure
var message = Asn1Sequence.of(
    Asn1Integer.of(1),
    Asn1OctetString.of("public"),
    Asn1Sequence.of(
        new Asn1ObjectIdentifier(StandardOids.SYS_DESCR),
        Asn1Null.INSTANCE
    )
);

// BER encode
ByteBuffer ber = BerEncoder.encode(message);

// DER encode (canonical)
ByteBuffer der = DerEncoder.encode(message);
```

### Decoding

```java
ByteBuffer data = ...; // received BER bytes
Asn1Type decoded = BerDecoder.decode(data);

// Pattern matching on sealed type
switch (decoded) {
    case Asn1Sequence seq -> processSequence(seq);
    case Asn1Integer i -> processInteger(i);
    // ...exhaustive
}
```

### OID Registry

```java
OidRegistry registry = OidRegistry.instance();
String name = registry.displayName(StandardOids.SYS_DESCR);
// "sysDescr (1.3.6.1.2.1.1.1)"

registry.register(ObjectIdentifier.parse("1.3.6.1.4.1.99999.1"), "myCustomOid");
```

## BER Encoding Rules

| Feature | BER | DER |
|---------|-----|-----|
| Length form | Short, long, or indefinite | Definite only |
| SET order | Any | Sorted by tag |
| BOOLEAN true | Any non-zero | 0xFF only |
| BIT STRING unused | 0-7 | Must be zero |

## Test Coverage

- 100+ tests covering all 14 ASN.1 types
- Round-trip encode/decode for every type
- Tag encoding: short form, long form, all 4 classes
- Length encoding: short (0-127), long (128+), indefinite
- INTEGER: positive, negative, zero, BigInteger, minimal encoding
- OID: all first-arc values (0/1/2), large arcs, standard OIDs
- Real-world structures: LDAP bind/search, SNMP get/response/trap, X.509 RDN
- DER: canonical SET ordering, definite length enforcement

## Documentation

- [Architecture](doc/ARCHITECTURE.md)
- [Requirements](doc/REQUIREMENTS.md)
- [Compliance](doc/COMPLIANCE.md)
