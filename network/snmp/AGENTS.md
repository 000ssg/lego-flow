# network / snmp — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `snmp` module implements SNMPv3 (Simple Network Management Protocol version 3) per RFC 3411-3418. It provides agent (server) and manager (client) implementations with USM security and VACM access control, built on the shared `network/common` BER/ASN.1 codec, the `service` module for lifecycle management, and `blocks` for data processing primitives.

## Key Interfaces

- `SnmpAgent` -- agent (server) with UDP listener, MIB tree, request processing (GET/GETNEXT/GETBULK/SET), trap/inform sender
- `SnmpManager` -- manager (client) with GET/GETNEXT/GETBULK/SET operations, trap/inform listener, retransmission
- `SnmpCodec` -- stateless BER codec for SNMPv3 messages, PDUs, VarBinds, USM security parameters
- `SnmpPdu` -- sealed interface with 7 PDU types: GetRequest, GetNextRequest, Response, SetRequest, GetBulkRequest, InformRequest, TrapV2
- `SnmpValue` -- sealed interface with 12 SMIv2 data types: Integer32, Counter32, Counter64, Gauge32, TimeTicks, OctetString, Oid, IpAddress, Opaque, Null, NoSuchObject, NoSuchInstance, EndOfMibView
- `MibTree` -- thread-safe in-memory MIB tree using ConcurrentSkipListMap for sorted OID storage
- `UsmEngine` -- USM security engine: HMAC-MD5-96/HMAC-SHA-96 auth, DES-CBC/AES-128-CFB privacy
- `VacmAccessControl` -- view-based access control with security-to-group, access table, view tree family

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | Message format: SnmpMessage (v3 wrapper), ScopedPdu, SnmpPdu (7 types), VarBind/VarBindList, SnmpValue (12 types), SnmpCodec (BER), SecurityLevel, UsmSecurityParameters, SnmpOids (well-known OIDs) |
| `security` | USM and VACM: UsmEngine (auth+priv), UsmUser (credentials), UsmKeyUtils (password-to-key, key localization), AuthProtocol (HMAC-MD5-96, HMAC-SHA-96), PrivProtocol (DES-CBC, AES-128-CFB), VacmAccessControl (RFC 3415) |
| `server` | Agent: SnmpAgent (UDP listener, request dispatcher, trap/inform sender), MibTree (sorted OID-value store with GETNEXT/GETBULK/subtree support) |
| `client` | Manager: SnmpManager (GET/GETNEXT/GETBULK/SET, retransmission with timeout, trap/inform listener) |

## SNMP-Specific Coding Conventions

### PDU Types (all 7 SNMPv3 PDU types)
- GetRequest (tag 0), GetNextRequest (tag 1), Response (tag 2), SetRequest (tag 3)
- GetBulkRequest (tag 5), InformRequest (tag 6), TrapV2 (tag 7)
- Sealed interface `SnmpPdu` enables exhaustive pattern matching in `switch` expressions

### SMIv2 Data Types (sealed `SnmpValue`)
- Primitives: Integer32, Counter32, Counter64, Gauge32, TimeTicks
- Strings: OctetString (byte[]), Oid (ObjectIdentifier), IpAddress (4 bytes), Opaque
- Special: Null, NoSuchObject, NoSuchInstance, EndOfMibView

### Security Levels (RFC 3411)
- `NO_AUTH_NO_PRIV` (0x00): no authentication, no encryption
- `AUTH_NO_PRIV` (0x01): HMAC authentication only
- `AUTH_PRIV` (0x03): HMAC authentication + cipher encryption
- Privacy without authentication (0x02) is invalid per spec

### USM Key Derivation (RFC 3414 Section A.2)
- Password-to-key: cyclic 1 MB expansion then hash (MD5 or SHA-1)
- Key localization: Hash(masterKey + engineID + masterKey)
- Privacy key derived same way, truncated to cipher key length

### BER Encoding
- Uses shared `ssg.legoflow.network.common` BER/ASN.1 codec
- Application-tagged values (Counter32, Gauge32, etc.) mapped through context-specific tags
- PDU types encoded as implicit constructed context-specific tags

### MIB Tree
- ConcurrentSkipListMap for lock-free sorted OID storage
- Supports exact get, lexicographic getNext (higherEntry), getCeiling, subtree queries
- ObjectIdentifier implements Comparable for natural OID ordering

## Testing Practices

- Unit tests for BER codec: encode -> decode round-trip for all 7 PDU types and all value types
- USM tests: HMAC-MD5-96 and HMAC-SHA-96 digest computation and verification, tamper detection
- Encryption tests: DES-CBC and AES-128-CFB encrypt/decrypt round-trips
- Key derivation tests: password-to-key determinism, engine ID differentiation, key localization
- VACM tests: security-to-group mapping, access table lookups, view inclusion/exclusion with masks
- MibTree tests: get, getNext, getCeiling, subtree, put, remove, clear, boundary conditions
- Agent tests: processRequest for GET/GETNEXT/GETBULK/SET/INFORM, noSuchObject, endOfMibView
- Integration tests: SnmpManager + SnmpAgent over loopback UDP (GET, GETNEXT, GETBULK, SET)
- All tests use loopback transport (no external SNMP agent required)
- Test count: 113
