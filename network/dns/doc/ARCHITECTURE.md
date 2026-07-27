# DNS Module — Architecture

This document describes the architectural decisions for the DNS module.

---

## Protocol Overview

DNS (Domain Name System) is the hierarchical naming system that maps domain names to IP addresses and other resource records. The Lego Flow implementation covers RFC 1034/1035 (core protocol), RFC 4033-4035 (DNSSEC), RFC 8484 (DNS-over-HTTPS), and RFC 7858 (DNS-over-TLS).

## Layered Architecture

```mermaid
graph TD
    L1["Client / Lookup / Server<br/>(high-level API, zone management, query routing)"]
    L2["Resolvers<br/>(StubResolver: forwarding + caching<br/>RecursiveResolver: iterative from root hints)"]
    L3["Cache<br/>(TTL-based expiry, ConcurrentHashMap,<br/>automatic TTL adjustment on read)"]
    L4["Transport<br/>(UDP, TCP with 2-byte prefix,<br/>DoH via HTTP POST/GET, DoT via TLS on 853)"]
    L5["Codec<br/>(DnsCodec: binary encode/decode,<br/>name compression with 0xC0 pointers)"]
    L6["RDATA<br/>(sealed RData interface, 19 record types,<br/>exhaustive pattern matching in codec)"]
    L7["service module (TCP/UDP)<br/>(virtual threads, channel management)"]
    L8["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6
    L4 --> L7 --> L8
```

## DNS Message Structure

Every DNS message follows the same binary format (RFC 1035, Section 4):

```mermaid
graph TD
    MSG["DNS Message"] --> HDR["Header (12 bytes)<br/>ID, Flags, Section Counts"]
    MSG --> QD["Question Section<br/>(QNAME + QTYPE + QCLASS)"]
    MSG --> AN["Answer Section<br/>(Resource Records)"]
    MSG --> NS["Authority Section<br/>(NS referrals, SOA for NXDOMAIN)"]
    MSG --> AR["Additional Section<br/>(Glue records, OPT pseudo-record)"]
```

### Header Flags Layout

The `DnsHeader` record models the 12-byte header with individual flag fields:

| Bit(s) | Field | Description |
|--------|-------|-------------|
| 0 | QR | Query (0) or Response (1) |
| 1-4 | OPCODE | QUERY, IQUERY, STATUS, NOTIFY, UPDATE |
| 5 | AA | Authoritative Answer |
| 6 | TC | Truncation (response exceeded 512 bytes UDP) |
| 7 | RD | Recursion Desired |
| 8 | RA | Recursion Available |
| 9 | Z | Reserved |
| 10 | AD | Authenticated Data (DNSSEC) |
| 11 | CD | Checking Disabled (DNSSEC) |
| 12-15 | RCODE | NOERROR, FORMERR, SERVFAIL, NXDOMAIN, NOTIMP, REFUSED |

## Name Compression

DNS names use label-length encoding with optional compression pointers. The `DnsCodec` handles both:

```mermaid
sequenceDiagram
    participant Encoder
    participant CompressionMap
    participant Wire

    Note over Encoder: Encoding "www.example.com"
    Encoder->>CompressionMap: Check "www.example.com" at offset 12
    CompressionMap-->>Encoder: Not found
    Encoder->>Wire: Write label "www" (3 bytes + length)
    Encoder->>CompressionMap: Store "www.example.com" -> offset 12
    Encoder->>CompressionMap: Check "example.com"
    CompressionMap-->>Encoder: Not found
    Encoder->>Wire: Write label "example" (7 bytes + length)
    Encoder->>CompressionMap: Store "example.com" -> offset 16
    Encoder->>Wire: Write label "com" (3 bytes + length)
    Encoder->>Wire: Write terminating zero

    Note over Encoder: Encoding "mail.example.com"
    Encoder->>CompressionMap: Check "mail.example.com"
    CompressionMap-->>Encoder: Not found
    Encoder->>Wire: Write label "mail" (4 bytes + length)
    Encoder->>CompressionMap: Check "example.com"
    CompressionMap-->>Encoder: Found at offset 16
    Encoder->>Wire: Write pointer 0xC010
```

Decoding handles pointer cycles with a 128-jump safety limit to prevent infinite loops.

## RDATA Sealed Hierarchy

The `RData` sealed interface enables exhaustive pattern matching in the codec:

```mermaid
graph TD
    RData["sealed interface RData"]
    RData --> A["ARecord<br/>(Inet4Address)"]
    RData --> AAAA["AaaaRecord<br/>(Inet6Address)"]
    RData --> NS["NsRecord<br/>(DnsName)"]
    RData --> CNAME["CnameRecord<br/>(DnsName)"]
    RData --> PTR["PtrRecord<br/>(DnsName)"]
    RData --> MX["MxRecord<br/>(preference, exchange)"]
    RData --> SOA["SoaRecord<br/>(mname, rname, serial,<br/>refresh, retry, expire, min)"]
    RData --> TXT["TxtRecord<br/>(List of strings)"]
    RData --> SRV["SrvRecord<br/>(priority, weight, port, target)"]
    RData --> MORE["NaptrRecord, CaaRecord,<br/>OptRecord, RawRData"]
    RData --> DNSSEC["DnskeyRecord, RrsigRecord,<br/>DsRecord, NsecRecord,<br/>Nsec3Record, Nsec3ParamRecord"]
```

Adding a new record type requires updating: (1) `RData` permits clause, (2) `RecordType` enum, (3) `DnsCodec` encode/decode switch expressions.

## Server Architecture

```mermaid
graph TD
    UDP["UDP Listener<br/>(DatagramChannel)"] --> VT["Virtual Thread Pool<br/>(Executors.newVirtualThreadPerTaskExecutor)"]
    TCP["TCP Listener<br/>(ServerSocket)"] --> VT
    VT --> DECODE["DnsCodec.decode()"]
    DECODE --> HANDLER["DnsHandler<br/>(functional interface)"]
    HANDLER --> ZONES["Zone Lookup<br/>(walk domain parents<br/>to find matching zone)"]
    ZONES --> AZ["AuthoritativeZone<br/>.handleQuery()"]
    AZ --> LOOKUP["Record Lookup<br/>(exact -> wildcard -> CNAME)"]
    LOOKUP -->|"Found"| NOERROR["NOERROR + answers<br/>+ NS authority"]
    LOOKUP -->|"Name exists, no type"| NODATA["NOERROR + SOA authority"]
    LOOKUP -->|"Name not found"| NXDOMAIN["NXDOMAIN + SOA authority"]
    AZ --> ENCODE["DnsCodec.encode()"]
    ENCODE --> UDP
    ENCODE --> TCP

    Note1["UDP: truncate to 512 bytes,<br/>set TC flag if exceeded"]
    Note2["TCP: 2-byte length prefix"]
```

- The server binds to both UDP and TCP on the same address
- Each incoming query is dispatched to a virtual thread
- UDP responses exceeding 512 bytes are truncated with the TC flag set
- TCP uses the standard 2-byte big-endian length prefix

## Resolver Architecture

### Stub Resolver

```mermaid
sequenceDiagram
    participant App
    participant Stub as StubResolver
    participant Cache as DnsCache
    participant Upstream as Upstream Server

    App->>Stub: resolve("example.com", A)
    Stub->>Cache: get(example.com, A)
    alt Cache hit
        Cache-->>Stub: Records (TTL adjusted)
        Stub-->>App: Response from cache
    else Cache miss
        Stub->>Upstream: Forward query (UDP/TCP)
        Upstream-->>Stub: Response
        Stub->>Cache: put(response)
        Stub-->>App: Response
    end
```

### Recursive Resolver

```mermaid
sequenceDiagram
    participant App
    participant Resolver as RecursiveResolver
    participant Cache as DnsCache
    participant Root as Root Server
    participant TLD as TLD Server
    participant Auth as Authoritative Server

    App->>Resolver: resolve("www.example.com", A)
    Resolver->>Cache: Check cache
    Cache-->>Resolver: Miss

    Resolver->>Root: Query www.example.com A (RD=0)
    Root-->>Resolver: Referral: NS for .com + glue A records

    Resolver->>TLD: Query www.example.com A (RD=0)
    TLD-->>Resolver: Referral: NS for example.com + glue

    Resolver->>Auth: Query www.example.com A (RD=0)
    Auth-->>Resolver: Authoritative answer: 93.184.216.34

    Resolver->>Cache: Cache all sections
    Resolver-->>App: Answer: 93.184.216.34
```

- Maximum recursion depth: 20 hops (prevents infinite referral loops)
- Tries glue records in additional section first; falls back to cache for NS resolution
- Includes all 13 root server addresses (a.root-servers.net through m.root-servers.net)

## Transport Architecture

```mermaid
graph LR
    CLIENT["DnsClient"] -->|"Primary"| UDP["UdpTransport<br/>(single datagram,<br/>512 byte limit)"]
    CLIENT -->|"Fallback on TC"| TCP["TcpTransport<br/>(2-byte length prefix,<br/>up to 65535 bytes)"]

    DOH["DohTransport<br/>(RFC 8484)"] -->|"POST"| HTTPS_POST["HTTP POST<br/>Content-Type: application/dns-message"]
    DOH -->|"GET"| HTTPS_GET["HTTP GET<br/>?dns=base64url"]

    DOT["DotTransport<br/>(RFC 7858)"] --> TLS["TLS on port 853<br/>(SSLSocket + 2-byte prefix)"]
```

## Cache Design

- **Key**: `(DnsName, RecordType)` pair
- **Entry**: `DnsRecord` + `Instant` expiry (computed from TTL at insert time)
- **Thread safety**: `ConcurrentHashMap` for the top-level map; `synchronized` on per-key entry lists for expiry cleanup
- **TTL adjustment**: on read, remaining TTL is recalculated from expiry time
- **Eviction**: `evictExpired()` scans all entries; also lazy eviction on `get()` removes expired entries
- **Capacity**: default 10,000 entries

## DNSSEC Validation

```mermaid
graph TD
    VERIFY["DnssecValidator.verify()"]
    VERIFY --> TIME["Check temporal validity<br/>(inception <= now <= expiration)"]
    VERIFY --> KEYTAG["Check key tag matches"]
    VERIFY --> ALG["Check algorithm matches"]
    VERIFY --> BUILD["Build signed data:<br/>RRSIG fields + canonical RRset"]
    BUILD --> CRYPTO{"Algorithm?"}
    CRYPTO -->|"8"| RSA["RSA-SHA256<br/>(parse exponent+modulus from DNSKEY)"]
    CRYPTO -->|"13"| ECDSA["ECDSA-P256-SHA256<br/>(parse X+Y from DNSKEY,<br/>convert r||s to DER)"]

    DS_VERIFY["DnssecValidator.verifyDs()"]
    DS_VERIFY --> DS_TAG["Check key tag + algorithm"]
    DS_VERIFY --> DS_DIGEST["Compute digest:<br/>wire-format owner name + DNSKEY RDATA"]
    DS_DIGEST --> DS_SHA{"Digest type?"}
    DS_SHA -->|"1"| SHA1["SHA-1"]
    DS_SHA -->|"2"| SHA256["SHA-256"]
```

## Integration with Lego Flow

| Lego Flow Module | Usage in DNS |
|------------------|-------------|
| `blocks` | DP<I,O> for packet processing pipeline, Statistics for metrics |
| `service` | Virtual thread pools for concurrent query handling, channel management |
| `http` | HttpClient used by DoH transport for HTTPS requests |

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
