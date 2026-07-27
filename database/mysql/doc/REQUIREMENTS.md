# MySQL Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 204
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: MySQL Client/Server Protocol (MySQL 8.0 reference)

---

## Requirements

### Packet Framing (MysqlPacket)
1. Encode and decode MySQL packet headers: 3-byte payload length (little-endian) + 1-byte sequence ID
2. Read single packets from an InputStream with proper error handling on truncated streams
3. Read multi-packet payloads: continue reading while payload equals maximum size (16 MB), reassemble into single logical payload
4. Write packets to an OutputStream, automatically splitting payloads exceeding 16 MB into multiple packets with incrementing sequence IDs
5. Terminate exactly-max-size payloads with an empty packet
6. Encode/decode packets to/from byte arrays for in-memory processing

### Wire Protocol Codec (MysqlCodec)
1. Encode and decode COM_QUERY (query string)
2. Encode and decode COM_STMT_PREPARE (query string)
3. Encode and decode COM_STMT_EXECUTE header (statement ID, flags, iteration count)
4. Encode COM_STMT_CLOSE, COM_STMT_RESET, COM_STMT_SEND_LONG_DATA
5. Encode COM_PING, COM_QUIT, COM_INIT_DB, COM_STATISTICS, COM_FIELD_LIST, COM_SET_OPTION, COM_RESET_CONNECTION
6. Encode and decode HandshakeResponse41 with all fields: capabilities, max packet size, charset, username, auth response, database, auth plugin name, connection attributes
7. Encode and decode COM_STMT_PREPARE OK response (statement ID, column count, param count, warning count)
8. Classify response packets: isOk(), isErr(), isEof()

### HandshakeV10
1. Encode and decode server greeting: protocol version, server version string, connection ID, auth plugin data (split into part1 + part2), capability flags (lower + upper), charset, status flags, auth plugin name
2. Generate handshake packets with random scramble data (SecureRandom)
3. Support configurable auth plugin name in handshake creation
4. Default server version: "8.0.35-legoflow", default charset: utf8mb4_general_ci (45)

### Response Packets
1. OkPacket: affected rows, last insert ID, status flags, warnings, info string; capability-aware encoding (CLIENT_PROTOCOL_41, CLIENT_SESSION_TRACK)
2. ErrPacket: error code, SQLSTATE (5-char), message; factory methods for access denied, syntax error, unknown database/table/column
3. EofPacket: warnings, status flags; isEof() detection (0xFE header, payload < 9 bytes)

### Length-Encoded Types
1. Length-encoded integer: read/write with variable-length encoding (1/3/4/9 bytes), NULL marker (0xFB)
2. Length-encoded string: read/write with length prefix, NULL support
3. Null-terminated string: read/write for handshake fields
4. Fixed-length string and rest-of-packet string reading

### Authentication
1. AuthPlugin interface: generateAuthResponse (client-side), verify (server-side)
2. mysql_native_password: SHA1-based challenge-response, double-SHA1 stored hash
3. caching_sha2_password: SHA256-based challenge-response, double-SHA256 stored hash, fast auth success/full auth indicators
4. AuthSwitchRequest: decode/encode auth switch (0xFE header), detect AuthMoreData (0x01 header)

### Server
1. TCP listener with virtual thread per connection (Executors.newVirtualThreadPerTaskExecutor)
2. HandshakeV10 greeting on new connection
3. HandshakeResponse41 decoding and capability negotiation
4. Pluggable authentication with user/password management (addUser, addUserWithHash)
5. Command dispatch for all supported COM_* commands
6. QueryExecutor with regex-based SQL parsing: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, DROP TABLE, SHOW TABLES, SHOW DATABASES, SELECT VERSION()/DATABASE()/COUNT(*)
7. InMemoryDatabase with thread-safe table storage (ConcurrentHashMap + CopyOnWriteArrayList)
8. Text protocol result set writing (column count, column definitions, EOF, text rows, final EOF)
9. Binary protocol result set writing for prepared statement execution
10. Prepared statement lifecycle: prepare (assign ID, count params), execute (substitute params, run SQL), close, reset, send long data
11. COM_FIELD_LIST: return column definitions for a table
12. COM_STATISTICS: return uptime and connection count
13. COM_RESET_CONNECTION: clear session state
14. Graceful shutdown via AutoCloseable

### Client
1. MysqlClient.connect() factory: open TCP socket, perform handshake, return ready client
2. Handshake and authentication including auth switch and AuthMoreData handling
3. MysqlStatement: COM_QUERY for text queries, read OK/ERR/result-set responses
4. MysqlPreparedStatement: COM_STMT_PREPARE, COM_STMT_EXECUTE with typed parameters (string, int, long, double, null), NULL bitmap, COM_STMT_CLOSE, COM_STMT_RESET, COM_STMT_SEND_LONG_DATA
5. MysqlResult: cursor-based result set with next(), getString(), getInt(), getLong(), getDouble(), isNull(), column metadata access
6. Utility commands: ping, useDatabase, statistics, resetConnection, setMultiStatements
7. Connection attributes sent during handshake (client name, version, OS, platform, PID)
8. Graceful disconnect: COM_QUIT on close

### Common Types
1. Charset enum: map charset/collation names to protocol IDs (utf8mb4, latin1, binary, ascii, etc.)
2. MysqlError enum: error code + SQLSTATE + message template for common MySQL errors
3. ColumnType enum: 25 MySQL column types with code mapping, isNumeric/isString/isBlob/isTemporal classification
4. CapabilityFlags: 26 capability flag constants with DEFAULT_SERVER/CLIENT_CAPABILITIES composites
5. StatusFlags: 14 server status flags with hasStatus check and toString formatting

---

## Commit: `pending` - Advanced SQL Engine Features (2026-07-06)

### Original Request
> "Implement missing features for the MySQL module: JOIN queries (INNER/LEFT), ORDER BY/LIMIT, GROUP BY with aggregates, multi-condition WHERE, and real transaction rollback."

### Reformulated Requirements
1. INNER JOIN and LEFT JOIN support with table aliases and chained JOINs
2. ORDER BY with ASC/DESC, multi-column sort, numeric-aware comparison
3. LIMIT with optional OFFSET
4. GROUP BY with aggregate functions: COUNT(*), COUNT(col), SUM, AVG, MIN, MAX
5. HAVING clause for filtering aggregate results
6. Aggregate aliases: SELECT COUNT(*) AS cnt
7. Multi-condition WHERE: AND, OR logical connectors
8. Comparison operators: =, !=, <>, <, >, <=, >=
9. LIKE with % and _ wildcards
10. IS NULL / IS NOT NULL
11. IN (value list)
12. Real transaction ROLLBACK: BEGIN snapshots tables, COMMIT discards snapshot, ROLLBACK restores from snapshot
13. Per-client session transaction state

### Final Design Decisions
- Replaced regex-based SELECT parsing with clause-based approach: split SQL into keyword-delimited clauses (SELECT, FROM, JOIN, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT) and parse each independently
- Keyword finder uses word+underscore boundary detection to avoid matching keywords inside table names (e.g., "WHERE" inside "demo_where")
- JOIN uses nested loop implementation (adequate for in-memory demo)
- Row data stored as maps with both qualified (alias.col) and unqualified (col) keys during JOIN processing
- Transaction snapshots stored per-ClientSession using deep-copied row lists; snapshot covers all databases
- Aggregates computed per group; without GROUP BY, all rows form one group
- Numeric comparison auto-detected by attempting Double.parseDouble

### Implementation Details
- **QueryExecutor.java**: Refactored SELECT handling to clause-based parser; added ParsedSelect, SelectColumn, JoinClause, WhereCondition, OrderByColumn records; implemented JOIN execution with nested loops, WHERE filtering with condition evaluation, GROUP BY with aggregate computation, HAVING filtering, ORDER BY sorting, LIMIT/OFFSET slicing, and column projection
- **InMemoryDatabase.java**: Added snapshot()/restoreSnapshot() to Table; added snapshotAll()/restoreAll() to InMemoryDatabase for transaction support
- **ClientSession.java**: Added transaction state tracking (inTransaction, transactionSnapshot); intercepts BEGIN/COMMIT/ROLLBACK before QueryExecutor; BEGIN creates deep-copy snapshot, COMMIT discards it, ROLLBACK restores from it
- **MysqlServer.java**: Added allDatabases() method for transaction snapshot access
- **DemoMysqlAll.java**: Added 5 new demo methods (joinQueries, orderByLimit, aggregateQueries, advancedWhere, transactionRollback); extended Results record with 5 new fields

### Test Coverage
- **JoinQueryTest**: 12 tests — INNER JOIN, LEFT JOIN, aliases, multi-table, SELECT *, with WHERE, with ORDER BY/LIMIT
- **OrderByLimitTest**: 10 tests — ASC/DESC, numeric sort, multi-column, LIMIT, OFFSET, combined ORDER BY+LIMIT
- **AggregateQueryTest**: 10 tests — COUNT(*), COUNT(col), SUM, AVG, MIN, MAX, GROUP BY, HAVING, alias, ORDER BY with aggregates
- **WhereClauseTest**: 12 tests — equals, AND, OR, !=, <, >=, LIKE %, LIKE _, IS NULL, IS NOT NULL, IN, AND+OR combination
- **TransactionRollbackTest**: 8 tests — COMMIT persists, ROLLBACK reverts INSERT/DELETE/UPDATE, multiple transactions, ROLLBACK/COMMIT without BEGIN, START TRANSACTION
- **DemoMysqlAllTest**: updated with 5 new assertions
- New tests: 52, Total tests: 204 (was 151)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (agent-afd83a993b63b0780) |
| Agent tokens | ~80K |
| Agent tool calls | ~50 |
| Agent wall time | ~30 min |
| Files created/modified | 12 |
| Lines added/removed | +1800 / -60 |
| Tests added | 53 (total: 204) |

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-06
