
# Lego Flow MySQL Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-204-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

MySQL wire protocol module for the Lego Flow framework, providing both client and server implementations of the MySQL client/server protocol.

## Overview

This module implements the MySQL client/server wire protocol, enabling Java applications to build MySQL-compatible servers and clients. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
MySQL Client / Server (application layer)
  -> Authentication (mysql_native_password, caching_sha2_password)
    -> Command Processing (COM_QUERY, COM_STMT_PREPARE, COM_STMT_EXECUTE, ...)
      -> Result Set Writer (text + binary protocol)
        -> Packet Codec (framing, length-encoded types, capability negotiation)
          -> TCP Transport (service module, virtual threads)
```

## Features

- **MySQL Wire Protocol** — full implementation of the MySQL client/server protocol
- **Server** — in-memory MySQL server with virtual threads, handshake, authentication, SQL execution
- **Client** — high-level client API with connect, query, prepared statements, ping, statistics
- **Packet Framing** — 4-byte header with automatic multi-packet splitting for payloads > 16 MB
- **HandshakeV10** — server greeting with capability negotiation and auth plugin selection
- **Authentication** — pluggable auth plugins: mysql_native_password (SHA1) and caching_sha2_password (SHA256)
- **Auth Switch** — server-initiated authentication plugin switching
- **Text Protocol** — COM_QUERY with full result set (column definitions + text rows)
- **Binary Protocol** — COM_STMT_PREPARE / COM_STMT_EXECUTE with typed parameter binding
- **Prepared Statements** — server-side prepare, execute, close, reset, and long data transfer
- **SQL Engine** — clause-based executor supporting CREATE TABLE, INSERT, SELECT (with JOIN, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT), UPDATE, DELETE, DROP TABLE, SHOW TABLES/DATABASES
- **JOIN Queries** — INNER JOIN and LEFT JOIN with table aliases, chained multi-table JOINs
- **ORDER BY / LIMIT** — multi-column sort (ASC/DESC), numeric-aware comparison, LIMIT with OFFSET
- **GROUP BY / Aggregates** — COUNT(*), COUNT(col), SUM, AVG, MIN, MAX; GROUP BY with HAVING clause
- **Advanced WHERE** — AND/OR, comparison operators (=, !=, <, >, <=, >=), LIKE (% and _ wildcards), IS NULL/IS NOT NULL, IN (value list)
- **Transaction Rollback** — BEGIN snapshots table state, COMMIT discards snapshot, ROLLBACK restores from snapshot; per-session state
- **Result Sets** — text and binary protocol encoding/decoding with NULL bitmap
- **Column Types** — 25 MySQL column types with numeric/string/blob/temporal classification
- **Capability Flags** — full 32-bit capability negotiation (CLIENT_PROTOCOL_41, CLIENT_DEPRECATE_EOF, CLIENT_SESSION_TRACK, etc.)
- **Connection Attributes** — client metadata sent during handshake (client name, OS, PID)
- **Error Handling** — structured ERR packets with MySQL error codes and SQLSTATE values
- **Character Sets** — charset/collation ID mapping (utf8mb4, latin1, binary, etc.)

## Quick Start

### Start a MySQL server

```java
var server = new MysqlServer("localhost", 3306);
server.createDatabase("testdb");
server.addUser("root", "password123");
server.start();
```

### Connect a client and query

```java
try (var client = MysqlClient.connect("localhost", 3306, "root", "password123", "testdb")) {
    // Execute a query
    var result = client.query("SELECT * FROM users");
    while (result.next()) {
        System.out.println(result.getString("name"));
    }

    // Execute an update
    client.execute("INSERT INTO users (name, age) VALUES ('Alice', 30)");
}
```

### Use prepared statements

```java
try (var client = MysqlClient.connect("localhost", 3306, "root", "pass", "testdb")) {
    try (var ps = client.prepare("INSERT INTO users (name, age) VALUES (?, ?)")) {
        ps.setString(0, "Bob");
        ps.setInt(1, 25);
        ps.executeUpdate();
    }

    try (var ps = client.prepare("SELECT * FROM users WHERE name = ?")) {
        ps.setString(0, "Bob");
        var result = ps.executeQuery();
        while (result.next()) {
            System.out.println(result.getString("name") + ": " + result.getInt("age"));
        }
    }
}
```

### Ping and statistics

```java
try (var client = MysqlClient.connect("localhost", 3306, "root", "pass", null)) {
    boolean alive = client.ping();
    String stats = client.statistics();
    client.useDatabase("testdb");
    client.resetConnection();
}
```

## Package Structure

```
ssg.legoflow.database.mysql/
├── protocol/          — Wire protocol: MysqlPacket framing, MysqlCodec, HandshakeV10,
│                        OkPacket, ErrPacket, EofPacket, LengthEncodedInt/String,
│                        CapabilityFlags, StatusFlags, ColumnType
├── server/            — Server: MysqlServer, ClientSession, QueryExecutor,
│                        InMemoryDatabase, ResultSetWriter, PreparedStatement, ColumnDefinition
├── client/            — Client: MysqlClient, MysqlConnection, MysqlStatement,
│                        MysqlPreparedStatement, MysqlResult, ConnectionAttributes
├── auth/              — Authentication: AuthPlugin, MysqlNativePassword, CachingSha2Password,
│                        AuthSwitchRequest
└── common/            — Shared: Charset, MysqlError
```

## Dependencies

This module depends on:
- `lego-flow-blocks` — DP/DF data processing primitives
- `lego-flow-service` — TCP transport, lifecycle management, virtual threads

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
