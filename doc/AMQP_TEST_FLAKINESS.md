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
- AMQP test uses polling with `isConnected()` check before each client connection
- XMPP test fixed with 50ms delays between connections
- STOMP test fixed with 50ms delays between connections

## Key Files
- `messaging/amqp/src/test/java/ssg/legoflow/messaging/amqp/service/AmqpServiceIntegrationTest.java`
- `messaging/xmpp/src/test/java/ssg/legoflow/xmpp/service/XmppServiceIntegrationTest.java`
- `messaging/stomp/src/test/java/ssg/legoflow/messaging/stomp/server/service/StompServerServiceTest.java`
- `service/src/main/java/ssg/legoflow/service/manager/SelectableChannelManager.java`
- `service/src/main/java/ssg/legoflow/service/channel/PipelineByteTransport.java`
- `messaging/amqp/src/main/java/ssg/legoflow/messaging/amqp/client/AmqpClient.java`
