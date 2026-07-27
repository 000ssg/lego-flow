# MQTT Module — Requirements

## Timeline Overview

- **Module Added**: May 2026
- **Tests**: 146
- **Dependencies**: blocks (DP/DF), service (TCP transport)
- **Standards**: MQTT v3.1.1 (OASIS), MQTT v5.0

---

## Requirements

### Packet Codec
1. Encode and decode all 15 MQTT control packet types in binary format
2. Parse fixed header: packet type (4 bits), flags (4 bits), remaining length (variable-length encoding)
3. Parse variable headers specific to each packet type (e.g., CONNECT flags, packet identifiers)
4. Handle payload encoding for CONNECT (client ID, will topic/message, username, password), PUBLISH (application message), SUBSCRIBE (topic filters + QoS), UNSUBSCRIBE (topic filters)
5. Support MQTT v5.0 properties section in variable headers
6. Validate packet structure and reject malformed packets

### Topic Engine
1. Maintain a topic tree data structure for efficient message routing
2. Match subscriptions against publish topics with wildcard support
3. Single-level wildcard (+): matches exactly one topic level
4. Multi-level wildcard (#): matches zero or more levels, must be last character
5. Store retained messages per topic (one per topic, replaced on new retained publish)
6. Deliver retained messages to new subscribers matching the topic filter
7. Handle system topics ($SYS/) that are not matched by # at the beginning

### QoS Delivery Flows
1. QoS 0 (at most once): single PUBLISH, no acknowledgement, no retry
2. QoS 1 (at least once): PUBLISH -> PUBACK, retry PUBLISH if PUBACK not received
3. QoS 2 (exactly once): PUBLISH -> PUBREC -> PUBREL -> PUBCOMP, four-step handshake
4. Track inflight messages with packet identifiers (16-bit)
5. Retry unacknowledged messages with configurable timeout
6. Downgrade QoS when subscription QoS < publish QoS

### Session Management
1. Clean session (v3.1.1) / clean start (v5.0): discard all state on connect
2. Persistent session: preserve subscriptions and queued QoS 1/2 messages across disconnects
3. Queue QoS 1/2 messages for offline persistent-session clients
4. Deliver queued messages on client reconnection
5. Session expiry interval (v5.0): remove persistent session after configurable timeout
6. Generate unique client identifiers when client provides empty client ID

### Broker
1. Accept TCP connections and perform CONNECT/CONNACK handshake
2. Maintain client registry with connection state tracking
3. Route published messages to subscribers via topic tree
4. Enforce maximum QoS per subscription
5. Handle client keep-alive with PINGREQ/PINGRESP
6. Detect client timeout and trigger will message delivery
7. Support configurable maximum connections, message size, and inflight limits
8. Thread-safe concurrent access using virtual threads

### Client
1. Establish TCP connection and send CONNECT with configurable options
2. Subscribe to topic filters with per-subscription QoS
3. Publish messages with QoS 0/1/2 and optional retain flag
4. Auto-reconnect with configurable delay and backoff
5. Keep-alive via periodic PINGREQ with configurable interval
6. Track inflight QoS 1/2 messages and handle acknowledgements
7. Support Last Will and Testament configuration

### Last Will and Testament
1. Configure will topic, payload, QoS, and retain flag at connect time
2. Broker stores will message associated with client session
3. Deliver will message when client disconnects ungracefully (connection lost, keep-alive timeout)
4. Do not deliver will message on graceful DISCONNECT
5. Support will delay interval (v5.0)

### Demo Applications
1. SimpleBrokerDemo: start broker, accept clients, route messages
2. PubSubDemo: client publish/subscribe with QoS levels and wildcards
3. RetainedMessageDemo: retained messages and last will behavior
4. SessionPersistenceDemo: clean vs persistent sessions with offline queuing
5. MultiClientDemo: multiple clients with mixed QoS and topic patterns

---

## Commit: `TBD` - Stream-Oriented ByteBuffer Codec (2026-07-06)

### Original Request
> "check all protocol implementations for correct handling of data split over multiple data buffers"

### Reformulated Requirements
1. MqttCodec must correctly handle MQTT packets split across multiple TCP reads (ByteBuffer stream semantics)
2. Codec must accumulate partial data internally and emit complete packets only when fully received
3. Per-connection codec instance ensures no cross-connection state leakage

### Final Design Decisions
- Added internal `ByteBuffer accumulator` field to MqttCodec for buffering partial packets
- Added `combineWithAccumulator()` to merge accumulator contents with new incoming ByteBuffer data
- Added `hasBufferedData()` to check whether the accumulator holds incomplete packet data
- Rewrote `decodeAll()` to combine the accumulator with new input, decode complete packets in a loop, catch `BufferUnderflowException` for partial packets, and save the remainder back to the accumulator
- MqttBroker creates one MqttCodec per connection; accumulation is handled internally by MqttCodec (confirmed with comment in MqttBroker)

### Implementation Details
- Modified `MqttCodec.java`: added accumulator field, `combineWithAccumulator()`, `hasBufferedData()`, rewrote `decodeAll()` loop
- Modified `MqttBroker.java`: added comment confirming per-connection codec instance

### Test Coverage
- No new tests added (total: 193)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (MQTT codec fix) |
| Agent tokens | ~41K |
| Agent tool calls | ~10 |
| Agent wall time | ~1 min |
| Files created/modified | 2 |
| Lines added/removed | +69 / -7 |
| Tests added | 0 (total: 193) |

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition
