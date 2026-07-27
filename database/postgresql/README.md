
# Lego Flow PostgreSQL Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-300-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-blue.svg)]()

PostgreSQL wire protocol module for the Lego Flow framework, providing client and server implementations of the PostgreSQL v3 frontend/backend protocol.

## Overview

This module implements the PostgreSQL v3 wire protocol from scratch, enabling Java applications to communicate with PostgreSQL-compatible servers or to build custom PostgreSQL-compatible servers. The implementation covers the full connection lifecycle including authentication, simple and extended query protocols, COPY bulk data transfer, and LISTEN/NOTIFY asynchronous notifications.

```
PgClient / PgServer (application layer)
  -> Authentication (cleartext, MD5, SCRAM-SHA-256)
    -> Query Execution (simple + extended query protocols)
      -> COPY Protocol (bulk import/export)
        -> LISTEN/NOTIFY (asynchronous notifications)
          -> PgCodec (binary encode/decode for all message types)
            -> TCP Transport (java.net.Socket / virtual threads)
```

## Features

- **PostgreSQL v3 Wire Protocol** — complete frontend/backend message encoding and decoding
- **Simple Query Protocol** — single-step SQL query execution with `Query` ('Q') messages
- **Extended Query Protocol** — prepared statements with Parse/Bind/Describe/Execute/Sync cycle
- **Three Authentication Methods** — cleartext password, MD5 hash, SCRAM-SHA-256 (RFC 5802)
- **COPY Protocol** — bulk data import (COPY FROM STDIN) and export (COPY TO STDOUT)
- **LISTEN/NOTIFY** — asynchronous pub/sub notifications between clients
- **Transaction Support** — BEGIN/COMMIT/ROLLBACK with proper transaction status tracking (I/T/E)
- **22 PostgreSQL Type OIDs** — bool, int2/4/8, float4/8, numeric, varchar, text, bytea, date, time, timestamp, timestamptz, interval, uuid, json, jsonb, xml, oid, void, unknown
- **30 SQLSTATE Error Codes** — standard SQL and PostgreSQL error codes
- **Aggregate Functions** — COUNT, SUM, AVG, MIN, MAX with GROUP BY and HAVING support
- **JOIN Support** — INNER JOIN and LEFT JOIN with table aliases and qualified column references
- **Server** — virtual thread-based server with pluggable authentication and in-memory database
- **Client** — connection management, query execution, prepared statements, COPY streams
- **Sealed Message Hierarchy** — type-safe `PgMessage` -> `FrontendMessage` / `BackendMessage` with Java records

## Quick Start

### Start a server

```java
var server = new PgServer();
server.start(5432);
```

### Start a server with authentication

```java
var auth = new ScramSha256().addUser("myuser", "mypassword");
var server = new PgServer(auth);
server.start(5432);
```

### Connect and query

```java
try (PgClient client = PgClient.connect("127.0.0.1", 5432, "mydb", "myuser", "mypassword")) {
    // Simple query
    PgResult result = client.query("SELECT * FROM users WHERE active = true");
    for (int i = 0; i < result.rowCount(); i++) {
        System.out.println(result.getString(i, "name") + ": " + result.getString(i, "email"));
    }
}
```

### Prepared statements (Extended Query Protocol)

```java
try (PgClient client = PgClient.connect("127.0.0.1", 5432, "mydb", "myuser", null)) {
    try (PgStatement stmt = client.prepare("SELECT * FROM users WHERE id = $1")) {
        PgResult result = stmt.execute("42");
        System.out.println(result.getString(0, "name"));
    }
}
```

### COPY bulk import

```java
try (PgClient client = PgClient.connect("127.0.0.1", 5432, "mydb", "myuser", null)) {
    List<String> rows = List.of(
        "1\tAlice\talice@example.com\n",
        "2\tBob\tbob@example.com\n"
    );
    client.copyIn("COPY users FROM STDIN", rows);
}
```

### LISTEN/NOTIFY

```java
try (PgClient client = PgClient.connect("127.0.0.1", 5432, "mydb", "myuser", null)) {
    client.listen("events", notification ->
        System.out.println("Event: " + notification.payload()));
    // From another client:
    client.notify("events", "something happened");
}
```

## Package Structure

```
ssg.legoflow.database.postgresql/
├── auth/              — Authentication: PgAuthenticator, CleartextAuth, Md5Auth, ScramSha256
├── client/            — Client: PgClient, PgConnection, PgStatement, PgResult, PgCopyStream
├── common/            — Shared types: PgSeverity, SqlState
├── protocol/          — Wire protocol: PgMessage, FrontendMessage, BackendMessage, PgCodec, PgType, TransactionStatus
├── server/            — Server: PgServer, ClientSession, QueryExecutor, InMemoryDatabase, NotificationManager
└── demo/              — Demo application: PostgreSqlDemo
```

## Demo Application

**PostgreSqlDemo** — demonstrates the full lifecycle of a PostgreSQL wire protocol session:
1. Start an embedded server on an ephemeral port
2. Simple query: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE
3. Extended query: prepared statements with parameter binding
4. Clean shutdown with Terminate message

## Dependencies

This module has no external PostgreSQL driver dependencies. It implements the wire protocol from scratch using only:
- `java.net.Socket` for TCP transport
- `java.security` / `javax.crypto` for authentication (MD5, HMAC-SHA-256, PBKDF2)
- `org.slf4j` for logging

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
