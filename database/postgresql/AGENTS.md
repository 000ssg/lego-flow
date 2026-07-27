# PostgreSQL Module — Development Guide

## Module Purpose

The `postgresql` module implements the PostgreSQL v3 frontend/backend wire protocol. It provides both server and client implementations with an in-memory database for testing, built from scratch without external PostgreSQL driver dependencies.

## Key Interfaces

- `PgServer` — wire protocol server using virtual threads, accepts client connections, delegates to `ClientSession`
- `PgClient` — client supporting simple query, extended query (prepared statements), COPY, and LISTEN/NOTIFY
- `PgCodec` — encoder/decoder for all PostgreSQL v3 wire protocol messages (frontend + backend)
- `PgMessage` — sealed interface hierarchy: `FrontendMessage` (client-to-server) and `BackendMessage` (server-to-client)
- `PgAuthenticator` — pluggable authentication interface with cleartext, MD5, and SCRAM-SHA-256 implementations
- `InMemoryDatabase` — minimal SQL engine for testing (CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, DROP TABLE)
- `QueryExecutor` — executes queries against the in-memory database and produces backend messages
- `NotificationManager` — manages LISTEN/NOTIFY pub/sub channels

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `auth` | Authentication mechanisms: `PgAuthenticator` interface, `CleartextAuth`, `Md5Auth`, `ScramSha256` (RFC 5802) with `ScramUtils` crypto utilities |
| `client` | Client implementation: `PgClient` (simple/extended query, COPY, LISTEN/NOTIFY), `PgConnection` (startup/auth handshake), `PgStatement` (prepared statements), `PgCopyStream` (COPY IN/OUT), `PgResult` (result accessor) |
| `common` | Shared types: `PgSeverity` (error severity levels), `SqlState` (SQLSTATE error codes) |
| `protocol` | Wire protocol: `PgMessage`/`FrontendMessage`/`BackendMessage` (sealed record hierarchy), `PgCodec` (encode/decode), `PgType` (type OIDs), `TransactionStatus` |
| `server` | Server implementation: `PgServer` (accept loop on virtual threads), `ClientSession` (per-client lifecycle), `QueryExecutor`, `InMemoryDatabase` (with aggregates, GROUP BY, HAVING, JOINs), `PreparedStatement`, `Portal`, `ResultSet`, `CopyHandler`, `NotificationManager` |
| `demo` | Demo application: `PostgreSqlDemo` (full lifecycle: simple query, extended query, COPY, LISTEN/NOTIFY) |

## PostgreSQL Wire Protocol Conventions

### Message Structure
All typed messages consist of:
1. 1-byte type identifier
2. 4-byte length (including self, excluding type byte)
3. Payload bytes

Untyped messages (StartupMessage, SSLRequest, CancelRequest) omit the type byte and start with the 4-byte length.

### Frontend Messages (Client to Server)
- `StartupMessage` (untyped) — initiates connection with protocol version 3.0 (196608) and parameters
- `SSLRequest` (untyped, code 80877103) — requests SSL upgrade
- `CancelRequest` (untyped, code 80877102) — requests query cancellation
- `PasswordMessage` ('p') — cleartext or MD5 password
- `SASLInitialResponse` ('p') — SCRAM-SHA-256 client-first-message
- `SASLResponse` ('p') — SCRAM-SHA-256 client-final-message
- `Query` ('Q') — simple query protocol
- `Parse` ('P'), `Bind` ('B'), `Describe` ('D'), `Execute` ('E'), `Sync` ('S'), `Flush` ('H'), `Close` ('C') — extended query protocol
- `CopyData` ('d'), `CopyDone` ('c'), `CopyFail` ('f') — COPY protocol
- `Terminate` ('X') — connection close

### Backend Messages (Server to Client)
- `AuthenticationOk/CleartextPassword/MD5Password/SASL*` ('R') — authentication
- `ParameterStatus` ('S'), `BackendKeyData` ('K'), `ReadyForQuery` ('Z') — startup
- `RowDescription` ('T'), `DataRow` ('D'), `CommandComplete` ('C'), `EmptyQueryResponse` ('I') — query results
- `ParseComplete` ('1'), `BindComplete` ('2'), `CloseComplete` ('3'), `NoData` ('n'), `ParameterDescription` ('t'), `PortalSuspended` ('s') — extended query
- `CopyInResponse` ('G'), `CopyOutResponse` ('H'), `CopyBothResponse` ('W'), `CopyData` ('d'), `CopyDone` ('c') — COPY
- `NotificationResponse` ('A') — LISTEN/NOTIFY
- `ErrorResponse` ('E'), `NoticeResponse` ('N') — errors/notices with field codes (S, V, C, M, D, H, P)

### Authentication Methods
- **Trust** — no authentication (authenticator = null)
- **Cleartext** — password sent in plain text
- **MD5** — `"md5" + md5(md5(password + username) + salt)`
- **SCRAM-SHA-256** — RFC 5802 four-step handshake: client-first -> server-first -> client-final -> server-final

### Transaction Status Indicators
- `I` — idle (not in a transaction)
- `T` — in a transaction block
- `E` — in a failed transaction block

## Testing Practices

- Unit tests for codec: encode -> decode round-trip for all message types
- Authentication tests: cleartext, MD5, SCRAM-SHA-256 handshakes
- Server integration tests: simple query, extended query, COPY, LISTEN/NOTIFY
- In-memory database tests: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, WHERE, ORDER BY, LIMIT, aggregates, GROUP BY, HAVING, INNER JOIN, LEFT JOIN
- All tests use loopback transport (no external PostgreSQL required)
- Test count: 300

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
