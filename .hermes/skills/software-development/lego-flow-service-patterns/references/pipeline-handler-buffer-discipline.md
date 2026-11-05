# Pipeline Handler Buffer Discipline

## The ByteBuffer position problem
`ChannelPipeline.fireRead()` passes the **same** `ByteBuffer` instance to every handler in the
pipeline. When a handler reads from it (`data.get(bytes)`), it advances the position. Subsequent
handlers see only the remaining bytes — often zero.

## Fix: Recording/monitoring handlers must use `duplicate()`
Any handler that inspects or records data without consuming it MUST use `data.duplicate()`:
```java
// WRONG — consumes buffer, downstream handlers see empty
data.get(bytes);

// CORRECT — duplicate shares the backing array but has independent position
var dup = data.duplicate();
dup.get(bytes);  // original buffer position untouched
```

## Real example: ChannelManagerDemo.RecordingHandler
The `RecordingHandler` in the demo consumed the buffer in `onRead()`, causing
`testMultipleHandlersChainedInPipeline()` to fail — the second handler saw `READ:` instead
of `READ:data`. Fixed by switching to `data.duplicate()`.

## When NOT to use duplicate()
In real protocol handlers, you WANT to consume the buffer (frame decoder advances position,
next handler processes what's left). Only use `duplicate()` for:
- Monitoring/logging handlers
- Recording handlers (test fixtures, demo helpers)
- Branching logic that needs to re-read the same bytes
