
# Lego Flow Redis Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-176-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

Redis RESP2/RESP3 wire protocol module for the Lego Flow framework, providing a complete Redis-compatible server and client library.

## Overview

This module implements the Redis Serialization Protocol (RESP) versions 2 and 3, enabling Java applications to build Redis-compatible servers and clients. The architecture provides an in-memory key-value store with virtual-thread-based concurrency:

```
Redis Client / Cluster Client (application layer)
  -> Pipeline / Subscriber (batching and pub/sub)
    -> RESP Codec (encode/decode all 15 wire types)
      -> Command Registry (dispatch to handlers)
        -> Database (key-value store, 16 databases)
          -> Key Expiration (lazy + active TTL eviction)
            -> TCP Transport (virtual threads, service module)
```

## Features

- **RESP2 and RESP3** -- full wire protocol support with all 15 type prefixes (SimpleString, Error, Integer, BulkString, Array, Null, Double, Boolean, BigNumber, BlobError, VerbatimString, Map, Set, Attribute, Push)
- **String commands** -- SET (NX/XX/EX/PX/EXAT/PXAT/KEEPTTL/GET), GET, MSET, MGET, APPEND, STRLEN, INCR, DECR, INCRBY, DECRBY, INCRBYFLOAT, GETSET, SETNX, SETEX, PSETEX, GETRANGE, SETRANGE, GETDEL
- **List commands** -- LPUSH, RPUSH, LPOP, RPOP, LLEN, LRANGE, LINDEX, LSET, LREM, LINSERT, LTRIM, BLPOP, BRPOP, LMOVE, BLMOVE
- **Set commands** -- SADD, SREM, SMEMBERS, SISMEMBER, SCARD, SINTER, SUNION, SDIFF, SRANDMEMBER, SPOP, SMOVE
- **Sorted set commands** -- ZADD, ZREM, ZSCORE, ZRANK, ZREVRANK, ZRANGE, ZRANGEBYSCORE, ZRANGEBYLEX, ZCARD, ZCOUNT, ZINCRBY, ZINTERSTORE, ZUNIONSTORE, ZPOPMIN, ZPOPMAX
- **Hash commands** -- HSET, HGET, HMSET, HMGET, HDEL, HEXISTS, HLEN, HKEYS, HVALS, HGETALL, HINCRBY, HINCRBYFLOAT, HSETNX
- **Key commands** -- DEL, EXISTS, EXPIRE, EXPIREAT, PEXPIRE, TTL, PTTL, PERSIST, TYPE, KEYS, SCAN, RENAME, RANDOMKEY, UNLINK
- **Pub/Sub** -- SUBSCRIBE, UNSUBSCRIBE, PSUBSCRIBE, PUNSUBSCRIBE, PUBLISH, PUBSUB (CHANNELS/NUMSUB/NUMPAT)
- **Streams** -- XADD, XLEN, XRANGE, XREVRANGE, XREAD, XGROUP (CREATE/DESTROY/DELCONSUMER), XREADGROUP, XACK, XPENDING, XCLAIM, XTRIM
- **Transactions** -- MULTI, EXEC, DISCARD, WATCH, UNWATCH with optimistic locking
- **Server commands** -- PING, ECHO, INFO, DBSIZE, FLUSHDB, FLUSHALL, SELECT, COMMAND, CLIENT (ID/SETNAME/GETNAME/LIST), CONFIG, DEBUG, HELLO, QUIT, RESET, CLUSTER (INFO/MYID/NODES/SLOTS/KEYSLOT)
- **Authentication** -- AUTH command with configurable password, per-client auth state tracking
- **HyperLogLog commands** -- PFADD, PFCOUNT, PFMERGE for probabilistic cardinality estimation (16384 registers, <1% standard error)
- **Geo commands** -- GEOADD, GEODIST, GEOPOS, GEOSEARCH with geohash encoding and Haversine distance
- **Pipelining** -- batch multiple commands in a single TCP write for reduced latency
- **Cluster support** -- CRC16 hash slot calculation, hash tag extraction, MOVED/ASK redirect handling
- **Virtual threads** -- each client connection handled on its own virtual thread
- **TTL expiration** -- lazy expiration on access plus active sweep

## Quick Start

### Start a server

```java
var server = new RedisServer();
server.start(6379);
```

### Connect a client

```java
try (var client = new RedisClient("127.0.0.1", 6379)) {
    client.connect();

    // Basic SET/GET
    client.set("key", "value");
    String value = client.get("key");

    // SET with TTL
    client.execute("SET", "session:123", "user-data", "EX", "3600");

    // Atomic counter
    client.execute("INCR", "page:views");
}
```

### Pipelining

```java
try (var client = new RedisClient("127.0.0.1", 6379)) {
    client.connect();
    var pipeline = client.pipeline();
    pipeline.add("SET", "k1", "v1");
    pipeline.add("SET", "k2", "v2");
    pipeline.add("GET", "k1");
    pipeline.add("GET", "k2");
    List<RespType> responses = pipeline.execute();
}
```

### Pub/Sub

```java
// Subscriber
try (var sub = new RedisClient("127.0.0.1", 6379)) {
    sub.connect();
    var subscriber = sub.subscriber();
    subscriber.onMessage((channel, message) ->
        System.out.println(channel + ": " + message));
    subscriber.subscribe("news", "alerts");
    // Read messages in a loop
    subscriber.nextMessage();
}

// Publisher
try (var pub = new RedisClient("127.0.0.1", 6379)) {
    pub.connect();
    pub.execute("PUBLISH", "news", "Breaking news!");
}
```

### Streams with consumer groups

```java
try (var client = new RedisClient("127.0.0.1", 6379)) {
    client.connect();
    client.execute("XADD", "events", "*", "type", "login", "user", "alice");
    client.execute("XGROUP", "CREATE", "events", "processors", "0", "MKSTREAM");
    client.execute("XREADGROUP", "GROUP", "processors", "worker-1",
        "COUNT", "10", "STREAMS", "events", ">");
}
```

### Cluster-aware client

```java
try (var cluster = new ClusterClient("127.0.0.1", 6379)) {
    cluster.connect();
    cluster.execute("SET", "key", "value"); // Auto-routes by hash slot
    int slot = cluster.slotForKey("{user}.session");
}
```

## Package Structure

```
ssg.legoflow.database.redis/
├── protocol/          -- RESP wire protocol: RespType (15 types), RespCodec, RespParser, RespVersion
├── command/           -- Command framework: RedisCommand enum, CommandArgs, CommandHandler, CommandRegistry
├── server/            -- Server core: RedisServer, ClientConnection, Database, KeyExpiration, PubSubManager, StreamStore, TransactionExecutor
│   └── impl/          -- Command handlers: StringCommands, ListCommands, SetCommands, SortedSetCommands, HashCommands, KeyCommands, PubSubCommands, StreamCommands, TransactionCommands, ServerCommands
├── client/            -- Client library: RedisClient, RedisPipeline, RedisSubscriber, ClusterClient
├── cluster/           -- Cluster support: HashSlot (CRC16), ClusterInfo (topology, redirects)
└── demo/              -- Demo applications: RedisCacheDemo, RedisPubSubDemo, RedisStreamDemo
```

## Demo Applications

1. **RedisCacheDemo** -- SET/GET with TTL, atomic counters (INCR/DECR), hash-based object storage, MSET/MGET batch operations
2. **RedisPubSubDemo** -- Multi-channel pub/sub with subscriber and publisher on virtual threads
3. **RedisStreamDemo** -- Stream entries (XADD), consumer groups (XGROUP CREATE), group reading (XREADGROUP)

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- TCP transport, lifecycle management, virtual threads
- `slf4j-api` -- Logging facade

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
