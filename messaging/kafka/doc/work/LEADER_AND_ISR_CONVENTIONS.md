# LeaderAndIsr Codec Conventions

## Spec Source
- Vendor JSON: `doc/spec/message/LeaderAndIsrRequest.json`, `LeaderAndIsrResponse.json`
- Frozen order: `doc/spec/order/LeaderAndIsr.{Request,Response}.txt`
- Layout: `doc/spec/LAYOUTS.md` lines 1318-1417

## Version Summary
| Version | Key Changes |
|---------|-------------|
| v0      | Base: flat `UngroupedPartitionStates`, no broker epoch |
| v1      | + `IsNew` on partition state |
| v2      | + `BrokerEpoch`, partitions grouped by `TopicStates` |
| v3      | + `AddingReplicas`, `RemovingReplicas` |
| v4      | First flexible (KIP-482) |
| v5      | + `Type`, `TopicId` on topics; response restructured to `Topics` |
| v6      | + `LeaderRecoveryState` |
| v7      | + `isKRaftController` |

## Field Processing Order (Request)
```
v0-v1: ControllerId(int32), ControllerEpoch(int32), UngroupedPartitionStates[], LiveLeaders[]
v2-v4: ControllerId(int32), ControllerEpoch(int32), BrokerEpoch(int64, v2+), TopicStates[], LiveLeaders[]
v5-v6: ControllerId(int32), ControllerEpoch(int32), BrokerEpoch(int64), Type(int8, v5+), TopicStates[], LiveLeaders[]
v7:    ControllerId(int32), isKRaftController(bool, v7+), ControllerEpoch(int32), BrokerEpoch(int64), Type(int8), TopicStates[], LiveLeaders[]
```

## Field Processing Order (Response)
```
v0-v4: ErrorCode(int16), PartitionErrors[]
v5-v7: ErrorCode(int16), Topics[]
```

## PartitionState Fields (common struct)
```
v0-v1: TopicName(string, nullable), PartitionIndex(int32), ControllerEpoch(int32),
       Leader(int32), LeaderEpoch(int32), Isr(int32[]), PartitionEpoch(int32), Replicas(int32[])
v1+:   + IsNew(bool)
v3+:   + AddingReplicas(int32, nullable), RemovingReplicas(int32, nullable)
v6+:   + LeaderRecoveryState(int8)
```

## PartitionError Fields (common struct)
```
v0-v4: TopicName(string, nullable), PartitionIndex(int32), ErrorCode(int16)
v5+:   TopicName(null), PartitionIndex(int32), ErrorCode(int16)
```

## Flexible Encoding (v4+)
- Strings: KIP-482 compact encoding (varint length prefix, UTF-8 bytes)
- Arrays: N+1 varint count (0 = empty, -1 represented as 0 in compact form)
- EndTags: 4-byte tag count (0) followed by 0 tags at end of each variable-length field

## UUID Encoding
- 16 bytes, big-endian
- Null = 16 zero bytes
- Write: `writeUuid(buf, uuid)` handles null (16 zeros) and non-null

## Nullable Int Array
- Non-flexible: int32 count, -1 = null
- Flexible: varint count, 0 = empty/null (but we write as 0 for empty)

## Java Record Model
```java
public record LeaderAndIsrRequest(
    int controllerId,
    int controllerEpoch,
    long brokerEpoch,           // -1 if absent (v2+)
    boolean isKRaftController,  // false (v7+)
    int type,                   // 0=all topics (v5+)
    List<PartitionState> partitionStates,   // v0-v1 only
    List<TopicState> topicStates,           // v2+
    List<LiveLeader> liveLeaders
)

public record PartitionState(
    String topic,              // null v2+
    int partition,
    int leader,
    int leaderEpoch,
    List<Integer> isr,
    int partitionEpoch,
    List<Integer> replicas,
    boolean isNew,             // false v0
    List<Integer> addingReplicas,      // empty v0-v2
    List<Integer> removingReplicas,    // empty v0-v2
    int leaderRecoveryState    // 0 v0-v5
)

public record TopicState(
    String topicName,
    UUID topicId,              // null v0-v4
    List<PartitionState> partitionStates
)

public record LiveLeader(
    int brokerId,
    String hostName,
    int port
)
```

## Response Model
```java
public record LeaderAndIsrResponse(
    short errorCode,
    List<PartitionError> partitionErrors,  // v0-v4
    List<TopicError> topics                // v5+
)

public record PartitionError(
    String topicName,    // null v5+
    int partitionIndex,
    short errorCode
)

public record TopicError(
    UUID topicId,
    List<PartitionError> partitionErrors
)
```

## Facade Pattern
- `KafkaCodec` delegates to `LeaderAndIsrCodec` with version parameter
- Facade methods: `encodeLeaderAndIsrRequest(short, LeaderAndIsrRequest)`, etc.
- Pinned version: `LeaderAndIsrCodec.PINNED_VERSION = 0`

## Broker Dispatch
- `KafkaBroker.handleLeaderAndIsr(ByteBuffer, short version)`
- Uses v0 semantics (flat partition states) for backward compatibility
- Response uses `PartitionError` for v0, `TopicError` for v5+

## Test Pattern (from MetadataCodecTest)
1. Round-trip encode/decode at v0, v4, v5, v7
2. Golden byte-for-byte comparison at v0 (from Python reference encoder)
3. Version constraints (min/max bounds)
4. Edge cases: null topics, empty arrays, all-topics marker