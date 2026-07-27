# STOMP 1.2 Protocol Compliance

Reference specification: [STOMP Protocol Specification, Version 1.2](https://stomp.github.io/stomp-specification-1.2.html)

## Supported Features

| Feature | Status | Notes |
|---------|--------|-------|
| **Frame format** | Full | COMMAND\n headers\n\n body\0 |
| **CONNECT/STOMP** | Full | Both CONNECT and STOMP commands supported |
| **CONNECTED** | Full | version, server, session, heart-beat headers |
| **SEND** | Full | destination, content-type, content-length, receipt, transaction |
| **SUBSCRIBE** | Full | id, destination, ack (auto/client/client-individual) |
| **UNSUBSCRIBE** | Full | id, receipt |
| **ACK** | Full | id, transaction |
| **NACK** | Full | id, transaction |
| **BEGIN** | Full | transaction |
| **COMMIT** | Full | transaction |
| **ABORT** | Full | transaction |
| **DISCONNECT** | Full | receipt |
| **MESSAGE** | Full | destination, message-id, subscription, content-type, ack |
| **RECEIPT** | Full | receipt-id |
| **ERROR** | Full | message, receipt-id, content-type, body with details |
| **Heart-beats** | Full | Bidirectional negotiation (cx,cy format) |
| **Receipts** | Full | Client can request receipt for any frame |
| **Transactions** | Full | BEGIN/COMMIT/ABORT with buffered SEND/ACK/NACK |
| **Ack: auto** | Full | No ACK needed, immediate delivery |
| **Ack: client** | Full | Cumulative acknowledgment |
| **Ack: client-individual** | Full | Per-message acknowledgment |
| **Content-length** | Full | Binary body support with content-length header |
| **Content-type** | Full | MIME type preserved in MESSAGE frames |
| **Header escaping** | Full | \n, \\\\, \c (colon), \r per STOMP 1.2 spec |
| **Version negotiation** | Full | accept-version: 1.0,1.1,1.2; prefers highest |
| **First header wins** | Full | Duplicate headers: first occurrence takes precedence |
| **EOL handling** | Full | Both LF and CR+LF accepted |
| **Heart-beat frame** | Full | Empty line (single LF byte) |
| **NULL terminator** | Full | Body terminated by \0 (or content-length) |

## Version Support

| Version | Status | Notes |
|---------|--------|-------|
| 1.0 | Negotiable | Supported via accept-version negotiation |
| 1.1 | Negotiable | Supported via accept-version negotiation |
| 1.2 | Full | Primary implementation target |

## Transport Adapters

| Transport | Status | Notes |
|-----------|--------|-------|
| In-memory | Full | Blocking queue pair for testing |
| TCP | Full | Raw TCP with frame boundary detection |
| WebSocket | Full | Text frames, requires http module at runtime |

## Known Limitations

| Limitation | Reason |
|------------|--------|
| No destination wildcards (topic.>, topic.*) | Not defined by STOMP spec; broker-specific extension |
| No message selectors | Not part of STOMP spec |
| No authentication enforcement | Broker accepts all CONNECT frames; login/passcode are stored but not validated |
| No message persistence | All messages are in-memory only |
| No message redelivery on NACK | NACK removes pending ack but does not redeliver |
| WebSocket adapter requires http module | Optional runtime dependency |

## Test Coverage

- **Total tests**: 157
- Frame codec: parse/serialize all commands, header escaping, binary body with content-length
- Broker: multi-destination subscribe/send, ack modes (auto/client/client-individual)
- Transactions: BEGIN/SEND/ACK/COMMIT, BEGIN/SEND/ABORT (messages not delivered)
- Heart-beats: negotiation, timeout detection, timer management
- Receipts: request and verify receipt for SEND, DISCONNECT
- Session lifecycle: CONNECT -> operations -> DISCONNECT
- Error handling: malformed frames, unknown commands, invalid ack IDs
- TCP adapter: full client-server round-trip (connect, pub/sub, transactions, disconnect)
- Demo tests: pub/sub, request-reply, transactional messaging
