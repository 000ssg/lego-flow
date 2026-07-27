# DNS Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 25
- **Dependencies**: blocks (DP/DF), service (TCP/UDP transport), http (DoH transport)
- **Standards**: RFC 1034, RFC 1035, RFC 4033-4035 (DNSSEC), RFC 8484 (DoH), RFC 7858 (DoT)

---

## Requirements

### Wire Format Codec
1. Encode and decode DNS messages per RFC 1035, Section 4
2. Parse 12-byte header: ID, flags (QR, OPCODE, AA, TC, RD, RA, AD, CD, RCODE), section counts
3. Encode/decode domain names with label-length format and compression pointers (0xC0 prefix)
4. Handle compression pointer cycles with a maximum jump limit (128)
5. Decode questions (QNAME + QTYPE + QCLASS) and resource records (NAME + TYPE + CLASS + TTL + RDLENGTH + RDATA)
6. Support all 19 implemented record types in RDATA encode/decode
7. Big-endian byte ordering for all multi-byte integers
8. Validate message structure and reject malformed input with DnsFormatException

### Domain Names
1. Parse dotted string representation with optional trailing dot (FQDN)
2. Case-insensitive comparison per RFC 4343 (preserve original case, compare lowercase)
3. Enforce 63-byte maximum label length
4. Support root domain (empty label list, string ".")
5. Provide parent(), isSubdomainOf(), matchesWildcard(), prepend() operations
6. Compute wire-format length for name compression and buffer sizing
7. Canonical lowercase form for DNSSEC ordering

### Record Types
1. Standard types (RFC 1035): A, NS, CNAME, SOA, PTR, MX, TXT
2. Extended types: AAAA (RFC 3596), SRV (RFC 2782), NAPTR (RFC 3403), CAA (RFC 8659)
3. EDNS0: OPT pseudo-record (RFC 6891) with UDP payload size, extended RCODE, DNSSEC OK flag
4. DNSSEC types (RFC 4034): DNSKEY, RRSIG, DS, NSEC
5. NSEC3 types (RFC 5155): NSEC3, NSEC3PARAM
6. Pseudo-type: ANY (255) for wildcard queries
7. Sealed RDATA interface for exhaustive pattern matching

### Response Codes and Operation Codes
1. Response codes: NOERROR, FORMERR, SERVFAIL, NXDOMAIN, NOTIMP, REFUSED, YXDOMAIN, YXRRSET, NXRRSET, NOTAUTH, NOTZONE
2. Operation codes: QUERY, IQUERY (obsolete), STATUS, NOTIFY, UPDATE
3. Record classes: IN, CH, HS, ANY

### Authoritative Server
1. Bind to both UDP and TCP on the same address
2. Use virtual threads for concurrent query handling
3. Support pluggable query handler via DnsHandler functional interface
4. Zone-based query routing: walk domain name parents to find matching zone
5. Authoritative zone with SOA record, indexed record storage by name and type
6. Record lookup: exact match, wildcard match (*.parent), CNAME chasing
7. Correct NXDOMAIN handling: return SOA in authority section
8. No-data response: name exists but no matching type, return SOA
9. Include NS records in authority section for successful queries
10. UDP truncation: set TC flag when response exceeds 512 bytes
11. TCP: 2-byte big-endian length prefix per RFC 1035, Section 4.2.2
12. Statistics: query count and response count tracking

### Zone File Parser
1. Parse BIND-format zone files per RFC 1035, Section 5
2. Handle $ORIGIN directive for setting default origin
3. Handle $TTL directive for setting default TTL
4. Support @ shorthand for current origin
5. Support multi-line records with parentheses
6. Parse time suffixes (w, d, h, m, s) in TTL values
7. Support record types: A, AAAA, NS, CNAME, MX, SOA, TXT, SRV, PTR, CAA
8. Handle inline comments (semicolon)
9. Implicit name inheritance from previous record

### Zone Management
1. Programmatic zone creation with convenience methods: addA, addAAAA, addNS, addCNAME, addMX, addTXT, addSRV
2. Validate that records belong to the zone (subdomain check)
3. Thread-safe concurrent access using ConcurrentHashMap
4. Query handling directly on zone (handleQuery method)

### DNS Client
1. Send queries over UDP, fall back to TCP if response is truncated (TC flag)
2. Direct TCP query method for large responses
3. Configurable server address and query timeout
4. Async query via CompletableFuture

### High-Level Lookup
1. Resolve hostname to IPv4 addresses (A records)
2. Resolve hostname to IPv6 addresses (AAAA records)
3. Resolve hostname to all addresses (A + AAAA)
4. Look up MX records sorted by preference
5. Look up SRV records sorted by priority and weight
6. Look up TXT records
7. Reverse DNS lookup (PTR) for both IPv4 (in-addr.arpa) and IPv6 (ip6.arpa)

### Stub Resolver
1. Forward queries to configurable upstream DNS server
2. TTL-based response caching
3. Cache-first lookup: return cached records if available and not expired
4. Cache all sections of responses (answer, authority, additional)

### Recursive Resolver
1. Iterative resolution starting from root hints (13 root servers)
2. Follow NS referrals down the delegation chain
3. Extract glue records from additional section for referral addresses
4. Fall back to cache for NS name resolution when glue is missing
5. Maximum recursion depth (20) to prevent infinite referral loops
6. Configurable root servers and timeout per hop
7. Return SERVFAIL on resolution failure

### DNS Cache
1. TTL-based expiry: compute absolute expiry time from TTL at insertion
2. Automatic TTL adjustment on read: return remaining seconds
3. Lazy eviction on get(): remove expired entries during lookup
4. Explicit eviction via evictExpired()
5. Thread-safe concurrent access via ConcurrentHashMap
6. Configurable maximum entries (default 10,000)
7. Skip caching OPT pseudo-records and zero-TTL records

### Transport — UDP
1. Single datagram send/receive via DatagramChannel
2. Configurable receive timeout via SO_TIMEOUT

### Transport — TCP
1. 2-byte big-endian length prefix for framing
2. Configurable connect and read timeout
3. Full message read with truncation detection

### Transport — DNS-over-HTTPS (RFC 8484)
1. HTTP POST with Content-Type: application/dns-message
2. HTTP GET with base64url-encoded query in ?dns= parameter
3. Configurable DoH server URI
4. Custom HTTP client support

### Transport — DNS-over-TLS (RFC 7858)
1. TLS connection on port 853 (configurable)
2. Same 2-byte length prefix framing as TCP
3. Configurable SSLContext for custom trust stores
4. TLS handshake before DNS exchange

### DNSSEC Validation
1. RRSIG signature verification: check temporal validity, key tag, algorithm
2. Build canonical signed data: RRSIG fields + sorted canonical RRset
3. RSA-SHA256 (algorithm 8): parse exponent/modulus from DNSKEY, verify with JCA
4. ECDSA-P256-SHA256 (algorithm 13): parse X/Y coordinates, convert r||s to DER, verify with JCA
5. DS record validation: compute digest of wire-format owner name + DNSKEY RDATA
6. Support DS digest types: SHA-1 (type 1) and SHA-256 (type 2)
7. Type bit maps encoding/decoding for NSEC/NSEC3 records

### Demo Applications
1. DnsServerDemo: authoritative server with example.com zone, client lookups for A, MX, TXT
2. ZoneManagementDemo: BIND zone file parsing, zone queries, NXDOMAIN handling

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-06
