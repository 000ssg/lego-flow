# network / dns — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `dns` module implements the Domain Name System protocol (RFC 1034/1035) with extensions for DNSSEC (RFC 4033-4035), DNS-over-HTTPS (RFC 8484), and DNS-over-TLS (RFC 7858). It provides a complete DNS stack: wire-format codec, authoritative server, recursive and stub resolvers, a high-level lookup client, and four transport implementations (UDP, TCP, DoH, DoT).

## Key Interfaces

- `DnsCodec` — binary codec for DNS messages: encode/decode with name compression (0xC0 pointers)
- `DnsMessage` — immutable message with header, questions, answer/authority/additional sections; builder pattern
- `DnsServer` — UDP+TCP server using virtual threads, delegates to `DnsHandler`
- `DnsHandler` — functional interface for query handling
- `AuthoritativeZone` — zone with SOA, record storage indexed by name+type, wildcard lookup, CNAME chasing
- `ZoneFile` — BIND-format zone file parser ($ORIGIN, $TTL, multi-line parentheses)
- `DnsClient` — UDP-first client with TCP fallback on truncation; sync + async
- `DnsLookup` — high-level convenience: resolveA, resolveAAAA, lookupMx, lookupSrv, lookupTxt, reverseLookup
- `DnsResolver` — resolver interface with `StubResolver` (forwarding+caching) and `RecursiveResolver` (iterative from root hints)
- `DnsCache` — TTL-based cache with ConcurrentHashMap, automatic expiry, TTL adjustment on read
- `RData` — sealed interface for typed RDATA; 19 permitted subtypes via pattern matching
- `DnssecValidator` — RRSIG verification (RSA-SHA256, ECDSA-P256-SHA256) and DS digest validation

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | Wire format: `DnsMessage`, `DnsHeader` (12-byte record), `DnsName` (case-insensitive, compression-aware), `DnsRecord`, `DnsQuestion`, `DnsCodec` (encode/decode with name compression), `RecordType` (19 types), `RecordClass`, `OpCode`, `ResponseCode`, `DnsFormatException` |
| `rdata` | Typed RDATA: `RData` sealed interface + `ARecord`, `AaaaRecord`, `NsRecord`, `CnameRecord`, `PtrRecord`, `MxRecord`, `SoaRecord`, `TxtRecord`, `SrvRecord`, `NaptrRecord`, `CaaRecord`, `OptRecord` (EDNS0), `DnskeyRecord`, `RrsigRecord`, `DsRecord`, `NsecRecord`, `Nsec3Record`, `Nsec3ParamRecord`, `RawRData` |
| `rdata.dnssec` | DNSSEC utilities: `DnssecValidator` (RRSIG + DS verification), `TypeBitMaps` (NSEC/NSEC3 bitmap encode/decode) |
| `server` | DNS server: `DnsServer` (UDP+TCP dual transport, virtual threads), `DnsHandler` (functional query handler), `AuthoritativeZone` (zone storage, lookup, wildcard, CNAME, NXDOMAIN), `ZoneFile` (BIND zone parser) |
| `resolver` | Resolvers: `DnsResolver` interface, `StubResolver` (forwarding with caching), `RecursiveResolver` (iterative from root hints, referral following), `DnsCache` (TTL-based, thread-safe) |
| `client` | Client: `DnsClient` (UDP-first with TCP fallback, sync+async), `DnsLookup` (high-level A/AAAA/MX/SRV/TXT/PTR convenience) |
| `transport` | Transport layers: `UdpTransport` (RFC 1035), `TcpTransport` (2-byte length prefix), `DohTransport` (RFC 8484, POST+GET), `DotTransport` (RFC 7858, TLS on port 853) |
| `demo` | Demo applications: `DnsServerDemo`, `ZoneManagementDemo` |

## DNS-Specific Coding Conventions

### Record Types (19 supported)
- Standard (RFC 1035): A(1), NS(2), CNAME(5), SOA(6), PTR(12), MX(15), TXT(16)
- Extended: AAAA(28), SRV(33), NAPTR(35), OPT(41), CAA(257), ANY(255)
- DNSSEC (RFC 4034/5155): DS(43), RRSIG(46), NSEC(47), DNSKEY(48), NSEC3(50), NSEC3PARAM(51)

### RDATA Sealed Hierarchy
All RDATA types implement the `RData` sealed interface. The codec uses exhaustive `switch` with pattern matching — no default branch needed. Adding a new record type requires:
1. Create a record class implementing `RData`
2. Add it to the `permits` clause in `RData`
3. Add encode/decode cases in `DnsCodec`
4. Add the value to `RecordType` enum

### Message Wire Format
- Header is always 12 bytes: ID(2) + Flags(2) + QDCOUNT(2) + ANCOUNT(2) + NSCOUNT(2) + ARCOUNT(2)
- Names use label-length encoding with optional 0xC0 compression pointers
- Multi-byte integers are big-endian
- UDP limit is 512 bytes (without EDNS0); TCP uses 2-byte length prefix

### Transport Strategy
- `DnsClient` tries UDP first, falls back to TCP if response has TC (truncation) flag
- `DohTransport` supports both POST (binary body) and GET (base64url query param)
- `DotTransport` uses TLS over port 853 with same 2-byte length prefix as TCP

### Zone Handling
- `AuthoritativeZone` stores records in `ConcurrentHashMap<DnsName, Map<RecordType, List<DnsRecord>>>`
- Lookup order: exact match -> wildcard match (*.parent) -> CNAME check -> NXDOMAIN
- `ZoneFile` parser handles $ORIGIN, $TTL directives, multi-line SOA with parentheses, and @ shorthand

### DNSSEC Validation
- `DnssecValidator.verify()` checks RRSIG temporal validity, key tag, algorithm match, then cryptographic signature
- Supports algorithm 8 (RSA/SHA-256) and 13 (ECDSA-P256-SHA256)
- `DnssecValidator.verifyDs()` validates DS digests (SHA-1 type 1, SHA-256 type 2)
- ECDSA signatures are converted from raw r||s format to DER for JCA verification

## Testing Practices

- Unit tests for domain name parsing: case insensitivity, subdomain checks, wildcard matching, wire length, edge cases (root, long labels, empty labels)
- Unit tests for record type enum: value round-trip, unknown type handling
- All tests use JUnit 5 with AssertJ
- Parameterized tests for round-trip parsing and enum coverage
- Test count: 25
