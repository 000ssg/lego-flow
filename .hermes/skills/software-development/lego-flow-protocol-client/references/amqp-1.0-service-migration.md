# AMQP 1.0 Service Migration Lessons

## Integer Overflow on Unsigned 32-bit Defaults

AMQP 1.0 spec uses `uint32` for `max-frame-size` (up to 4,294,967,295).
Java `int` max is 2,147,483,647. A literal `4294967295` causes
`error: integer number too large` at compile time.

**Fix:** Use `Integer.MAX_VALUE` as the default in service builder fields:
```java
private int maxFrameSize = Integer.MAX_VALUE;  // NOT 4294967295
```

## Builder Signature Drift After Refactoring

When migrating from pipeline-era DP/DF services to the delegate pattern
(service → blocking protocol client), builder signatures change. Tests that
were written against the old API will fail to compile:

- `AmqpContainerService.builder(port)` → `AmqpContainerService.builder().port(port)`
- `AmqpClientService.getAmqpContext()` → `AmqpClientService.getClient()`
- Tests referencing `AmqpClientProtocol` (non-existent) → use `AmqpClient` directly

**Rule:** Always run `compileJava` AND `compileTestJava` immediately after service
refactoring. Fix tests before moving to the next phase. Never assume tests
from a prior commit still match the current API.

## Service Test Patterns Post-Migration

After migration to delegate pattern, service tests should verify:

1. `builder().<args>().build()` creates a service
2. `isConnected()` is false before connect
3. `disconnect()` before connect doesn't throw
4. `getClient()`/`getContainer()` is null before connect
5. Builder with custom priority/name/dependencies works
