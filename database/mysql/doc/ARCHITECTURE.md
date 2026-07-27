# MySQL Module — Architecture

This document describes the architectural decisions for the MySQL module.

---

## Protocol Overview

The MySQL client/server protocol is a binary protocol over TCP used for communication between MySQL clients and servers. It handles connection setup (handshake + authentication), command execution (queries, prepared statements), and result set transmission. The Lego Flow implementation provides both server and client sides of this protocol.

## Layered Architecture

```mermaid
graph TD
    L1["Server / Client<br/>(connection management, API surface)"]
    L2["Authentication<br/>(mysql_native_password, caching_sha2_password,<br/>auth switch, auth more data)"]
    L3["Command Processing<br/>(COM_QUERY, COM_STMT_PREPARE/EXECUTE/CLOSE,<br/>COM_PING, COM_INIT_DB, COM_STATISTICS, ...)"]
    L4["Result Set Writer<br/>(text protocol rows, binary protocol rows,<br/>column definitions, NULL bitmaps)"]
    L5["Packet Codec<br/>(MysqlPacket framing, MysqlCodec commands,<br/>HandshakeV10, Ok/Err/Eof packets,<br/>length-encoded int/string)"]
    L6["service module (TCP)<br/>(ServerSocket, virtual threads)"]
    L7["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7
```

## Connection Lifecycle

### Handshake Sequence

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server

    S->>C: HandshakeV10 (protocol=10, server version,<br/>connection ID, scramble, capabilities, auth plugin)
    C->>S: HandshakeResponse41 (capabilities, username,<br/>auth response, database, auth plugin, attributes)
    alt Auth Switch Required
        S->>C: AuthSwitchRequest (new plugin name, new scramble)
        C->>S: Auth Response (re-computed with new plugin)
    end
    alt caching_sha2_password Fast Auth
        S->>C: AuthMoreData (0x01 + 0x03 fast auth success)
    end
    S->>C: OK_Packet (authentication success)
    Note over C,S: Command Phase begins
```

### Command Phase

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server

    C->>S: COM_QUERY ("SELECT * FROM users")
    S->>C: Column Count
    S->>C: Column Definition (per column)
    S->>C: EOF / OK (column end marker)
    S->>C: Row Data (text-encoded values)
    S->>C: EOF / OK (result set end)

    C->>S: COM_PING
    S->>C: OK_Packet

    C->>S: COM_QUIT
    Note over C,S: Connection closed
```

## Packet Framing

Every MySQL packet has a 4-byte header followed by the payload:

```mermaid
graph LR
    H["Header (4 bytes)"] --> P["Payload (0 to 16MB)"]
    H --> L["Length: 3 bytes LE"]
    H --> S["Sequence ID: 1 byte"]
```

- Maximum payload per packet: 16,777,215 bytes (2^24 - 1)
- Payloads exceeding the maximum are automatically split into multiple packets with incrementing sequence IDs
- A payload of exactly the maximum size is followed by an empty terminator packet
- Multi-packet reads reassemble the full payload transparently via `MysqlPacket.readFullFrom()`

## Server Architecture

```mermaid
graph TD
    TCP["TCP ServerSocket<br/>(virtual threads)"] --> CS["ClientSession<br/>(per-connection)"]
    CS --> HS["Handshake<br/>(HandshakeV10)"]
    CS --> AUTH["Authentication<br/>(AuthPlugin registry)"]
    CS --> CMD["Command Dispatch"]
    CMD --> QE["QueryExecutor<br/>(regex-based SQL)"]
    CMD --> PS["PreparedStatement<br/>(parameter substitution)"]
    CMD --> MISC["Ping / InitDB /<br/>Statistics / FieldList /<br/>SetOption / ResetConnection"]
    QE --> DB["InMemoryDatabase<br/>(ConcurrentHashMap tables)"]
    QE --> RSW["ResultSetWriter<br/>(text + binary protocol)"]
    PS --> QE
```

- **MysqlServer**: binds a TCP ServerSocket, creates a virtual thread per connection via `Executors.newVirtualThreadPerTaskExecutor()`
- **ClientSession**: runs in its own virtual thread, handles handshake, authentication, and command processing loop
- **QueryExecutor**: clause-based SQL parser supporting CREATE TABLE, INSERT, SELECT (with JOIN, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT, aggregates), UPDATE, DELETE, DROP TABLE, SHOW TABLES/DATABASES, SELECT VERSION()/DATABASE()
- **InMemoryDatabase**: thread-safe storage with `ConcurrentHashMap` for tables and `CopyOnWriteArrayList` for rows
- **ResultSetWriter**: encodes text and binary result sets with proper column definitions, NULL bitmaps, and EOF/OK terminators
- **PreparedStatement**: server-side state holding the SQL template, parameter count, long data buffers, and parameter substitution logic

## Client Architecture

```mermaid
graph TD
    MC["MysqlClient<br/>(high-level API)"] --> CONN["MysqlConnection<br/>(handshake, auth, packet I/O)"]
    MC --> STMT["MysqlStatement<br/>(COM_QUERY, text results)"]
    MC --> PS["MysqlPreparedStatement<br/>(COM_STMT_PREPARE/EXECUTE,<br/>binary results)"]
    MC --> CMDS["Commands<br/>(ping, useDatabase, statistics,<br/>resetConnection, setMultiStatements)"]
    CONN --> PKT["MysqlPacket<br/>(framing, send/receive)"]
    PS --> PKT
    STMT --> PKT
```

- **MysqlClient**: factory method `connect()` opens a TCP socket, performs handshake, returns a ready client
- **MysqlConnection**: manages the full handshake and auth flow including auth switch and AuthMoreData handling
- **MysqlStatement**: sends COM_QUERY, reads OK/ERR/result-set responses, decodes text protocol rows
- **MysqlPreparedStatement**: sends COM_STMT_PREPARE, receives PrepareOK + column/param definitions, sends COM_STMT_EXECUTE with typed parameters and NULL bitmap, reads binary protocol results
- **MysqlResult**: cursor-based result set accessor with `next()`, `getString()`, `getInt()`, `getLong()`, `getDouble()`, `isNull()`

## Authentication Architecture

```mermaid
graph TD
    IF["AuthPlugin interface"] --> MNP["MysqlNativePassword<br/>(SHA1-based, mysql_native_password)"]
    IF --> CSP["CachingSha2Password<br/>(SHA256-based, caching_sha2_password)"]
    ASR["AuthSwitchRequest<br/>(0xFE, plugin switch)"] --> IF
    AMD["AuthMoreData<br/>(0x01, fast auth / full auth)"] --> CSP
```

- **mysql_native_password**: `SHA1(password) XOR SHA1(scramble + SHA1(SHA1(password)))`, stored hash is `SHA1(SHA1(password))`
- **caching_sha2_password**: `SHA256(password) XOR SHA256(SHA256(SHA256(password)) + scramble)`, stored hash is `SHA256(SHA256(password))`
- Both plugins implement `generateAuthResponse()` (client-side) and `verify()` (server-side)
- Server maintains a registry of auth plugins and stored password hashes per user

## Length-Encoded Types

Length-encoded integers and strings are used throughout the protocol:

| Byte Range | Encoding |
|------------|----------|
| 0-250 | 1 byte (value itself) |
| 0xFB (251) | NULL marker |
| 0xFC + 2 bytes LE | Values up to 2^16 - 1 |
| 0xFD + 3 bytes LE | Values up to 2^24 - 1 |
| 0xFE + 8 bytes LE | Values up to 2^64 - 1 |

Strings use a length-encoded integer prefix followed by that many bytes of UTF-8 data. Null-terminated strings are used in specific contexts (HandshakeV10 server version, username, auth plugin name).

## Capability Negotiation

Capabilities are a 32-bit bitmask negotiated during the handshake:

- Server advertises capabilities in HandshakeV10 (split across two 16-bit words)
- Client responds with desired capabilities in HandshakeResponse41
- Negotiated = client AND server capabilities
- Key behavioral switches:
  - `CLIENT_PROTOCOL_41`: enables 4.1 protocol features (status flags, warnings in OK/EOF)
  - `CLIENT_DEPRECATE_EOF`: replaces EOF packets with OK packets in result sets
  - `CLIENT_PLUGIN_AUTH`: enables auth plugin negotiation
  - `CLIENT_SESSION_TRACK`: enables session state tracking in OK packets
  - `CLIENT_CONNECT_ATTRS`: enables connection attribute transmission

## SQL Parser Architecture

The QueryExecutor uses a clause-based parsing approach for SELECT queries:

```mermaid
graph TD
    SQL["SQL String"] --> SPLIT["Clause Splitter<br/>(find keyword boundaries)"]
    SPLIT --> SEL["SELECT clause<br/>(columns, aggregates, aliases)"]
    SPLIT --> FROM["FROM clause<br/>(table, alias)"]
    SPLIT --> JOIN["JOIN clauses<br/>(INNER/LEFT, table, alias, ON condition)"]
    SPLIT --> WHERE["WHERE clause<br/>(conditions: AND/OR, operators)"]
    SPLIT --> GB["GROUP BY clause<br/>(grouping columns)"]
    SPLIT --> HAV["HAVING clause<br/>(aggregate conditions)"]
    SPLIT --> OB["ORDER BY clause<br/>(sort columns, ASC/DESC)"]
    SPLIT --> LIM["LIMIT/OFFSET clause"]
    
    FROM --> EXEC["Query Execution"]
    JOIN --> EXEC
    EXEC --> NLJ["Nested Loop JOIN<br/>(build qualified row maps)"]
    NLJ --> FILT["WHERE Filtering"]
    FILT --> AGG{"Has Aggregates?"}
    AGG -->|Yes| GRP["Group Rows<br/>(by GROUP BY columns)"]
    GRP --> COMP["Compute Aggregates<br/>(COUNT/SUM/AVG/MIN/MAX)"]
    COMP --> HAVF["HAVING Filter"]
    AGG -->|No| SORT["ORDER BY Sort"]
    HAVF --> SORT
    SORT --> SLICE["LIMIT/OFFSET Slice"]
    SLICE --> PROJ["Column Projection<br/>(resolve qualified names)"]
    PROJ --> RES["ResultSet"]
```

Key design decisions:
- **Keyword boundary detection**: `findKeyword()` checks both letter/digit AND underscore boundaries to avoid matching SQL keywords inside table names (e.g., "WHERE" inside "demo_where")
- **Qualified column names**: JOIN rows store values with both qualified (`alias.col`) and unqualified (`col`) keys for flexible resolution
- **Numeric comparison**: `compareValues()` attempts Double.parseDouble before falling back to String comparison
- **LIKE implementation**: Converts SQL LIKE patterns (`%`, `_`) to Java regex for matching

## Transaction Architecture

```mermaid
sequenceDiagram
    participant C as Client
    participant CS as ClientSession
    participant DB as InMemoryDatabase

    C->>CS: BEGIN
    CS->>DB: snapshotAll() — deep copy all table rows
    CS->>CS: Store snapshot per session
    CS->>C: OK

    C->>CS: INSERT/UPDATE/DELETE
    CS->>DB: Execute (modifies live data)
    CS->>C: OK

    alt COMMIT
        C->>CS: COMMIT
        CS->>CS: Discard snapshot
        CS->>C: OK
    else ROLLBACK
        C->>CS: ROLLBACK
        CS->>DB: restoreAll(snapshot) — replace live rows
        CS->>C: OK
    end
```

- Snapshots are per-ClientSession (per-connection), stored as `Map<String, Map<String, List<Map<String, String>>>>` (database -> table -> rows)
- Each row is deep-copied via `new LinkedHashMap<>(row)` to avoid shared references
- ROLLBACK calls `table.restoreSnapshot()` which clears `CopyOnWriteArrayList` and repopulates from snapshot

## Thread Safety Model

- **MysqlServer**: `ConcurrentHashMap` for databases, user hashes, and auth plugins; `AtomicInteger` for connection counter and active connection count
- **ClientSession**: each session runs in its own virtual thread with isolated state
- **InMemoryDatabase**: `ConcurrentHashMap` for tables; `CopyOnWriteArrayList` for rows within each table; `AtomicLong` for auto-increment IDs
- **PreparedStatement**: per-session, no shared state

## Integration with Lego Flow

| Lego Flow Module | Usage in MySQL |
|------------------|----------------|
| `blocks` | DP<I,O> for packet processing pipeline, Statistics for metrics |
| `service` | TCP socket transport, virtual thread pools, lifecycle management |

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
