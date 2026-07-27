
# Lego Flow LDAP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-90-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-blue.svg)]()

LDAP v3 protocol module for the Lego Flow framework, providing BER codec, client, and server with pluggable directory backend.

## Overview

This module implements LDAP v3 (RFC 4511) from scratch, enabling Java applications to build LDAP clients and servers for directory services. The architecture layers protocol handling on top of BER/ASN.1 encoding shared with the `network-common` module:

```
LDAP Client / Server (application layer)
  → Controls (paged results, sort)
    → Protocol (17 operation records, LdapMessage, LdapResult)
      → DN / Filter Parsing (RFC 4514, RFC 4515)
        → BER Codec (LdapCodec, LdapFilterCodec)
          → network-common (BER/ASN.1 primitives)
```

## Features

- **LDAP v3 (RFC 4511)** — full protocol implementation with all 17 operations
- **BER codec** — dual-mode: static one-shot encode/decode (thread-safe) and stream-oriented ByteBuffer accumulation (per-connection)
- **All 17 operations** — Bind, Unbind, Search, Modify, Add, Delete, ModifyDN, Compare, Abandon, Extended, plus response variants
- **DN parsing (RFC 4514)** — distinguished name parsing with proper escaping/unescaping
- **Search filters (RFC 4515)** — sealed hierarchy: and, or, not, equality, substring, comparison, present, approx, extensible match
- **Controls** — paged results and sort controls
- **Client** — bind, search, modify, add, delete, compare, extended operations with paged results support
- **Server** — virtual-thread-per-connection with pluggable `DirectoryBackend` interface
- **In-memory backend** — ConcurrentHashMap-based directory for testing and development
- **Sealed hierarchies** — `LdapProtocolOp` (17 records) and `SearchFilter` use sealed interfaces

## Quick Start

### Start a server with in-memory backend

```java
var backend = new InMemoryDirectoryBackend();
backend.addEntry("dc=example,dc=com", List.of(
    LdapAttribute.of("objectClass", "top", "domain"),
    LdapAttribute.of("dc", "example")
));
try (var server = LdapServer.start(389, backend)) {
    // server is accepting connections
}
```

### Connect a client, bind, and search

```java
try (var client = LdapClient.connect("localhost", 389)) {
    client.bind("cn=admin,dc=example,dc=com", "secret");
    var results = client.search("dc=example,dc=com",
            SearchScope.WHOLE_SUBTREE,
            SearchFilter.equalityMatch("objectClass", "person"));
    for (var entry : results) {
        System.out.println(entry.objectName());
    }
}
```

### Add and modify entries

```java
client.add("cn=alice,dc=example,dc=com", List.of(
    LdapAttribute.of("objectClass", "person"),
    LdapAttribute.of("cn", "alice"),
    LdapAttribute.of("sn", "Smith")
));

client.modify("cn=alice,dc=example,dc=com",
    ModifyRequest.replace("mail", "alice@example.com"));
```

### Search with filters

```java
var filter = SearchFilter.and(
    SearchFilter.equalityMatch("objectClass", "person"),
    SearchFilter.substring("cn", "ali", null, null)
);
var results = client.search("dc=example,dc=com",
        SearchScope.WHOLE_SUBTREE, filter);
```

## Package Structure

```
ssg.legoflow.network.ldap/
├── codec/             — BER encoder/decoder for LDAP messages, filter BER codec
├── protocol/          — All 17 operation records, LdapMessage, LdapResult, result codes, attributes
├── dn/                — Distinguished name parsing per RFC 4514
├── filter/            — Search filter sealed hierarchy and parser per RFC 4515
├── control/           — LDAP controls (paged results, sort)
├── client/            — LDAP client operations (bind, search, modify, add, delete, compare, extended)
└── server/            — LDAP server, directory backend interface, in-memory backend
```

## Dependencies

This module depends on:
- `network-common` — shared BER/ASN.1 codec (BerEncoder, BerDecoder, BerTag, BerLength)
- `slf4j-api` — logging

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
