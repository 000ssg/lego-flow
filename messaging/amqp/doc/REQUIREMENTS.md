# AMQP Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 195
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: AMQP 1.0 (ISO 19464 / OASIS)

---

## Requirements

### Type System Codec
1. Implement all 22 AMQP primitive types as a sealed interface with record implementations
2. Encode values in self-describing binary format with constructor byte prefix
3. Use compact encoding forms: uint0/ulong0 for zero, smalluint/smallulong for values 0-255, smallint/smalllong for values -128 to 127
4. Encode/decode described types: constructor 0x00 + descriptor + described value
5. Encode/decode composite types: list (with element count and size prefix), map (key-value pairs), array (shared constructor)
6. Support variable-length types: binary, string (UTF-8), symbol (ASCII) with small (1-byte length) and large (4-byte length) forms
7. Validate type ranges at construction (e.g., UByte 0-255, UShort 0-65535, UInt 0-4294967295)

### Frame Codec
1. Encode AMQP frames with 8-byte header: SIZE (4 bytes) + DOFF (1 byte) + TYPE (1 byte) + CHANNEL (2 bytes)
2. Support AMQP frame type (0x00) and SASL frame type (0x01)
3. Handle heartbeat frames (empty body)
4. Parse extended headers when DOFF > 2
5. Separate performative body from payload in transfer frames
6. Enforce maximum frame size limit
7. Provide frame completeness check and frame size peek without advancing buffer position

### Performative Codec
1. Encode and decode all 9 AMQP performatives as described lists with ulong descriptors
2. Open: container-id, hostname, max-frame-size, channel-max, idle-timeout, capabilities, properties
3. Begin: remote-channel, next-outgoing-id, incoming-window, outgoing-window, handle-max
4. Attach: name, handle, role, settle modes, source, target, initial-delivery-count, max-message-size
5. Flow: session windows (next-incoming-id, incoming-window, next-outgoing-id, outgoing-window) + link fields (handle, delivery-count, link-credit, available, drain, echo)
6. Transfer: handle, delivery-id, delivery-tag, message-format, settled, more, state
7. Disposition: role, first, last, settled, state, batchable
8. Detach: handle, closed, error
9. End/Close: optional error condition
10. Trim trailing null fields from field lists for compact encoding
11. Encode/decode source and target terminus types with address extraction

### Session Management
1. Track session state: UNMAPPED, BEGIN_SENT, BEGIN_RCVD, MAPPED, END_SENT, END_RCVD, DISCARDING
2. Manage incoming/outgoing flow control windows (default 2048 each)
3. Allocate outgoing delivery-ids, consuming remote incoming window
4. Record incoming transfers, consuming incoming window with auto-replenish at 25%
5. Maintain link registry: sender links and receiver links indexed by handle, with name-to-handle mapping
6. Support frame sender callback for sending performatives on the session's channel

### Link Layer
1. Sender link: credit-based send, delivery-id from session, delivery-tag generation, unsettled delivery tracking
2. Receiver link: credit granting via flow, message queue (BlockingQueue), timed/blocking receive, message handler callback
3. Link state machine: DETACHED, ATTACH_SENT, ATTACH_RCVD, ATTACHED, DETACH_SENT, DETACH_RCVD
4. Credit management: sender tracks available credit, receiver auto-replenishes at 25% threshold
5. Disposition handling: update or settle deliveries by delivery-id range
6. Convenience methods: accept, reject (with condition), release

### Delivery Management
1. Track delivery lifecycle: delivery-id, delivery-tag, message, sender-settled flag
2. Support 6 delivery states as sealed interface: Received, Accepted, Rejected, Released, Modified, TransactionalState
3. Settlement via CompletableFuture for async notification
4. Encode/decode all delivery states to/from described AMQP types
5. Rejected carries optional error condition and description
6. Modified carries delivery-failed, undeliverable-here, and message-annotations
7. TransactionalState wraps a transaction-id and nested outcome

### Message Model
1. Support all 7 message sections: header, delivery-annotations, message-annotations, properties, application-properties, body, footer
2. Header record: durable, priority (0-9), TTL, first-acquirer, delivery-count
3. Properties record (13 fields): message-id, user-id, to, subject, reply-to, correlation-id, content-type, content-encoding, absolute-expiry-time, creation-time, group-id, group-sequence, reply-to-group-id
4. Three body types: data (binary), amqp-value (any AMQP type), amqp-sequence (list)
5. Annotations as symbol-keyed maps (delivery, message, footer sections)
6. Application properties as string-keyed maps
7. Encode/decode complete messages as sequences of described sections

### SASL Authentication
1. Client-side SaslMechanism interface: name(), initialResponse(), respond(challenge)
2. Three built-in mechanisms: ANONYMOUS (no credentials), PLAIN (\0username\0password), EXTERNAL (TLS cert)
3. Server-side SaslAuthenticator: mechanism list, credential store, anonymous toggle, external support
4. Pluggable custom AuthFunction for external identity provider integration
5. Five outcome codes: OK, AUTH, SYS, SYS_TEMP, SYS_PERM
6. SASL frame codec: encode/decode mechanisms, init, challenge, response, outcome

### Container (Server)
1. Accept TCP connections on configurable host/port (port 0 for ephemeral)
2. Perform SASL negotiation when requireSasl is enabled
3. Exchange protocol headers and open performatives
4. Manage sessions: begin/end lifecycle with remote-to-local channel mapping
5. Manage links: attach/detach with address-based routing registration
6. Route messages: transfer on receiver link -> decode -> route to sender links on same address
7. Auto-accept unsettled transfers and send disposition
8. Issue initial credit to receiver links
9. Handle close gracefully with close response
10. Clean up address routing on detach and connection close
11. Virtual thread per connection via Executors.newVirtualThreadPerTaskExecutor()

### Client
1. Connect to container with optional SASL mechanism
2. Exchange protocol headers and negotiate max-frame-size
3. Background frame reader on virtual thread
4. Create sessions: send begin, spin-wait for begin response
5. Create sender links: send attach, wait for attach response
6. Create receiver links: send attach, wait for attach response, issue initial credit
7. Send messages through sender links with settled/unsettled option
8. Handle incoming performatives: begin, attach, flow, transfer, disposition, detach, end, close
9. AutoCloseable with graceful close (send close performative)

### Error Handling
1. Define 23 standard AMQP error conditions as symbolic constants (connection, session, link, general, transaction errors)
2. AmqpException carries error condition symbol and human-readable description
3. Error encoding for performatives (detach, end, close)

### Demo Applications
1. SimpleSendReceiveDemo: container + producer + consumer with basic message flow
2. PubSubDemo: one publisher, multiple subscribers on the same address
3. RequestReplyDemo: request/reply using correlation-id and reply-to properties
4. TransactionDemo: transactional delivery states (commit/rollback patterns)

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
