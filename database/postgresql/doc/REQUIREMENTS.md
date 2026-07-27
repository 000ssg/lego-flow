# PostgreSQL Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 300
- **Dependencies**: None (standalone wire protocol implementation)
- **Standards**: PostgreSQL v3 Frontend/Backend Protocol

---

## Requirements

### Wire Protocol Codec (PgCodec)
1. Encode and decode all frontend (client-to-server) message types as binary
2. Encode and decode all backend (server-to-client) message types as binary
3. Handle typed messages: 1-byte type identifier + 4-byte length + payload
4. Handle untyped startup-phase messages: StartupMessage, SSLRequest, CancelRequest
5. Support null-terminated C-string encoding/decoding for all string fields
6. Support big-endian integer encoding (int16, int32) per PostgreSQL protocol convention
7. Support stream-based I/O with `InputStream`/`OutputStream` for incremental message processing
8. Correctly handle the startup/non-startup mode distinction when decoding frontend messages

### Message Types — Frontend (Client to Server)
1. StartupMessage: protocol version (3.0 = 196608), key-value parameters (user, database, client_encoding)
2. SSLRequest: SSL upgrade request (code 80877103)
3. CancelRequest: query cancellation (code 80877102, processId, secretKey)
4. PasswordMessage: cleartext or MD5-hashed password
5. SASLInitialResponse: SASL mechanism name + client-first-message
6. SASLResponse: client-final-message
7. Query: simple query protocol (SQL string)
8. Parse: create prepared statement (name, SQL with $N placeholders, parameter type OIDs)
9. Bind: bind parameters to portal (portal name, statement name, parameter formats/values, result formats)
10. Describe: describe statement ('S') or portal ('P')
11. Execute: execute portal with optional row limit
12. Sync: end extended query sequence, request ReadyForQuery
13. Flush: flush server output buffer
14. Close: close statement ('S') or portal ('P')
15. CopyData: COPY data chunk
16. CopyDone: end of COPY IN data
17. CopyFail: abort COPY IN with error
18. Terminate: close connection

### Message Types — Backend (Server to Client)
1. AuthenticationOk, AuthenticationCleartextPassword, AuthenticationMD5Password (with 4-byte salt)
2. AuthenticationSASL (mechanism list), AuthenticationSASLContinue, AuthenticationSASLFinal
3. ParameterStatus: server parameter key-value pairs
4. BackendKeyData: processId and secretKey for cancel requests
5. ReadyForQuery: transaction status indicator (I=idle, T=in-transaction, E=failed)
6. RowDescription: column metadata (name, tableOid, columnIndex, typeOid, typeSize, typeModifier, formatCode)
7. DataRow: column values as byte arrays (null = SQL NULL)
8. CommandComplete: command tag string (e.g., "SELECT 5", "INSERT 0 1", "UPDATE 3")
9. EmptyQueryResponse: response to empty query string
10. ParseComplete, BindComplete, CloseComplete: extended query acknowledgements
11. NoData: describe returned no row description
12. ParameterDescription: prepared statement parameter OIDs
13. PortalSuspended: Execute completed with row limit, more rows available
14. CopyInResponse, CopyOutResponse, CopyBothResponse: COPY mode indicators with format codes
15. CopyData, CopyDone: COPY data transfer
16. NotificationResponse: asynchronous LISTEN/NOTIFY (processId, channel, payload)
17. ErrorResponse, NoticeResponse: error/notice fields (Severity, SQLSTATE, Message, Detail, Hint, Position)

### Authentication
1. Pluggable authentication via PgAuthenticator interface (method(), authenticate())
2. Trust authentication: no password required (authenticator = null)
3. Cleartext password authentication: password sent in plain text
4. MD5 password authentication: server sends 4-byte salt, client computes md5(md5(password+username)+salt)
5. SCRAM-SHA-256 authentication (RFC 5802): full 4-step handshake
6. SCRAM server-side: ServerSession with processClientFirst() and processClientFinal()
7. SCRAM client-side: ClientSession with createClientFirstMessage(), processServerFirst(), verifyServerFinal()
8. SCRAM cryptographic primitives: HMAC-SHA-256, SHA-256, PBKDF2 (Hi function), XOR, nonce generation
9. Thread-safe credential storage using ConcurrentHashMap

### Client (PgClient)
1. Connect to PostgreSQL server with host, port, database, username, password
2. Perform startup handshake with authentication negotiation
3. Simple query: send Query message, collect RowDescription + DataRow + CommandComplete + ReadyForQuery
4. Execute: simple query shortcut returning affected row count
5. Extended query: prepare statement (Parse + Describe + Sync), execute with parameters (Bind + Execute + Sync)
6. PgStatement: named prepared statement with execute(params), execute(maxRows, params), describe(), close()
7. COPY IN: send COPY command, stream CopyData rows, send CopyDone
8. COPY OUT: send COPY command, collect CopyData rows until CopyDone
9. LISTEN/NOTIFY: subscribe to channels with callback, send notifications
10. PgResult: typed accessor for query results (getString, getInt, getLong, getBoolean, isNull, affectedRows)
11. AutoCloseable: send Terminate message on close

### Server (PgServer)
1. Listen on configurable port (0 for ephemeral) with ServerSocket
2. Accept connections in a virtual thread accept loop
3. Spawn per-client virtual threads with ClientSession
4. Handle startup phase: SSLRequest rejection, StartupMessage parsing, authentication
5. Send server parameters (server_version, encoding, DateStyle, TimeZone, etc.)
6. Send BackendKeyData with unique processId and secretKey per session
7. Handle simple query protocol: parse SQL, execute against InMemoryDatabase, return result messages
8. Handle extended query protocol: Parse, Bind, Describe, Execute, Sync, Close
9. Handle COPY IN/OUT: CopyInResponse/CopyOutResponse, process CopyData, CopyDone, CopyFail
10. Handle LISTEN/NOTIFY: subscribe/unsubscribe channels, deliver pending notifications
11. Handle transaction state tracking: BEGIN/COMMIT/ROLLBACK, failed transaction detection
12. AutoCloseable: close all sessions and server socket

### In-Memory Database
1. Support CREATE TABLE (with IF NOT EXISTS, column definitions, type parsing)
2. Support INSERT INTO with column list or positional values
3. Support SELECT with column projection, WHERE (= and != with AND), ORDER BY (ASC/DESC), LIMIT
4. Support UPDATE with SET clause and WHERE filter
5. Support DELETE with WHERE filter
6. Support DROP TABLE (with IF EXISTS)
7. Support BEGIN, COMMIT, ROLLBACK, SET (no-op for protocol compliance)
8. Substitute $1, $2, ... parameter placeholders in SQL
9. Handle SQL NULL values, quoted string escaping, constraint keywords
10. Thread-safe table storage using ConcurrentHashMap

### Type System (PgType)
1. Map 22 PostgreSQL type names to their OIDs
2. Support type lookup by OID (fromOid) and by name (fromName, case-insensitive)
3. Handle common type aliases (integer->int4, bigint->int8, boolean->bool, etc.)
4. Provide type size information (fixed sizes for int/float/date, -1 for variable-length)

### Common Types
1. PgSeverity: 8 severity levels (FATAL, PANIC, ERROR, WARNING, NOTICE, DEBUG, INFO, LOG)
2. SqlState: 30 SQLSTATE error codes organized by class (connection, authorization, syntax, data, integrity, etc.)
3. TransactionStatus: 3 states (IDLE, IN_TRANSACTION, FAILED) with wire protocol byte indicators

### Demo Application
1. PostgreSqlDemo: start embedded server, run simple query demo (CRUD), run extended query demo (prepared statements)
2. All demos self-contained with server and client in same process

### Aggregate Functions
1. Support COUNT(*), COUNT(col), SUM(col), AVG(col), MIN(col), MAX(col) in SELECT
2. Support GROUP BY with one or more columns
3. Support HAVING clause for filtering groups by aggregate conditions
4. Support column aliases: SELECT COUNT(*) AS cnt
5. Support mixing aggregates with non-aggregate columns (when using GROUP BY)
6. Support WHERE filtering before aggregation
7. Support multiple aggregates in a single SELECT

### JOIN Support
1. Support INNER JOIN with ON equality condition
2. Support LEFT JOIN with NULL-filled non-matching rows
3. Support multiple chained JOINs
4. Support table aliases: FROM orders o JOIN customers c ON o.customer_id = c.id
5. Support qualified column references: t1.col, alias.col, or plain col
6. Support WHERE, ORDER BY, LIMIT with JOINs
7. Support SELECT * with JOINs (all columns from all tables)

---

## Commit: `pending` - SCRAM-SHA-256 Auth Tests, Aggregates, JOINs (2026-07-06)

### Original Request
> "Implement SCRAM-SHA-256 authentication integration tests, aggregate functions with GROUP BY/HAVING, and JOIN support (INNER/LEFT) for the PostgreSQL module's in-memory database."

### Reformulated Requirements
1. Create SCRAM-SHA-256 integration tests via full client/server handshake (~10 tests)
2. Add aggregate functions (COUNT, SUM, AVG, MIN, MAX) to InMemoryDatabase
3. Add GROUP BY and HAVING support
4. Add INNER JOIN and LEFT JOIN support with table aliases
5. Add column alias support (AS)
6. Create test classes for aggregates (~15 tests) and JOINs (~12 tests)
7. Update DemoPostgreSqlAll with new demo sections
8. Update all documentation

### Final Design Decisions
- Aggregate parsing uses regex to detect COUNT/SUM/AVG/MIN/MAX patterns in SELECT
- GROUP BY groups rows into LinkedHashMap buckets keyed by group column values
- HAVING evaluates aggregate expressions against each group
- JOIN execution uses nested-loop join with Map<String, String[]> per joined row
- JOIN clause parsing walks forward through the SQL, extracting JOIN...ON pairs before trailing clauses
- Table aliases stored in LinkedHashMap<String, TableContext> for ordered iteration
- Column resolution tries qualified (alias.col) first, then searches all tables for unqualified names

### Implementation Details
- Modified: `InMemoryDatabase.java` (added ~500 lines: aggregate, GROUP BY, HAVING, JOIN parsing and execution)
- Modified: `DemoPostgreSqlAll.java` (added SCRAM-SHA-256, aggregate, JOIN demos; extended Results record)
- Modified: `DemoPostgreSqlAllTest.java` (added assertions for new fields)
- Created: `ScramSha256AuthTest.java` (10 integration tests)
- Created: `AggregateQueryTest.java` (15 tests)
- Created: `JoinQueryTest.java` (12 tests)
- Updated: CLAUDE.md, README.md, ARCHITECTURE.md, COMPLIANCE.md, REQUIREMENTS.md

### Test Coverage
- New tests: 38 (10 SCRAM auth + 15 aggregate + 12 JOIN + 1 demo)
- Total tests: 300

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (agent-aa39d1138a1894d0d) |
| Agent tokens | ~80K |
| Agent tool calls | ~40 |
| Agent wall time | ~20 min |
| Files created/modified | 10 |
| Lines added/removed | +900 / -30 |
| Tests added | 38 (total: 300) |

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-06
