# AMQP Integration Test Flakiness Investigation

## Symptom
`AmqpServiceIntegrationTest.testMultipleClients()` and `testFullMessagingPipeline()` fail intermittently with:
```
java.io.IOException: Invalid header from server: [0, 0, 0, 28, 2, 1, 0, 0]
```

## Root Cause Analysis
The bytes `[0, 0, 0, 28, 2, 1, 0, 0]` decode as:
- Length: 28 (big-endian)
- Type: 2 (FRAME)
- Channel: 1

This is an AMQP **frame header**, not a protocol header. The client is reading 7 bytes of a SASL frame payload as the 8-byte protocol header.

## Why It Happens
1. `PipelineByteTransport.receiveWithTimeout()` can return partial data when timeout expires
2. `AmqpClient.readFully()` assumes `receive()` always fills the buffer
3. When 3 clients connect simultaneously, the shared `SelectableChannelManager` selector can interleave reads
4. The client reads 7 bytes instead of 8, corrupting the protocol state

## Investigation History
- Tested with `git stash` to confirm flakiness exists on clean HEAD
- Confirmed: pre-existing issue, not caused by recent changes
- Root cause: race condition in test infrastructure (shared selector + async connections)

## Current State
- Single-client tests pass reliably
- Multi-client tests are inherently flaky due to concurrent connection handling
- Test currently uses `Thread.sleep(100)` between connections (not reliable)

## Potential Fixes (not yet implemented)
1. **Serialize connections**: One client at a time, wait for `isConnected()` before next
2. **Fix PipelineByteTransport**: Ensure `receiveWithTimeout()` blocks until buffer is full or timeout
3. **Fix AmqpClient.readFully()**: Loop until buffer is full, handling partial reads
4. **Use separate selectors**: Each test gets its own `SelectableChannelManager`

## Verification Commands
```bash
# Maven single test
mvn test -pl messaging/amqp -Dtest="AmqpServiceIntegrationTest#testMultipleClients" -am -Dsurefire.failIfNoSpecifiedTests=false

# Maven all AMQP tests
mvn test -pl messaging/amqp -am -DskipInteropTests=true

# Gradle full suite
./gradlew test -PskipInteropTests=true --rerun-tasks

# Repeat 10x to check flakiness
for i in 1 2 3 4 5 6 7 8 9 10; do mvn test -pl messaging/amqp -Dtest="AmqpServiceIntegrationTest#testMultipleClients" -am -Dsurefire.failIfNoSpecifiedTests=false -q 2>&1 | grep "Tests run" || echo "FAIL"; done
```

## Related Tests
- `testClientConnectsToServer()` - single client, passes reliably
- `testFullMessagingPipeline()` - single client, flaky in Gradle CI
- `testMultipleClients()` - 3 clients, flaky in both Maven and Gradle

## Other Flaky Tests Found
- `Stomp InMemoryTransportTest.testMultipleMessages()` - fixed with TCP semantics
- `DnsServerIntegrationTest.testServerQueryCounters()` - fixed with async wait
- `SmtpServerLifecycleTest.testHandlerAuthenticate()` - pre-existing flaky

## Key Files
- `messaging/amqp/src/test/java/ssg/legoflow/messaging/amqp/service/AmqpServiceIntegrationTest.java`
- `service/src/main/java/ssg/legoflow/service/manager/SelectableChannelManager.java`
- `service/src/main/java/ssg/legoflow/service/channel/PipelineByteTransport.java`
- `messaging/amqp/src/main/java/ssg/legoflow/messaging/amqp/client/AmqpClient.java`
