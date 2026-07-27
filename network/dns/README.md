
# Lego Flow DNS Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-25-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-blue.svg)]()

DNS protocol module for the Lego Flow framework, providing a complete Domain Name System implementation with authoritative server, resolvers, and client.

## Overview

This module implements DNS per RFC 1034/1035 with extensions for DNSSEC (RFC 4033-4035), DNS-over-HTTPS (RFC 8484), and DNS-over-TLS (RFC 7858). The architecture layers wire-format codec, transport, resolution, and server components:

```
DNS Client / Lookup (high-level resolution API)
  -> Resolvers (stub forwarding, recursive iterative)
    -> Cache (TTL-based, thread-safe)
      -> Transport (UDP, TCP, DoH, DoT)
        -> Codec (binary encode/decode, name compression)
          -> RDATA (sealed hierarchy, 19 record types)

DNS Server (authoritative query handling)
  -> Zones (SOA, record storage, wildcard, CNAME)
    -> Zone File Parser (BIND format)
      -> Codec + RDATA
```

## Features

- **RFC 1034/1035** -- complete wire format codec with name compression (0xC0 pointers)
- **19 record types** -- A, AAAA, NS, CNAME, SOA, PTR, MX, TXT, SRV, NAPTR, CAA, OPT (EDNS0), DS, RRSIG, NSEC, DNSKEY, NSEC3, NSEC3PARAM, ANY
- **Authoritative server** -- UDP+TCP dual transport, virtual threads, zone-based query routing
- **Zone management** -- programmatic zone creation, BIND-format zone file parser ($ORIGIN, $TTL, multi-line)
- **Stub resolver** -- forwarding queries to upstream with TTL-based caching
- **Recursive resolver** -- iterative resolution from root hints, follows NS referrals, glue record extraction
- **DNS client** -- UDP-first with TCP fallback on truncation, sync + async (CompletableFuture)
- **High-level lookup** -- resolveA, resolveAAAA, lookupMx, lookupSrv, lookupTxt, reverseLookup (PTR)
- **DNS-over-HTTPS** (RFC 8484) -- POST (binary) and GET (base64url) methods via java.net.http
- **DNS-over-TLS** (RFC 7858) -- TLS on port 853 with configurable SSLContext
- **DNSSEC validation** -- RRSIG signature verification (RSA-SHA256, ECDSA-P256-SHA256), DS digest validation
- **EDNS0** -- OPT pseudo-record with DNSSEC OK flag, extended RCODE, UDP payload size

## Quick Start

### Start an authoritative DNS server

```java
AuthoritativeZone zone = AuthoritativeZone.create(
    "example.com", "ns1.example.com", "admin.example.com",
    2024010101L, 3600, 900, 604800, 86400);

zone.addA("example.com", 300, "93.184.216.34");
zone.addA("www.example.com", 300, "93.184.216.34");
zone.addMX("example.com", 300, 10, "mail.example.com");
zone.addTXT("example.com", 300, "v=spf1 include:_spf.example.com ~all");

var server = new DnsServer(new InetSocketAddress("127.0.0.1", 5353));
server.addZone(zone);
server.start();
```

### Query with the DNS client

```java
var client = new DnsClient("127.0.0.1", Duration.ofSeconds(5));
DnsMessage response = client.query("example.com", RecordType.A);
System.out.println(response.answers());
```

### High-level lookups

```java
var lookup = new DnsLookup(new InetSocketAddress("127.0.0.1", 5353), Duration.ofSeconds(5));

List<Inet4Address> ips = lookup.resolveA("example.com");
List<MxRecord> mxRecords = lookup.lookupMx("example.com");
List<String> txtRecords = lookup.lookupTxt("example.com");
List<SrvRecord> srvRecords = lookup.lookupSrv("_sip._tcp.example.com");
```

### Parse a BIND zone file

```java
String zoneFile = """
    $ORIGIN example.org.
    $TTL 3600
    @   IN  SOA ns1.example.org. admin.example.org. (
                2024010101 3600 900 604800 86400 )
    @   IN  NS  ns1.example.org.
    @   IN  A   203.0.113.1
    www IN  A   203.0.113.2
    """;
AuthoritativeZone zone = ZoneFile.parse(zoneFile);
```

### DNS-over-HTTPS query

```java
var doh = new DohTransport(URI.create("https://dns.google/dns-query"), Duration.ofSeconds(5));
DnsMessage response = doh.send(DnsMessage.query("example.com", RecordType.A));
```

## Package Structure

```
ssg.legoflow.network.dns/
├── protocol/          -- Wire format: DnsMessage, DnsHeader, DnsName, DnsCodec, DnsRecord,
|                         DnsQuestion, RecordType, RecordClass, OpCode, ResponseCode
├── rdata/             -- Typed RDATA: sealed RData interface + 19 record type implementations
│   └── dnssec/        -- DNSSEC utilities: DnssecValidator, TypeBitMaps
├── server/            -- DNS server: DnsServer (UDP+TCP), DnsHandler, AuthoritativeZone, ZoneFile
├── resolver/          -- Resolvers: DnsResolver interface, StubResolver, RecursiveResolver, DnsCache
├── client/            -- Client: DnsClient (UDP+TCP, sync+async), DnsLookup (high-level convenience)
├── transport/         -- Transports: UdpTransport, TcpTransport, DohTransport, DotTransport
└── demo/              -- Demo applications: DnsServerDemo, ZoneManagementDemo
```

## Demo Applications

1. **DnsServerDemo** -- Creates an authoritative server with example.com zone, adds various record types (A, AAAA, NS, MX, TXT, SRV, CNAME), and resolves queries against it
2. **ZoneManagementDemo** -- Parses a BIND-format zone file, queries the zone for A, MX, and nonexistent records (NXDOMAIN)

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- TCP transport, lifecycle management, virtual threads
- `lego-flow-http` -- HTTP client (used by DoH transport)

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
