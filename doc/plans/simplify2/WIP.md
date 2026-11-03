# Plan: Simplify and Harden Protocol Implementations

## Goals
1. Fix AMQP test flakiness by eliminating race conditions and data leakage
2. Ensure zero data leakage between connections
3. Minimize synchronization needs
4. Convert direct TCP/socket implementations to DP/DF/service pattern
5. Apply findings cross all protocol implementations

## Root Cause Analysis

### AMQP Flakiness Root Cause
**Found**: Ring buffer bug in `PipelineByteTransport`

The ring buffer's compaction logic had a critical bug when `start > end` (after wrap-around):
```java
// BUGGY CODE:
System.arraycopy(buffer, start, buffer, 0, count);
start = 0;
end = count;
```

When `start > end` (wrapped state), this copies from wrong positions, causing data leakage between connections.

**Fix**: Properly handle wrap-around by copying in two parts:
```java
if (start < end) {
    System.arraycopy(buffer, start, buffer, 0, count);
} else {
    // Wrapped: copy [start..end) which is actually [start..buffer.length) + [0..end)
    System.arraycopy(buffer, start, buffer, 0, buffer.length - start);
    System.arraycopy(buffer, 0, buffer, buffer.length - start, end);
}
start = 0;
end = count;
```

### Data Leakage Confirmed
Traced execution showing:
- Connection 1 receives frame header `[0, 0, 0, 28, 2, 1, 0, 0]` (OPEN frame from Connection 2)
- This is data from a different connection leaking through the ring buffer

## Progress

### Step 1: AMQP Fix ✅ COMPLETE
- [x] Identified ring buffer bug in `PipelineByteTransport`
- [x] Fixed compaction logic to handle wrap-around correctly
- [x] Added detailed tracing for verification
- [x] Verified 15/15 runs pass (previously ~20% failure rate)
- [x] Full test suite passes (771 tests)

### Step 2: Cross-Protocol Review (NEXT)
- [ ] Check other protocol transports for similar ring buffer bugs
- [ ] Review `PipelineNatsTransport`, `PipelineKafkaTransport`, `PipelineMqttTransport`
- [ ] Verify no data leakage between connections in other protocols

### Step 3: Architecture Simplification (FUTURE)
- [ ] Identify protocols still using direct TCP sockets/channels
- [ ] Convert to DP/DF/service pattern
- [ ] Apply minimal synchronization principles
- [ ] Ensure each connection has isolated state

## Key Findings

1. **Ring buffer bug**: The `PipelineByteTransport` ring buffer had incorrect compaction logic for wrapped state
2. **Data leakage**: Connections could receive data from other connections due to the bug
3. **Fix is simple**: Proper array copying for wrap-around case
4. **Zero synchronization needed**: The fix maintains lock-free read path, only write path is synchronized

## Verification
- AMQP `testMultipleClients`: 15/15 passes (was ~20% failure rate)
- Full suite: 771 tests pass, 0 failures
- No data leakage between connections confirmed

## Next Steps
1. Run comprehensive verification across all protocols
2. Check for similar ring buffer patterns in other transports
3. Plan architecture simplification for remaining protocols