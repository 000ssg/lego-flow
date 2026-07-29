# database / redis — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `redis` module implements the Redis RESP2/RESP3 wire protocol. It provides a complete Redis-compatible server (in-memory key-value store with virtual threads) and client library, built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `RedisServer` — TCP server with virtual threads, 16 databases, command dispatch, pipelining
- `RedisClient` — client with convenience methods (SET/GET/DEL/PING), pipelining, pub/sub
- `RespCodec` — RESP2/RESP3 encoder for all 15 wire types
- `RespParser` — streaming RESP parser from InputStream, handles partial reads
- `RespType` — sealed interface with 15 record types (SimpleString, Error, Integer, BulkString, Array, Null, RespDouble, RespBoolean, BigNumber, BlobError, VerbatimString, RespMap, RespSet, Attribute, Push)
- `CommandRegistry` — maps command names to `CommandHandler` implementations
- `Database` — in-memory key-value store with TTL expiration (lazy + active)
- `ClusterClient` — cluster-aware client with MOVED/ASK redirect handling

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `protocol` | RESP wire protocol: `RespType` (sealed interface, 15 record types), `RespCodec` (encoder), `RespParser` (streaming decoder), `RespVersion` (RESP2/RESP3), `RespParseException` |
| `command` | Command framework: `RedisCommand` enum (arity + flags), `CommandArgs` (parsed arguments), `CommandHandler` (functional interface), `CommandRegistry` (dispatch) |
| `server` | Server core: `RedisServer` (TCP accept loop, virtual threads, optional password auth), `ClientConnection` (per-client state, auth tracking), `Database` (key-value store), `KeyExpiration` (TTL), `DataType` enum, `PubSubManager`, `StreamStore`, `TransactionExecutor`, `HyperLogLog` |
| `server.impl` | Command handlers by category: `StringCommands`, `ListCommands`, `SetCommands`, `SortedSetCommands`, `HashCommands`, `KeyCommands`, `PubSubCommands`, `StreamCommands`, `TransactionCommands`, `ServerCommands`, `HyperLogLogCommands`, `GeoCommands` |
| `client` | Client library: `RedisClient` (sync commands), `RedisPipeline` (batch execution), `RedisSubscriber` (pub/sub listener), `ClusterClient` (cluster-aware routing) |
| `cluster` | Cluster support: `HashSlot` (CRC16-CCITT, hash tag extraction), `ClusterInfo` (topology, MOVED/ASK redirect parsing) |
| `demo` | Demo applications: `RedisCacheDemo`, `RedisPubSubDemo`, `RedisStreamDemo` |

## Redis-Specific Coding Conventions

### Data Types
- **STRING** — byte arrays stored in `Database.data` via `setString`/`getString`
- **LIST** — `ConcurrentLinkedDeque<byte[]>` via `getOrCreateList`
- **SET** — `ConcurrentHashMap.newKeySet()` (Set<String>) via `getOrCreateSet`
- **ZSET** — `TreeMap<Double, Set<String>>` + reverse index `Map<String, Double>` via `getOrCreateZSet`
- **HASH** — `ConcurrentHashMap<String, byte[]>` via `getOrCreateHash`
- **STREAM** — `StreamStore` with `ConcurrentSkipListMap` entries, consumer groups
- **HYPERLOGLOG** — `HyperLogLog` with 16384 registers, MurmurHash3, harmonic mean estimation

### Authentication
- `RedisServer(String password)` constructor enables password authentication
- `ClientConnection.isAuthenticated()` tracks per-client auth state
- AUTH command validates password, sets authenticated=true
- When password set, all commands except AUTH/PING/QUIT rejected from unauthenticated clients with NOAUTH error

### HyperLogLog
- `HyperLogLog` class: 2^14 registers, MurmurHash3-style 64-bit hash, bias-corrected harmonic mean
- Small range correction (linear counting) when many registers are zero
- Commands: PFADD, PFCOUNT (single + multi-key union), PFMERGE

### Geo Commands
- Stored as sorted sets with 52-bit geohash scores (26 bits lon + 26 bits lat, interleaved)
- Haversine formula for distance calculation
- Commands: GEOADD (NX/XX), GEODIST (m/km/mi/ft), GEOPOS, GEOSEARCH (FROMMEMBER/FROMLONLAT BYRADIUS COUNT ASC/DESC)

### Command Implementation Pattern
Each command category follows the same pattern:
1. A `static void register(CommandRegistry)` method registers all handlers
2. Each handler is a `private static RespType method(CommandArgs, ClientConnection)`
3. Handlers extract the `Database` from `client.database()`, perform operations, return `RespType`
4. Write operations call `client.server().transactionExecutor().touchKey(key)` for WATCH support

### Transaction Model
- `MULTI` begins queuing; queued commands return `+QUEUED`
- `EXEC` validates WATCHed key versions, then executes atomically
- `WATCH` records key versions; if any change before `EXEC`, transaction aborts (returns null array)
- Transaction-bypass commands: EXEC, DISCARD, MULTI, WATCH

### Expiration Model
- **Lazy**: checked on every key access via `Database.exists()` -> `expiration.isExpired()`
- **Active**: `expireActiveKeys()` sweeps all expired keys (called from `size()`, `keys()`, `scan()`, `randomKey()`)
- Timestamps stored as absolute milliseconds in `ConcurrentHashMap<String, Long>`

### Protocol Negotiation
- Default: RESP2 per client
- HELLO command switches to RESP3, returns a Map (RESP3) or flat Array (RESP2)
- `ClientConnection.respVersion` tracks per-client protocol version

## Testing Practices

- Codec tests: encode -> decode round-trip for all 15 RESP types (RESP2 + RESP3)
- Parser tests: streaming parse from byte arrays, inline commands, edge cases
- Command tests: per-category integration tests using real RedisServer + RedisClient
- Each test class starts a server on ephemeral port, connects a client, runs commands, asserts responses
- All tests use loopback transport (no external Redis required)
- Test count: 176
