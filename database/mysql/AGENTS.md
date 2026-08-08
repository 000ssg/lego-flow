# database / mysql — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `mysql` module implements the MySQL client/server wire protocol. It provides both a TCP server (with in-memory database) and a client that can connect to any MySQL-compatible server. Built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `MysqlServer` — TCP server with virtual threads, handshake, authentication, command dispatch
- `MysqlClient` — high-level client with connect, query, prepared statements, ping, statistics
- `MysqlConnection` — connection lifecycle: handshake, auth negotiation, packet send/receive
- `MysqlCodec` — wire protocol codec for all command types and HandshakeResponse41
- `MysqlPacket` — packet framing: 4-byte header (3-byte length LE + 1-byte sequence ID) + payload
- `HandshakeV10` — server greeting packet with protocol version, capabilities, auth data
- `AuthPlugin` — pluggable authentication interface (mysql_native_password, caching_sha2_password)
- `QueryExecutor` — clause-based SQL executor: CREATE, INSERT, SELECT (with JOIN, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT), UPDATE, DELETE, SHOW; supports aggregates (COUNT, SUM, AVG, MIN, MAX), INNER/LEFT JOIN, LIKE, IS NULL, IN
- `ResultSetWriter` — text and binary protocol result set encoding/decoding

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | Wire protocol primitives: MysqlPacket framing, MysqlCodec command encoding, HandshakeV10, OkPacket, ErrPacket, EofPacket, LengthEncodedInt/String, CapabilityFlags, StatusFlags, ColumnType |
| `server` | Server implementation: MysqlServer TCP listener, ClientSession per-connection handler, QueryExecutor SQL engine, InMemoryDatabase storage, ResultSetWriter, PreparedStatement, ColumnDefinition |
| `client` | Client implementation: MysqlClient high-level API, MysqlConnection handshake/auth, MysqlStatement text queries, MysqlPreparedStatement binary protocol, MysqlResult accessor, ConnectionAttributes |
| `auth` | Authentication plugins: AuthPlugin interface, MysqlNativePassword (SHA1), CachingSha2Password (SHA256), AuthSwitchRequest |
| `common` | Shared types: Charset/collation IDs, MysqlError codes and SQLSTATE mappings |

## MySQL-Specific Coding Conventions

### Packet Framing
- 4-byte header: 3-byte payload length (little-endian) + 1-byte sequence ID
- Maximum payload per packet: 16 MB (2^24 - 1 = 16,777,215 bytes)
- Large payloads split into multiple packets with incrementing sequence IDs
- Exactly-max-size payloads followed by empty terminator packet

### Command Bytes
- COM_QUIT (0x01), COM_INIT_DB (0x02), COM_QUERY (0x03), COM_FIELD_LIST (0x04)
- COM_STATISTICS (0x09), COM_PING (0x0E)
- COM_STMT_PREPARE (0x16), COM_STMT_EXECUTE (0x17), COM_STMT_SEND_LONG_DATA (0x18)
- COM_STMT_CLOSE (0x19), COM_STMT_RESET (0x1A), COM_SET_OPTION (0x1B)
- COM_RESET_CONNECTION (0x1F)

### Authentication Flow
1. Server sends HandshakeV10 (protocol version 10, capabilities, scramble, auth plugin name)
2. Client sends HandshakeResponse41 (capabilities, username, auth response, database, attributes)
3. Server may send AuthSwitchRequest (0xFE) to switch auth plugin
4. Server may send AuthMoreData (0x01) for caching_sha2_password fast auth
5. Final OK or ERR packet

### Auth Plugins
- **mysql_native_password**: SHA1(password) XOR SHA1(scramble + SHA1(SHA1(password)))
- **caching_sha2_password**: SHA256(password) XOR SHA256(SHA256(SHA256(password)) + scramble)

### Result Set Protocol (Text)
1. Column count (length-encoded integer)
2. Column definitions (one packet per column)
3. EOF marker (or skipped with CLIENT_DEPRECATE_EOF)
4. Row data (length-encoded strings, 0xFB for NULL)
5. Final EOF (or OK with CLIENT_DEPRECATE_EOF)

### Result Set Protocol (Binary — Prepared Statements)
1. Column count
2. Column definitions
3. EOF marker
4. Binary rows: 0x00 header + NULL bitmap + typed column values
5. Final EOF

### Capability Negotiation
- Server advertises capabilities in HandshakeV10 (split across lower/upper 16-bit words)
- Client sends desired capabilities in HandshakeResponse41
- Negotiated capabilities = client AND server
- Key flags: CLIENT_PROTOCOL_41, CLIENT_SECURE_CONNECTION, CLIENT_PLUGIN_AUTH, CLIENT_DEPRECATE_EOF, CLIENT_SESSION_TRACK, CLIENT_CONNECT_ATTRS

## Testing Practices

- Protocol unit tests: encode/decode round-trip for MysqlPacket, HandshakeV10, Ok/Err/Eof packets
- Codec tests: all command encode/decode, HandshakeResponse encode/decode round-trip
- Length-encoded integer/string tests: boundary values, NULL markers, multi-byte encodings
- Capability and status flags tests: bitmask operations, toString formatting
- Column type tests: numeric/string/blob/temporal classification, fromCode lookup
- Auth plugin tests: mysql_native_password and caching_sha2_password generate/verify/storedHash
- All tests use in-process byte arrays (no external MySQL server required)
- Test count: 204
