# DNS Compliance Report

## Specifications Covered
- RFC 1034 — Domain Names: Concepts and Facilities (November 1987)
- RFC 1035 — Domain Names: Implementation and Specification (November 1987)
- RFC 4033-4035 — DNS Security Extensions (DNSSEC) (March 2005)
- RFC 5155 — DNS Security (DNSSEC) Hashed Authenticated Denial of Existence (March 2008)
- RFC 8484 — DNS Queries over HTTPS (DoH) (October 2018)
- RFC 7858 — DNS over Transport Layer Security (DoT) (May 2016)

## Compliance Matrix

### RFC 1035 — Message Format

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 4.1.1 | Header format (12 bytes: ID, flags, counts) | ✅ Implemented | `DnsHeader` record with `flags()` and `fromFlags()` methods |
| 4.1.1 | QR flag (query/response) | ✅ Implemented | `DnsHeader.qr()`; `DnsMessage.isResponse()` |
| 4.1.1 | OPCODE field (4 bits) | ✅ Implemented | `OpCode` enum: QUERY, IQUERY, STATUS, NOTIFY, UPDATE |
| 4.1.1 | AA flag (authoritative answer) | ✅ Implemented | `DnsHeader.aa()`; set by `AuthoritativeZone.handleQuery()` |
| 4.1.1 | TC flag (truncation) | ✅ Implemented | `DnsHeader.tc()`; `DnsServer` sets TC on UDP overflow |
| 4.1.1 | RD flag (recursion desired) | ✅ Implemented | `DnsHeader.rd()`; set by `DnsMessage.query()` |
| 4.1.1 | RA flag (recursion available) | ✅ Implemented | `DnsHeader.ra()`; set by resolvers in responses |
| 4.1.1 | RCODE field (4 bits) | ✅ Implemented | `ResponseCode` enum: NOERROR through NOTZONE (11 codes) |
| 4.1.1 | Section counts (QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT) | ✅ Implemented | `DnsHeader` fields; auto-calculated by `DnsMessage.Builder` |
| 4.1.2 | Question section (QNAME, QTYPE, QCLASS) | ✅ Implemented | `DnsQuestion` record; `DnsCodec` encode/decode |
| 4.1.3 | Resource record format (NAME, TYPE, CLASS, TTL, RDLENGTH, RDATA) | ✅ Implemented | `DnsRecord` record; `DnsCodec` encode/decode |
| 4.1.4 | Name compression (pointer with 0xC0 prefix) | ✅ Implemented | `DnsCodec.encodeName()` / `decodeName()` with compression map |
| 4.2.1 | UDP transport | ✅ Implemented | `UdpTransport`; `DnsServer` UDP listener |
| 4.2.2 | TCP transport with 2-byte length prefix | ✅ Implemented | `TcpTransport`; `DnsServer` TCP listener |
| 5 | Zone file format ($ORIGIN, $TTL, @, parentheses) | ✅ Implemented | `ZoneFile.parse()` |

### RFC 1035 — Domain Names

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 2.3.1 | Label length maximum 63 octets | ✅ Implemented | `DnsName.of()` validates; `DnsNameTest.testOfRejectsLongLabel` |
| 2.3.4 | Name length maximum 255 octets | ⚠️ Partial | Wire length computed via `wireLength()` but total 255-byte limit not enforced at parse time |
| 3.1 | Case-insensitive name comparison | ✅ Implemented | `DnsName` uses lowercase canonical form; `DnsNameTest.testCaseInsensitiveEquality` |
| 3.3 | Standard RR types (A, NS, CNAME, SOA, PTR, MX, TXT) | ✅ Implemented | `RecordType` enum + corresponding `RData` implementations |
| 4.1.4 | Compression pointer loop prevention | ✅ Implemented | `DnsCodec.decodeName()` limits to 128 jumps |

### RFC 1035 — Resource Record Types

| Type | Value | Status | Verification |
|------|-------|--------|-------------|
| A | 1 | ✅ Implemented | `ARecord` (Inet4Address); `RecordTypeTest` |
| NS | 2 | ✅ Implemented | `NsRecord` (DnsName); `RecordTypeTest` |
| CNAME | 5 | ✅ Implemented | `CnameRecord` (DnsName); `RecordTypeTest` |
| SOA | 6 | ✅ Implemented | `SoaRecord` (mname, rname, serial, refresh, retry, expire, minimum); `RecordTypeTest` |
| PTR | 12 | ✅ Implemented | `PtrRecord` (DnsName); `RecordTypeTest` |
| MX | 15 | ✅ Implemented | `MxRecord` (preference, exchange); `RecordTypeTest` |
| TXT | 16 | ✅ Implemented | `TxtRecord` (list of character-strings); `RecordTypeTest` |

### RFC 3596 — IPv6 DNS

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 2.1 | AAAA record type (value 28) | ✅ Implemented | `AaaaRecord` (Inet6Address); `RecordTypeTest` |
| 2.5 | IPv6 reverse lookup (ip6.arpa) | ✅ Implemented | `DnsLookup.reverseLookup()` builds nibble-reversed ip6.arpa name |

### RFC 2782 — SRV Records

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| - | SRV record type (value 33) | ✅ Implemented | `SrvRecord` (priority, weight, port, target); `RecordTypeTest` |
| - | Priority and weight-based ordering | ✅ Implemented | `SrvRecord` implements `Comparable`; `DnsLookup.lookupSrv()` sorts |

### RFC 3403 — NAPTR Records

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| - | NAPTR record type (value 35) | ✅ Implemented | `NaptrRecord` (order, preference, flags, service, regexp, replacement) |

### RFC 8659 — CAA Records

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| - | CAA record type (value 257) | ✅ Implemented | `CaaRecord` (flags, tag, value) |

### RFC 6891 — EDNS0

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 6.1.2 | OPT pseudo-record (type 41) | ✅ Implemented | `OptRecord` (udpPayloadSize, extendedRcode, version, dnssecOk, options) |
| 6.1.2 | OPT in additional section | ✅ Implemented | `DnsCodec` handles OPT encoding with class=UDP size, TTL=flags |
| 6.1.3 | EDNS options | ✅ Implemented | `OptRecord.EdnsOption` (code, data) |
| 6.1.4 | DNSSEC OK (DO) flag | ✅ Implemented | `OptRecord.dnssecOk()` parsed from TTL field |

### RFC 4034 — DNSSEC Resource Records

| Type | Value | Status | Verification |
|------|-------|--------|-------------|
| DS | 43 | ✅ Implemented | `DsRecord` (keyTag, algorithm, digestType, digest); `RecordTypeTest` |
| RRSIG | 46 | ✅ Implemented | `RrsigRecord` (typeCovered, algorithm, labels, origTtl, expiration, inception, keyTag, signerName, signature); `RecordTypeTest` |
| NSEC | 47 | ✅ Implemented | `NsecRecord` (nextDomainName, types via TypeBitMaps); `RecordTypeTest` |
| DNSKEY | 48 | ✅ Implemented | `DnskeyRecord` (flags, protocol, algorithm, publicKey); `RecordTypeTest` |

### RFC 4035 — DNSSEC Protocol

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 2.2 | AD flag in header (authenticated data) | ✅ Implemented | `DnsHeader.ad()` |
| 2.2 | CD flag in header (checking disabled) | ✅ Implemented | `DnsHeader.cd()` |
| 5.3.1 | RRSIG validation (temporal, key tag, algorithm, signature) | ✅ Implemented | `DnssecValidator.verify()` |
| 5.3.1 | RSA-SHA256 verification (algorithm 8) | ✅ Implemented | `DnssecValidator.verifyRsaSha256()` |
| 5.3.1 | ECDSA-P256-SHA256 verification (algorithm 13) | ✅ Implemented | `DnssecValidator.verifyEcdsaP256Sha256()` |
| 5.2 | DS digest verification | ✅ Implemented | `DnssecValidator.verifyDs()` (SHA-1, SHA-256) |
| 5.3 | Canonical RRset ordering for signature verification | ✅ Implemented | `DnssecValidator.buildSignedData()` sorts RRset |

### RFC 5155 — NSEC3

| Type | Value | Status | Verification |
|------|-------|--------|-------------|
| NSEC3 | 50 | ✅ Implemented | `Nsec3Record` (hashAlg, flags, iterations, salt, nextHash, types); `RecordTypeTest` |
| NSEC3PARAM | 51 | ✅ Implemented | `Nsec3ParamRecord` (hashAlg, flags, iterations, salt); `RecordTypeTest` |
| - | Type bit maps encoding/decoding | ✅ Implemented | `TypeBitMaps.encode()` / `decode()` |

### RFC 8484 — DNS-over-HTTPS

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 4.1 | HTTP POST with application/dns-message | ✅ Implemented | `DohTransport.sendPost()` |
| 4.1.1 | HTTP GET with base64url ?dns= parameter | ✅ Implemented | `DohTransport.sendGet()` |
| 4.2 | Accept: application/dns-message header | ✅ Implemented | Both POST and GET set Accept header |
| 4.2.1 | HTTP 200 response with binary DNS message body | ✅ Implemented | `DohTransport.executeRequest()` |

### RFC 7858 — DNS-over-TLS

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 3.1 | TLS connection on port 853 | ✅ Implemented | `DotTransport` default port 853 |
| 3.3 | 2-byte length prefix over TLS | ✅ Implemented | `DotTransport.send()` uses length-prefixed framing |
| 3.3 | Configurable SSLContext | ✅ Implemented | `DotTransport(Duration, SSLContext)` constructor |

### RFC 1034 — Authoritative Server Behavior

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 4.3.1 | Zone-based query resolution | ✅ Implemented | `DnsServer.handleWithZones()` walks parent domains |
| 4.3.2 | Authoritative answer (AA=1) | ✅ Implemented | `AuthoritativeZone.handleQuery()` sets AA flag |
| 4.3.2 | NXDOMAIN with SOA in authority | ✅ Implemented | `AuthoritativeZone.handleQuery()` |
| 4.3.2 | Wildcard matching (*.domain) | ✅ Implemented | `AuthoritativeZone.lookup()` tries wildcard on miss |
| 4.3.2 | CNAME following | ✅ Implemented | `AuthoritativeZone.lookup()` returns CNAME if no exact type match |
| 4.3.2 | NS records in authority section | ✅ Implemented | Returned for successful queries via `nsRecords()` |
| 4.3.4 | Iterative (non-recursive) resolution | ✅ Implemented | `RecursiveResolver` sends queries with RD=0 to each server |

### RFC 1035 — Resolver Behavior

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| 7.1 | Stub resolver (forward to upstream) | ✅ Implemented | `StubResolver` |
| 7.2 | Response caching based on TTL | ✅ Implemented | `DnsCache` with TTL-based expiry |
| 5.2.1 | UDP query with TCP fallback on truncation | ✅ Implemented | `DnsClient.query()` retries with `queryTcp()` on TC flag |

## Known Limitations

- ❌ No zone transfer (AXFR/IXFR, RFC 5936/1995)
- ❌ No dynamic updates (RFC 2136) — OpCode.UPDATE is defined but not processed
- ❌ No TSIG transaction signatures (RFC 8945)
- ❌ No DNS cookies (RFC 7873)
- ❌ No EDNS0 client subnet (RFC 7871)
- ❌ No DNS64 (RFC 6147)
- ❌ No DNSSEC chain validation (only individual RRSIG/DS verification; no full chain-of-trust walking)
- ❌ No negative caching (RFC 2308) — NXDOMAIN/NODATA responses are not cached
- ❌ No DNSSEC algorithms beyond 8 (RSA-SHA256) and 13 (ECDSA-P256-SHA256)
- ⚠️ Total domain name length (255 bytes) not enforced during parsing
- ⚠️ RecursiveResolver does not resolve NS names when glue records are absent and cache is empty
- ⚠️ Integration tests require network access for DoH/DoT (unit tests use loopback only)

## Test Coverage Summary

- Total tests: 25
- Key unit test classes: `DnsNameTest` (21 tests), `RecordTypeTest` (4 tests)
- Sections tested: Domain name parsing (case insensitivity, subdomain, wildcard, wire length, edge cases), record type value mapping (standard + extended + DNSSEC), round-trip enum coverage
- Key areas needing tests: DnsCodec encode/decode, DnsMessage builder, AuthoritativeZone query handling, ZoneFile parsing, DnsClient UDP/TCP, StubResolver caching, RecursiveResolver referral following, DNSSEC validation, transport layer operations

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Requirements](REQUIREMENTS.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-06
