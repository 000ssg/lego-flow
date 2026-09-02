# Phase 5: Unit Test Matrix
**Status:** ☐ Not started
**Started:** —
**Completed:** —

## Goal
Exhaustive unit tests for legoflow server with every client variant.

## Test Matrix (own server × clients)

| Server Mode | Client | Tests |
|------------|--------|-------|
| STANDARD | legoflow AmqpClient | Full suite |
| STANDARD | rhea.js | Full suite |
| RABBITMQ | legoflow AmqpClient (RABBITMQ mode) | Full suite |
| RABBITMQ | rhea.js | Full suite |
| ARTEMIS | legoflow AmqpClient (ARTEMIS mode) | Full suite |
| ARTEMIS | rhea.js | Full suite |
| QPID_DISPATCH | legoflow AmqpClient (QPID mode) | Full suite |
| QPID_DISPATCH | rhea.js | Full suite |

## Standard Test Suite (per combination)

| Test ID | Name | Description |
|---------|------|-------------|
| T01 | Connect_Proto0 | TCP connect → AMQP header exchange (no SASL) |
| T02 | Connect_SaslPlain | TCP connect → SASL PLAIN negotiation → AMQP header |
| T03 | Connect_SaslAnonymous | TCP connect → SASL ANONYMOUS negotiation → AMQP header |
| T04 | Open_Exchange | OPEN frame exchange, verify container-id, max-frame, channel-max |
| T05 | Begin_Session | BEGIN frame exchange, verify channel mapping |
| T06 | Attach_Sender_Credit | ATTACH sender link, verify credit grant via FLOW |
| T07 | Attach_Receiver_Credit | ATTACH receiver link, verify credit request via FLOW |
| T08 | Transfer_Small | Send 64-byte message, verify TRANSFER + disposition |
| T09 | Transfer_Large | Send 64KB message, verify multi-frame TRANSFER |
| T10 | Disposition_Accept | Send message → accept disposition → verify settled |
| T11 | Disposition_Reject | Send message → reject disposition → verify released |
| T12 | Flow_CreditUpdate | Broker sends FLOW with new credit → client adjusts |
| T13 | Detach_Link | DETACH sender link gracefully |
| T14 | End_Session | END session gracefully |
| T15 | Close_Connection | CLOSE connection gracefully |
| T16 | Close_WithError | CLOSE with error condition, verify error propagation |
| T17 | MultiMessage_Pipeline | Send 100 messages, verify all delivered |
| T18 | Reconnect_Resume | Disconnect → reconnect, verify state recovery |

## Sub-tasks (chained)

### 5.1 Design test suite framework
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 5.2 Implement T01-T05 (connection lifecycle)
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 5.3 Implement T06-T12 (message flow)
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 5.4 Implement T13-T18 (error/recovery)
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 5.5 Run matrix — own server × legoflow client
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 5.6 Run matrix — own server × rhea.js client
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 5.7 Document pass/fail matrix
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

## References
- Phase 3 server fixes: phase3-server-fixes.md
- Phase 4 client fixes: phase4-client-fixes.md
- Standard test suite spec: Defined above in this doc
- Test location: `../src/test/java/ssg/legoflow/messaging/amqp/matrix/`

## Summary doc: — (created after phase completion)
