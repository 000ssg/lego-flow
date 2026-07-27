# Redis Module — Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 146
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: Redis RESP2, Redis RESP3 (Redis 6.0+)

---

## Requirements

### RESP Wire Protocol
1. Encode and decode all 5 RESP2 types: Simple String (+), Error (-), Integer (:), Bulk String ($), Array (*)
2. Encode and decode all 10 RESP3 extension types: Null (_), Double (,), Boolean (#), Big Number ((), Blob Error (!), Verbatim String (=), Map (%), Set (~), Attribute (|), Push (>)
3. Handle null bulk strings ($-1) and null arrays (*-1) as sentinels
4. Parse inline commands (non-RESP, space-separated) for compatibility
5. Support streaming parse from InputStream with blocking on partial messages
6. Protocol version negotiation via HELLO command (switch between RESP2 and RESP3)

### Server
1. Accept TCP connections with virtual-thread-per-connection model
2. Support 16 databases (SELECT 0-15), each with independent key namespace
3. Dispatch commands via CommandRegistry to registered CommandHandler implementations
4. Handle pipelining (multiple commands per TCP read, responses in order)
5. Track per-client state: selected database, RESP version, transaction state, pub/sub subscriptions, client name
6. Configurable bind address and port (0 for ephemeral)
7. Graceful shutdown: close server socket, disconnect all clients, shutdown executor

### String Commands
1. SET with options: NX, XX, EX, PX, EXAT, PXAT, KEEPTTL, GET
2. GET, MSET, MGET for basic read/write
3. APPEND, STRLEN for string manipulation
4. INCR, DECR, INCRBY, DECRBY for atomic integer operations
5. INCRBYFLOAT for atomic floating-point operations
6. GETSET, SETNX, SETEX, PSETEX for conditional/timed operations
7. GETRANGE, SETRANGE for substring operations
8. GETDEL for atomic get-and-delete

### List Commands
1. LPUSH, RPUSH for push; LPOP, RPOP for pop
2. LLEN, LRANGE for inspection; LINDEX for random access
3. LSET, LREM, LINSERT, LTRIM for mutation
4. BLPOP, BRPOP for blocking pop operations
5. LMOVE, BLMOVE for atomic move between lists

### Set Commands
1. SADD, SREM for add/remove
2. SMEMBERS, SISMEMBER, SCARD for inspection
3. SINTER, SUNION, SDIFF for set operations
4. SRANDMEMBER, SPOP for random access
5. SMOVE for atomic move between sets

### Sorted Set Commands
1. ZADD, ZREM for add/remove with scores
2. ZSCORE, ZRANK, ZREVRANK for score/rank queries
3. ZRANGE, ZRANGEBYSCORE, ZRANGEBYLEX for range queries
4. ZCARD, ZCOUNT for size queries
5. ZINCRBY for atomic score increment
6. ZINTERSTORE, ZUNIONSTORE for set operations with scores
7. ZPOPMIN, ZPOPMAX for priority queue operations

### Hash Commands
1. HSET, HGET, HMSET, HMGET for field read/write
2. HDEL, HEXISTS for field management
3. HLEN, HKEYS, HVALS, HGETALL for inspection
4. HINCRBY, HINCRBYFLOAT for atomic field increment
5. HSETNX for conditional set

### Key Commands
1. DEL, UNLINK for key deletion
2. EXISTS for existence check (supports multiple keys)
3. EXPIRE, EXPIREAT, PEXPIRE for TTL management
4. TTL, PTTL for TTL inspection
5. PERSIST for removing expiration
6. TYPE for data type query
7. KEYS with glob pattern matching
8. SCAN with cursor-based iteration and optional pattern/count
9. RENAME for key renaming (transfers value, type, TTL, stream data)
10. RANDOMKEY for random key selection

### Pub/Sub
1. SUBSCRIBE, UNSUBSCRIBE for exact channel subscriptions
2. PSUBSCRIBE, PUNSUBSCRIBE for glob pattern subscriptions
3. PUBLISH for message delivery to channel and pattern subscribers
4. PUBSUB subcommands: CHANNELS, NUMSUB, NUMPAT for introspection
5. Messages delivered as RESP arrays: [type, channel, message] or [type, pattern, channel, message]

### Streams
1. XADD with auto-generated (*) or explicit entry IDs (timestamp-sequence format)
2. XLEN for stream length
3. XRANGE, XREVRANGE for range queries with - and + sentinels
4. XREAD for consuming entries after a given ID
5. XGROUP CREATE, DESTROY, DELCONSUMER for consumer group management
6. XREADGROUP for consumer group reading with > for new messages or ID for pending
7. XACK for acknowledging processed entries
8. XPENDING for listing pending entries
9. XCLAIM for transferring pending entries between consumers with minimum idle time
10. XTRIM for capping stream length

### Transactions
1. MULTI to begin queuing commands
2. EXEC to atomically execute queued commands and return all results
3. DISCARD to abort and clear the queue
4. WATCH for optimistic locking: record key versions at WATCH time
5. UNWATCH to clear all watched keys
6. Abort transaction (return null array) if any watched key version changed between WATCH and EXEC

### Server Commands
1. PING/ECHO for connectivity testing
2. INFO with sections: server, keyspace, clients, memory
3. DBSIZE for current database key count
4. FLUSHDB, FLUSHALL for clearing data
5. SELECT for database switching
6. COMMAND, COMMAND COUNT for command introspection
7. CLIENT ID, SETNAME, GETNAME, LIST for client management
8. CONFIG GET, SET, RESETSTAT for configuration
9. DEBUG SLEEP for testing
10. HELLO for RESP version negotiation (2 or 3) with SETNAME support
11. QUIT for graceful disconnect
12. RESET to return client to initial state
13. CLUSTER INFO, MYID, NODES, SLOTS, KEYSLOT for cluster protocol

### Client
1. Connect to server via TCP socket
2. Execute commands with RESP encoding and response parsing
3. Convenience methods: set(), get(), del(), ping()
4. Send-without-wait and receive-separately for async patterns
5. Static extraction helpers: extractString, extractLong, extractStringList

### Pipelining
1. Buffer multiple commands locally without sending
2. Send all buffered commands in a single TCP write
3. Read all responses in order
4. Discard queued commands without executing

### Pub/Sub Client
1. Subscribe to channels and patterns
2. Receive messages via blocking nextMessage() or non-blocking poll()
3. Register callback handler for incoming messages
4. Parse message, pmessage, subscribe, unsubscribe response types

### Cluster Client
1. Connect to seed node and discover topology
2. Route commands to correct node based on CRC16 hash slot (key mod 16384)
3. Support hash tags: {tag}key hashes only the tag content
4. Handle MOVED redirects by updating slot-to-node mapping permanently
5. Handle ASK redirects by sending ASKING before retry
6. Limit redirect chain to 5 hops maximum

### Key Expiration
1. Lazy expiration: check TTL on every key access, delete if expired
2. Active expiration: sweep expired keys when size/keys/scan/randomKey called
3. Support TTL in seconds and milliseconds
4. Support absolute expiration timestamps (EXAT, PXAT)
5. PERSIST to remove expiration and make key permanent

### Demo Applications
1. RedisCacheDemo: SET/GET with TTL, atomic counters, hash-based objects, MSET/MGET
2. RedisPubSubDemo: multi-channel pub/sub with virtual threads, subscriber and publisher coordination
3. RedisStreamDemo: stream entries, consumer group creation, group reading

### Authentication (AUTH)
1. Configurable password via `RedisServer(String password)` constructor
2. Per-client auth state in `ClientConnection.authenticated`
3. Reject all commands except AUTH/PING/QUIT from unauthenticated clients with NOAUTH error
4. AUTH command validates password, returns +OK or error
5. When no password configured, AUTH returns "no password is set" error

### HyperLogLog Commands
1. `HyperLogLog` data structure with 2^14 = 16384 registers (matching Redis)
2. MurmurHash3-style 64-bit hash function
3. Bias-corrected harmonic mean formula: E = alpha * m^2 / sum(2^(-register[i]))
4. Small range correction (linear counting) when many registers are zero
5. PFADD key element [element ...] -> :0 or :1
6. PFCOUNT key [key ...] -> estimated cardinality (union for multiple keys)
7. PFMERGE destkey sourcekey [sourcekey ...] -> +OK

### Geo Commands
1. Geohash encoding: 52-bit interleaved (26 bits lon + 26 bits lat) stored as sorted set score
2. GEOADD key [NX|XX] longitude latitude member [...] -> number added
3. GEODIST key member1 member2 [m|km|mi|ft] -> distance or null
4. GEOPOS key member [member ...] -> array of [longitude, latitude] arrays
5. GEOSEARCH key FROMMEMBER member|FROMLONLAT lon lat BYRADIUS radius unit [COUNT count] [ASC|DESC]
6. Haversine formula for great-circle distance calculation
7. Distance units: meters (m), kilometers (km), miles (mi), feet (ft)

---

## Commit: `pending` - AUTH, HyperLogLog, Geo Commands (2026-07-06)

### Original Request
> "Implement AUTH command support, HyperLogLog commands (PFADD, PFCOUNT, PFMERGE), and Geo commands (GEOADD, GEODIST, GEOPOS, GEOSEARCH) for the Redis module."

### Reformulated Requirements
1. Add password authentication to RedisServer with per-client auth tracking
2. Implement HyperLogLog probabilistic cardinality estimation with 16384 registers
3. Implement geospatial commands using sorted sets with geohash encoding
4. Add comprehensive tests for all three feature areas
5. Update demo and documentation

### Final Design Decisions
- AUTH implemented inline in RedisServer.registerCommands() for direct access to password field
- HyperLogLog uses MurmurHash3-style 64-bit hash with harmonic mean estimation and linear counting correction
- Geo commands store data in sorted sets with 52-bit interleaved geohash scores, matching Redis approach
- HyperLogLogCommands and GeoCommands follow existing server.impl pattern

### Implementation Details
- Files created: `HyperLogLog.java`, `HyperLogLogCommands.java`, `GeoCommands.java`, `AuthenticationTest.java`, `HyperLogLogTest.java`, `GeoCommandsTest.java`
- Files modified: `RedisServer.java`, `ClientConnection.java`, `DataType.java`, `Database.java`, `DemoRedisAll.java`, `DemoRedisAllTest.java`
- Documentation updated: `CLAUDE.md`, `README.md`, `ARCHITECTURE.md`, `COMPLIANCE.md`, `REQUIREMENTS.md`

### Test Coverage
- AuthenticationTest: 8 tests
- HyperLogLogTest: 10 tests (including 5% accuracy test for 10K elements)
- GeoCommandsTest: 12 tests
- Total new tests: 30 (total: ~176)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (agent-a7ac350b61b0ac630) |
| Agent tokens | ~50K |
| Agent tool calls | ~40 |
| Agent wall time | ~15 min |
| Files created/modified | 15 |
| Lines added/removed | +900 / -10 |
| Tests added | 30 (total: ~176) |

---

**Last Updated**: 2026-07-06
