# STOMP Compliance Report

## Specifications Covered
- STOMP Protocol Specification, Version 1.2 -- stomp.github.io (June 2012)

## Compliance Matrix

### STOMP 1.2 -- Client Commands

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| STOMP | STOMP command (v1.2 alternative to CONNECT) | ✅ Implemented | `StompCommand.STOMP`; `StompCommandTest`, `StompBrokerTest` |
| CONNECT | Establish connection with accept-version, host | ✅ Implemented | `StompClient.connect()`; `StompClientTest`, `StompBrokerTest` |
| SEND | Send message to destination | ✅ Implemented | `StompClient.send()`; `StompClientTest`, `StompBrokerTest` |
| SUBSCRIBE | Subscribe to destination with id and ack mode | ✅ Implemented | `StompClient.subscribe()`; `StompClientTest`, `StompBrokerTest` |
| UNSUBSCRIBE | Unsubscribe by subscription id | ✅ Implemented | `StompClient.unsubscribe()`; `StompClientTest`, `StompBrokerTest` |
| ACK | Acknowledge message consumption | ✅ Implemented | `StompClient.ack()`; `StompClientTest`, `StompBrokerTest` |
| NACK | Negative-acknowledge message | ✅ Implemented | `StompClient.nack()`; `StompBrokerTest` |
| BEGIN | Begin transaction | ✅ Implemented | `StompClient.begin()`; `StompClientTest`, `StompBrokerTest` |
| COMMIT | Commit transaction | ✅ Implemented | `StompClient.commit()`; `StompClientTest`, `StompBrokerTest` |
| ABORT | Abort transaction | ✅ Implemented | `StompClient.abort()`; `StompClientTest`, `StompBrokerTest` |
| DISCONNECT | Graceful disconnect with optional receipt | ✅ Implemented | `StompClient.disconnect()`; `StompClientTest` |

### STOMP 1.2 -- Server Frames

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| CONNECTED | Connection confirmation with version, server, session | ✅ Implemented | `StompBroker.handleConnect()`; `StompBrokerTest`, `StompClientTest` |
| MESSAGE | Deliver message with destination, message-id, subscription | ✅ Implemented | `StompBroker.deliverMessage()`; `StompBrokerTest`, `SimplePubSubDemoTest` |
| RECEIPT | Confirm frame receipt with receipt-id | ✅ Implemented | `StompBroker.sendReceipt()`; `StompBrokerTest` |
| ERROR | Error notification with message header | ✅ Implemented | `StompBroker.sendError()`; `StompBrokerTest` |

### STOMP 1.2 -- Frame Format

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| Frame structure | COMMAND + headers + blank line + body + NULL | ✅ Implemented | `StompCodec.encode()`/`decode()`; `StompCodecTest` (43 tests) |
| Header escaping | \n, \\, \c, \r per spec | ✅ Implemented | `StompCodec.escapeHeaderValue()`/`unescapeHeaderValue()`; `StompCodecTest` |
| Repeated headers | First occurrence wins | ✅ Implemented | `StompHeaders.putIfAbsent()`, `StompCodec.decode()`; `StompCodecTest` |
| Case-sensitive headers | Header names are case-sensitive | ✅ Implemented | `StompHeaders` (LinkedHashMap); `StompHeadersTest` |
| content-length | Binary body support via content-length header | ✅ Implemented | `StompCodec.decode()`; `StompCodecTest` |
| content-type | MIME type header support | ✅ Implemented | `StompHeaders.CONTENT_TYPE`; `StompCodecTest` |
| NULL terminator | Frame terminated by NULL byte (\0) | ✅ Implemented | `StompCodec.encode()`; `StompCodecTest` |
| EOL variants | Support both LF and CR+LF | ✅ Implemented | `StompCodec.decode()`; `StompCodecTest` |
| Heart-beat frame | Empty EOL frame (not a real command) | ✅ Implemented | `StompFrame.heartbeat()`, `StompCodec`; `StompCodecTest`, `StompFrameTest` |

### STOMP 1.2 -- Version Negotiation

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| accept-version | Client sends supported versions | ✅ Implemented | `StompClient.connect()` sends `1.0,1.1,1.2`; `StompClientTest` |
| Version selection | Server picks highest mutual version | ✅ Implemented | `StompBroker.negotiateVersion()`; `StompBrokerTest` |
| Version mismatch | ERROR if no mutual version | ✅ Implemented | `StompBroker.handleConnect()`; `StompBrokerTest` |
| No accept-version | Implies STOMP 1.0 | ✅ Implemented | `StompBroker.negotiateVersion()` returns "1.0"; `StompBrokerTest` |

### STOMP 1.2 -- Heart-Beats

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| Heart-beat header | cx,cy format in CONNECT/CONNECTED | ✅ Implemented | `HeartbeatMonitor.parseHeartbeat()`/`formatHeartbeat()`; `HeartbeatMonitorTest` |
| Negotiation | MAX(client-cx, server-cy) / MAX(server-cx, client-cy) | ✅ Implemented | `HeartbeatMonitor.negotiate()`; `HeartbeatMonitorTest` |
| Disable heart-beat | 0 means cannot/does not want | ✅ Implemented | `HeartbeatMonitor.negotiate()` returns 0 if either is 0; `HeartbeatMonitorTest` |
| Send timing | Heart-beat sent when interval exceeded | ✅ Implemented | `HeartbeatMonitor.shouldSendHeartbeat()`; `HeartbeatMonitorTest` |
| Receive timeout | Detect remote death (2x tolerance) | ✅ Implemented | `HeartbeatMonitor.isReceiveTimedOut()`; `HeartbeatMonitorTest` |

### STOMP 1.2 -- Acknowledgment Modes

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| auto | No ACK required, immediate acknowledgment | ✅ Implemented | `StompBroker.handleSubscribe()` default mode; `StompBrokerTest` |
| client | Cumulative ACK: ack(N) acknowledges all up to N | ✅ Implemented | `StompBroker.processAck()`; `StompBrokerTest` |
| client-individual | Per-message ACK: each must be individually acknowledged | ✅ Implemented | `StompBroker.processAck()`; `StompBrokerTest` |
| NACK | Negative acknowledgment (message not consumed) | ✅ Implemented | `StompBroker.processNack()`; `StompBrokerTest` |
| ACK in transaction | ACK/NACK buffered in transaction | ✅ Implemented | `StompBroker.handleAck()`/`handleNack()`; `StompBrokerTest` |

### STOMP 1.2 -- Transactions

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| BEGIN | Start named transaction | ✅ Implemented | `StompBroker.handleBegin()`; `StompBrokerTest`, `StompTransactionTest` |
| COMMIT | Apply all buffered frames atomically | ✅ Implemented | `StompBroker.handleCommit()`; `StompBrokerTest`, `TransactionalDemoTest` |
| ABORT | Discard all buffered frames | ✅ Implemented | `StompBroker.handleAbort()`; `StompBrokerTest`, `TransactionalDemoTest` |
| Buffer SEND | SEND within transaction is buffered | ✅ Implemented | `StompTransaction.buffer()`; `StompTransactionTest` |
| Buffer ACK/NACK | ACK/NACK within transaction is buffered | ✅ Implemented | `StompTransaction.buffer()`; `StompTransactionTest` |
| Duplicate BEGIN | ERROR if transaction already active | ✅ Implemented | `StompBroker.handleBegin()`; `StompBrokerTest` |
| Invalid COMMIT/ABORT | ERROR if transaction not found | ✅ Implemented | `StompBroker.handleCommit()`/`handleAbort()`; `StompBrokerTest` |

### STOMP 1.2 -- Receipts

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| Receipt request | Any client frame with receipt header | ✅ Implemented | `StompBroker.sendReceipt()`; `StompBrokerTest` |
| RECEIPT frame | Server sends receipt-id matching request | ✅ Implemented | `StompBroker.sendReceipt()`; `StompBrokerTest` |
| Disconnect receipt | RECEIPT for DISCONNECT frame | ✅ Implemented | `StompBroker.handleDisconnect()`; `StompBrokerTest` |
| Client receipt tracking | CompletableFuture for receipt confirmation | ✅ Implemented | `StompClient.sendWithReceipt()`; `StompClientTest` |

### STOMP 1.2 -- Required Headers

| Section | Requirement | Status | Verification |
|---------|------------|--------|-------------|
| CONNECT: accept-version | Required | ✅ Implemented | `StompClient.connect()`; `StompClientTest` |
| CONNECT: host | Required | ✅ Implemented | `StompClient.connect()`; `StompClientTest` |
| SEND: destination | Required | ✅ Implemented | `StompBroker.handleSend()` validates; `StompBrokerTest` |
| SUBSCRIBE: id | Required | ✅ Implemented | `StompBroker.handleSubscribe()` validates; `StompBrokerTest` |
| SUBSCRIBE: destination | Required | ✅ Implemented | `StompBroker.handleSubscribe()` validates; `StompBrokerTest` |
| UNSUBSCRIBE: id | Required | ✅ Implemented | `StompBroker.handleUnsubscribe()` validates; `StompBrokerTest` |
| ACK: id | Required | ✅ Implemented | `StompBroker.handleAck()` validates; `StompBrokerTest` |
| NACK: id | Required | ✅ Implemented | `StompBroker.handleNack()` validates; `StompBrokerTest` |
| BEGIN: transaction | Required | ✅ Implemented | `StompBroker.handleBegin()` validates; `StompBrokerTest` |
| COMMIT: transaction | Required | ✅ Implemented | `StompBroker.handleCommit()` validates; `StompBrokerTest` |
| ABORT: transaction | Required | ✅ Implemented | `StompBroker.handleAbort()` validates; `StompBrokerTest` |
| MESSAGE: destination | Required | ✅ Implemented | `StompBroker.deliverMessage()`; `StompBrokerTest` |
| MESSAGE: message-id | Required | ✅ Implemented | `StompBroker.deliverMessage()`; `StompBrokerTest` |
| MESSAGE: subscription | Required | ✅ Implemented | `StompBroker.deliverMessage()`; `StompBrokerTest` |
| CONNECTED: version | Required | ✅ Implemented | `StompBroker.handleConnect()`; `StompBrokerTest` |
| RECEIPT: receipt-id | Required | ✅ Implemented | `StompBroker.sendReceipt()`; `StompBrokerTest` |

### Transport Support

| Requirement | Status | Verification |
|------------|--------|-------------|
| Raw TCP transport | ✅ Implemented | `TcpStompTransport`, `TcpStompServer`, `TcpStompClient`; `TcpStompAdapterTest` |
| WebSocket transport (v12.stomp subprotocol) | ✅ Implemented | `WebSocketStompTransport`, `WebSocketStompHandler` |
| In-memory transport (testing) | ✅ Implemented | `InMemoryStompTransport`; used in all core/demo tests |

## Known Limitations

- No destination wildcards or pattern matching (e.g., `/topic/**`)
- No authentication framework (login/passcode accepted but not validated against a store)
- No ACL / authorization for destination-level access control
- No message persistence or durable subscriptions
- No message selectors (SQL-based header filtering)
- No broker-to-broker clustering or bridging
- No message priority support
- No maximum message size enforcement
- WebSocket adapter depends on lego-flow-http module at runtime

## Test Coverage Summary

- Total tests: 157
- Key unit test classes: `StompCodecTest` (43), `StompHeadersTest` (14), `StompFrameTest` (10), `StompCommandTest` (5), `StompSessionTest` (10), `StompTransactionTest` (9), `HeartbeatMonitorTest` (17), `StompBrokerTest` (20), `StompClientTest` (12), `TcpStompAdapterTest` (8)
- Key demo test classes: `SimplePubSubDemoTest`, `RequestReplyDemoTest`, `TransactionalDemoTest` (9 total)
- Sections fully covered: all 16 commands (codec), frame format, header escaping, version negotiation, heart-beats, all 3 ack modes, transactions, receipts, TCP transport
