# Redis Module — Architecture

This document describes the architectural decisions for the Redis module.

---

## Protocol Overview

Redis uses the RESP (Redis Serialization Protocol) as its wire format. RESP2 (default since Redis 1.2) provides 5 types: Simple String, Error, Integer, Bulk String, and Array. RESP3 (introduced in Redis 6.0) adds 10 additional types: Null, Double, Boolean, Big Number, Blob Error, Verbatim String, Map, Set, Attribute, and Push. This implementation supports both protocol versions with runtime negotiation via the HELLO command.

## Layered Architecture

```mermaid
graph TD
    L1["Client Layer<br/>(RedisClient, ClusterClient, RedisPipeline, RedisSubscriber)"]
    L2["Protocol Layer<br/>(RespCodec encoder, RespParser streaming decoder,<br/>RespType sealed interface with 15 record types)"]
    L3["Command Layer<br/>(CommandRegistry dispatch, CommandArgs parsing,<br/>RedisCommand enum with arity + flags)"]
    L4["Server Layer<br/>(RedisServer TCP accept, ClientConnection per-client state,<br/>virtual thread per connection)"]
    L5["Storage Layer<br/>(Database x16, KeyExpiration lazy+active,<br/>PubSubManager, StreamStore, TransactionExecutor)"]
    L6["Command Handlers<br/>(StringCommands, ListCommands, SetCommands,<br/>SortedSetCommands, HashCommands, KeyCommands,<br/>PubSubCommands, StreamCommands, TransactionCommands,<br/>ServerCommands, HyperLogLogCommands, GeoCommands)"]

    L1 --> L2 --> L3 --> L4 --> L5
    L3 --> L6
    L6 --> L5
```

## RESP Type System

The `RespType` sealed interface models all 15 wire types as Java records:

```mermaid
graph TD
    RT["RespType<br/>(sealed interface)"]

    subgraph RESP2["RESP2 Types"]
        SS["SimpleString<br/>+ prefix"]
        ER["Error<br/>- prefix"]
        IN["Integer<br/>: prefix"]
        BS["BulkString<br/>$ prefix"]
        AR["Array<br/>* prefix"]
    end

    subgraph RESP3["RESP3 Types"]
        NU["Null<br/>_ prefix"]
        DO["RespDouble<br/>, prefix"]
        BO["RespBoolean<br/># prefix"]
        BN["BigNumber<br/>( prefix"]
        BE["BlobError<br/>! prefix"]
        VS["VerbatimString<br/>= prefix"]
        RM["RespMap<br/>% prefix"]
        RS["RespSet<br/>~ prefix"]
        AT["Attribute<br/>| prefix"]
        PU["Push<br/>> prefix"]
    end

    RT --> SS & ER & IN & BS & AR
    RT --> NU & DO & BO & BN & BE & VS & RM & RS & AT & PU
```

## Server Architecture

```mermaid
graph TD
    TC["TCP Accept Loop<br/>(ServerSocket on virtual thread)"]
    VT["Virtual Thread Pool<br/>(Executors.newVirtualThreadPerTaskExecutor)"]
    CC["ClientConnection<br/>(per-client: socket, DB index, RESP version,<br/>transaction state, pub/sub subscriptions)"]
    RP["RespParser<br/>(streaming parse from InputStream)"]
    CR["CommandRegistry<br/>(Map&lt;String, CommandHandler&gt;)"]
    DB["Database[16]<br/>(ConcurrentHashMap-backed stores)"]
    TX["TransactionExecutor<br/>(WATCH key versions, MULTI queue, atomic EXEC)"]
    PS["PubSubManager<br/>(channel + pattern subscriptions, message routing)"]

    TC -->|"accept()"| VT
    VT -->|"one per client"| CC
    CC --> RP
    RP -->|"RespType.Array"| CR
    CR -->|"dispatch"| DB
    CR -->|"MULTI/EXEC"| TX
    CR -->|"SUBSCRIBE/PUBLISH"| PS
```

## Command Dispatch Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant S as RedisServer
    participant P as RespParser
    participant R as CommandRegistry
    participant H as CommandHandler
    participant D as Database

    C->>S: TCP bytes (RESP encoded)
    S->>P: parse()
    P-->>S: RespType.Array
    S->>S: Check MULTI state
    alt In MULTI transaction
        S->>S: Queue command (return +QUEUED)
    else Normal execution
        S->>R: dispatch(args, client)
        R->>H: handle(args, client)
        H->>D: read/write operations
        D-->>H: result
        H-->>R: RespType response
        R-->>S: response
    end
    S->>C: RespCodec.encode(response)
```

## Data Storage Model

Each of the 16 databases uses `ConcurrentHashMap` for thread-safe key-value storage:

```mermaid
graph LR
    subgraph Database
        DM["data<br/>Map&lt;String, Object&gt;"]
        TM["types<br/>Map&lt;String, DataType&gt;"]
        KE["KeyExpiration<br/>Map&lt;String, Long&gt;<br/>(absolute ms timestamps)"]
        SS["streams<br/>Map&lt;String, StreamStore&gt;"]
        ZS["zsetScores<br/>Map&lt;String, Map&lt;String, Double&gt;&gt;"]
    end

    DM -->|"STRING"| BA["byte[]"]
    DM -->|"LIST"| DQ["ConcurrentLinkedDeque&lt;byte[]&gt;"]
    DM -->|"SET"| HS["ConcurrentHashMap.newKeySet()"]
    DM -->|"ZSET"| TM2["TreeMap&lt;Double, Set&lt;String&gt;&gt;"]
    DM -->|"HASH"| HM["ConcurrentHashMap&lt;String, byte[]&gt;"]
    DM -->|"HYPERLOGLOG"| HL["HyperLogLog<br/>(16384 byte registers)"]
    SS -->|"STREAM"| ST["ConcurrentSkipListMap<br/>(entry ID -> fields)"]
```

## Key Expiration

Two-tier expiration strategy:

```mermaid
graph TD
    subgraph Lazy["Lazy Expiration (on access)"]
        A["Database.exists(key)"] --> B["expiration.isExpired(key)?"]
        B -->|"yes"| C["delete(key)"]
        B -->|"no"| D["return value"]
    end

    subgraph Active["Active Expiration (periodic sweep)"]
        E["Database.size() / keys() / scan() / randomKey()"]
        E --> F["expireActiveKeys()"]
        F --> G["getExpiredKeys() scan all entries"]
        G --> H["delete each expired key"]
    end
```

## Transaction Model (MULTI/EXEC with WATCH)

```mermaid
sequenceDiagram
    participant C1 as Client 1
    participant C2 as Client 2
    participant TX as TransactionExecutor
    participant DB as Database

    C1->>TX: WATCH key (record version=V)
    C1->>TX: MULTI (begin queuing)
    C1->>TX: SET key newval (queued)
    C2->>DB: SET key otherval
    C2->>TX: touchKey(key) (version=V+1)
    C1->>TX: EXEC
    TX->>TX: validateWatch() -> V != V+1
    TX-->>C1: null array (transaction aborted)
```

## Pub/Sub Architecture

```mermaid
graph TD
    PUB["Publisher<br/>PUBLISH channel message"]
    PSM["PubSubManager"]
    CH["channelSubscribers<br/>Map&lt;String, Set&lt;ClientConnection&gt;&gt;"]
    PA["patternSubscribers<br/>Map&lt;String, Set&lt;PatternSubscription&gt;&gt;"]
    S1["Subscriber 1<br/>(exact channel match)"]
    S2["Subscriber 2<br/>(glob pattern match)"]

    PUB --> PSM
    PSM --> CH --> S1
    PSM --> PA --> S2
```

## Stream Store Architecture

```mermaid
graph TD
    XA["XADD<br/>(auto-generated or explicit ID)"]
    SS["StreamStore<br/>ConcurrentSkipListMap&lt;String, Map&lt;String, String&gt;&gt;"]
    CG["ConsumerGroup<br/>(name, lastDeliveredId,<br/>pending entries, consumers)"]
    PE["PendingEntry<br/>(id, consumer, deliveryTime,<br/>deliveryCount)"]

    XA --> SS
    SS --> CG
    CG --> PE
    CG -->|"XREADGROUP >"| SS
    PE -->|"XACK"| CG
    PE -->|"XCLAIM"| CG
```

## Cluster Support

The module provides client-side cluster protocol support:

```mermaid
graph TD
    CC["ClusterClient"]
    HS["HashSlot<br/>CRC16-CCITT mod 16384<br/>+ hash tag extraction"]
    SM["slotMap<br/>Map&lt;Integer, String&gt;"]
    CI["ClusterInfo<br/>Node topology, SlotRange"]
    RD["Redirect<br/>MOVED / ASK parsing"]

    CC -->|"key -> slot"| HS
    CC -->|"slot -> node"| SM
    CC -->|"topology"| CI
    CC -->|"error handling"| RD
    RD -->|"MOVED: update slot map"| SM
    RD -->|"ASK: send ASKING + retry"| CC
```

## Authentication (AUTH)

```mermaid
sequenceDiagram
    participant C as Client
    participant S as RedisServer
    participant CC as ClientConnection

    C->>S: SET key value
    S->>CC: isAuthenticated()?
    CC-->>S: false (password is set)
    S-->>C: -NOAUTH Authentication required.

    C->>S: AUTH correctpassword
    S->>S: Validate password
    S->>CC: setAuthenticated(true)
    S-->>C: +OK

    C->>S: SET key value
    S->>CC: isAuthenticated()?
    CC-->>S: true
    S->>S: Execute normally
    S-->>C: +OK
```

- `RedisServer(String password)` constructor enables authentication
- `ClientConnection.authenticated` tracks per-client auth state
- AUTH, PING, and QUIT bypass the auth check (AUTH_BYPASS set)
- When no password is configured, AUTH returns error "no password is set"

## HyperLogLog Architecture

```mermaid
graph TD
    PA["PFADD key elem..."]
    PC["PFCOUNT key..."]
    PM["PFMERGE dest src..."]
    HLL["HyperLogLog<br/>16384 byte registers"]
    MH["MurmurHash3-style<br/>64-bit hash"]
    EST["Estimation<br/>alpha * m^2 / sum(2^-reg[i])<br/>+ linear counting correction"]

    PA -->|"hash element"| MH
    MH -->|"index = hash >> 50<br/>rank = leading zeros"| HLL
    PC --> HLL
    HLL --> EST
    PM -->|"max(reg[i])"| HLL
```

- Uses 2^14 = 16384 registers matching Redis standard
- MurmurHash3-style 64-bit hash for element distribution
- Harmonic mean formula with alpha bias correction
- Small range correction (linear counting) when many registers are zero
- Standard error ~0.81% for typical cardinalities

## Geospatial Architecture

```mermaid
graph TD
    GA["GEOADD key lon lat member"]
    GD["GEODIST key m1 m2 unit"]
    GP["GEOPOS key member"]
    GS["GEOSEARCH key ... BYRADIUS"]
    GH["Geohash Encoder<br/>52-bit interleaved<br/>(26 lon + 26 lat)"]
    ZS["Sorted Set<br/>(score = geohash)"]
    HV["Haversine Formula<br/>distance on sphere"]

    GA -->|"encode(lon, lat)"| GH
    GH -->|"score"| ZS
    GD --> ZS
    ZS -->|"decode scores"| HV
    GP --> ZS
    GP -->|"decode(score)"| GH
    GS --> ZS
    GS -->|"filter by distance"| HV
```

- Geo data stored in standard sorted sets with geohash as score (matching Redis approach)
- 52-bit geohash: 26 bits longitude (-180 to 180), 26 bits latitude (-90 to 90), interleaved
- Haversine formula for great-circle distance (Earth radius = 6,372,797.56 m)
- GEOSEARCH iterates all members and filters by Haversine distance
- Distance units: meters (m), kilometers (km), miles (mi), feet (ft)

## Thread Safety Model

- **Server**: `AtomicBoolean` for running state; `ConcurrentHashMap.newKeySet()` for client tracking
- **Database**: all internal maps are `ConcurrentHashMap`; sorted sets use `Collections.synchronizedNavigableMap`
- **ClientConnection**: output writes synchronized on the `OutputStream` object
- **KeyExpiration**: `ConcurrentHashMap<String, Long>` for atomic read/write of timestamps
- **PubSubManager**: `ConcurrentHashMap` for subscribers; `CopyOnWriteArraySet` for per-channel client sets
- **StreamStore**: `ConcurrentSkipListMap` for ordered entries; `AtomicLong` for ID generation
- **TransactionExecutor**: `ConcurrentHashMap` for global key versions; per-client `TransactionState` is single-threaded

## Extension Points

- **CommandHandler**: functional interface -- register custom commands via `CommandRegistry.register(name, handler)`
- **CommandRegistry**: add new command categories by following the static `register()` pattern
- **Database**: data type storage is extensible via the `DataType` enum and `ConcurrentHashMap<String, Object>` backing store

---

**Last Updated**: 2026-07-06
