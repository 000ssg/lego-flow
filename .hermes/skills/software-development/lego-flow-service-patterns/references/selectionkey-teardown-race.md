# SelectionKey Teardown Race

**Date discovered**: 2026-10-27
**Related test**: `TcpDataFlowTest`

## Problem

`TcpDataFlowTest` failed intermittently (flaky) on CI with `ClosedChannelException` thrown from `TestHandler.onWrite()` at line 173:
```java
key.interestOps(SelectionKey.OP_READ);
```

The test passed locally but failed ~30% of the time on CI (Ubuntu, macOS, Windows).

## Root Cause

Race condition between selector dispatch and teardown:

1. Server sends payload, client receives data
2. Client's `manager.close()` is called in `finally` block
3. `manager.close()` calls `stopEventLoop()` which sets `running=false` and wakes selector
4. Selector thread may be in the middle of dispatching `dispatchKey()` for an OP_WRITE event
5. `dispatchKey()` submits `processWritable()` to processing pool
6. Processing pool thread calls `pipeline.fireWrite(channel)` → `handler.onWrite(channel)`
7. Handler calls `key.interestOps(SelectionKey.OP_READ)`
8. But the key was already cancelled by `manager.close()` → `ClosedChannelException`
9. `ChannelPipeline.fireWrite()` catches the exception and calls `fireError()`
10. Test's `assertThat(handler.events).noneMatch(e -> e.startsWith("ERROR"))` fails

## Why Local vs CI

- Locally: fewer modules testing simultaneously, less CPU contention, slower teardown relative to selector dispatch
- CI: 40+ modules testing in parallel, CPU saturated, teardown races more frequently

## Fix

Wrap `SelectionKey` operations in try/catch during teardown:

```java
@Override
public void onWrite(DataChannel channel) {
    var key = channel.getSelectionKey();
    if (key != null) {
        try {
            key.interestOps(SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            // Key may be cancelled during teardown — ignore
        }
    }
}
```

## Key Insight

**Do not add timeouts to fix teardown races.** The race is between the selector thread and `close()`, not a timing issue. Adding `Thread.sleep()` or increasing timeouts does not help because:
- The key CAN be cancelled concurrently with handler dispatch
- The handler is responding to a legitimate OP_WRITE event that was already dispatched
- The exception is expected during teardown, not an error condition

## Related Patterns

- `SelectableChannelManager.stopEventLoop()` sets `running=false` then wakes selector
- `TcpDataChannel.close()` cancels the selection key
- `ChannelPipeline.fireWrite()` catches exceptions and calls `fireError()`
- Test handlers should be defensive against concurrent teardown

## Verification

After fix:
- Maven: 1449 tests, 0 failures, 0 errors, 83 skipped
- Gradle: 1449 tests, 0 failures, 0 errors, 83 skipped
- Both build systems produce identical results
- Test passes consistently (50/50 runs on both Maven and Gradle)
