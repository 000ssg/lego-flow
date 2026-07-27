# STOMP Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 157
- **Dependencies**: blocks (DP/DF), service (lifecycle), http (WebSocket adapter, optional)
- **Standards**: STOMP 1.2 (stomp.github.io), backward compatible with 1.0 and 1.1

---

## Requirements

### Frame Codec
1. Parse and serialize all 16 STOMP frame types (11 client + 4 server + heartbeat)
2. Handle text-based wire format: COMMAND\n, headers, blank line, body, NULL terminator
3. Implement header value escaping per STOMP 1.2: `\n` (newline), `\\` (backslash), `\c` (colon), `\r` (carriage return)
4. Support binary body via `content-length` header (NULL bytes allowed in body)
5. Detect and handle heart-beat frames (empty EOL)
6. Skip leading EOLs between frames (inter-frame whitespace)
7. First occurrence of repeated headers wins (STOMP 1.2 spec)
8. Support both LF and CR+LF line endings

### Headers
1. Case-sensitive header names (per STOMP 1.2 spec)
2. Preserve header insertion order (LinkedHashMap)
3. Standard header constants for all protocol headers (host, accept-version, destination, id, ack, etc.)
4. Support put, putIfAbsent, get, getOrDefault, contains, remove operations

### Session Management
1. Lifecycle states: CONNECTING -> CONNECTED -> DISCONNECTING -> DISCONNECTED
2. Track subscriptions per session (subscription ID -> destination)
3. Track active transactions per session
4. Track pending receipts per session
5. Generate unique message IDs per session (sessionId + counter)
6. Store heart-beat intervals (client and server, send and receive)
7. Thread-safe: ConcurrentHashMap for subscriptions, ConcurrentHashSet for transactions/receipts, AtomicLong for message IDs

### Broker
1. Accept connections via StompTransport SPI (transport-agnostic)
2. Perform version negotiation: support 1.0, 1.1, 1.2; prefer highest mutual version
3. Handle CONNECT/STOMP with login/passcode credentials
4. Route messages from SEND to matching SUBSCRIBE destinations (fan-out)
5. Support three acknowledgment modes: auto, client (cumulative), client-individual
6. Implement transactions: BEGIN creates buffer, SEND/ACK/NACK buffered, COMMIT applies all, ABORT discards all
7. Send RECEIPT frames when `receipt` header is present on any client frame
8. Send ERROR frames for protocol violations with descriptive messages
9. Heart-beat negotiation in CONNECT/CONNECTED exchange
10. Session cleanup on disconnect: remove subscriptions, transactions, pending acks
11. Handle graceful disconnect (DISCONNECT + RECEIPT) and ungraceful (connection loss)
12. Virtual thread per connection for concurrent handling

### Client
1. Connect to broker with configurable host, login, passcode, heart-beat settings
2. Version negotiation: send accept-version 1.0,1.1,1.2
3. Send messages to destinations with optional content-type and content-length
4. Send messages within transactions
5. Send with receipt: return CompletableFuture that completes on RECEIPT
6. Subscribe to destinations with configurable ack mode (auto, client, client-individual)
7. Unsubscribe by subscription ID
8. ACK and NACK (standalone and within transactions)
9. Transaction management: begin, commit, abort
10. Graceful disconnect with receipt confirmation
11. Background receiver thread (virtual thread) dispatching MESSAGE, RECEIPT, ERROR
12. Error handler registration for ERROR frames
13. Heart-beat monitoring with send/receive tracking

### Transport SPI
1. Define StompTransport interface: send(frame), receive(), close(), isOpen()
2. Blocking receive semantics
3. Transport responsible for frame boundary detection

### TCP Adapter
1. TcpStompTransport: raw TCP socket with NULL-byte frame boundary detection
2. Byte-by-byte reading with content-length awareness for binary bodies
3. Heart-beat detection (all-newline data)
4. Synchronized send on output stream
5. TcpStompServer: ServerSocket accept loop on virtual thread, auto-port support (port 0)
6. TcpStompClient: convenience wrapper creating Socket + TcpStompTransport + StompClient

### WebSocket Adapter
1. WebSocketStompTransport: one STOMP frame per WebSocket text message
2. String-based codec (encodeToString / decodeFromString)
3. Incoming frame queue (LinkedBlockingQueue) for blocking receive
4. Non-blocking tryReceive() variant
5. WebSocketStompHandler: HTTP upgrade handler, subprotocol `v12.stomp`

### Heart-Beat
1. Parse heart-beat header value: `cx,cy` (send capability ms, receive desire ms)
2. Format heart-beat header value
3. Negotiate intervals: send = MAX(client-cx, server-cy), receive = MAX(server-cx, client-cy); 0 if either is 0
4. Track last send/receive timestamps
5. shouldSendHeartbeat(): true if elapsed time exceeds send interval
6. isReceiveTimedOut(): true if elapsed time exceeds 2x receive interval (tolerance margin)

### Demo Applications
1. SimplePubSubDemo: broker + publisher + subscriber, message delivery verification
2. RequestReplyDemo: request-reply pattern over two destinations
3. TransactionalDemo: BEGIN/SEND/COMMIT and BEGIN/SEND/ABORT flows
4. InMemoryStompTransport: blocking queue pairs for testing without network I/O

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
