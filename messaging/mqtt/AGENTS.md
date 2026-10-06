# messaging / mqtt — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.


## Module Purpose

The `mqtt` module implements MQTT v3.1.1 and v5.0 protocols for publish/subscribe messaging. It provides both broker and client implementations, built on the `service` module for TCP transport and `blocks` for data processing primitives.

## Key Interfaces

- `MqttBroker` — broker with client registry, topic tree, session store, message routing, TLS, keep-alive, session expiry
- `MqttClient` — client with connect, subscribe, publish, auto-reconnect, keep-alive
- `MqttCodec` — binary codec for all 15 MQTT control packet types with stream-oriented accumulator
- `MqttBrokerService` / `MqttClientService` — service-layer wrappers for integration with `service` module
- `TopicTree` — topic-based routing with wildcard matching (+, #)
- `MqttSession` — session state: subscriptions, inflight messages, offline queue, expiry
- `MqttEventListener` — protocol flow listener for testing and debugging

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `broker` | Broker: client registry, connection handling, message routing, session store, will delivery, session expiry sweep, keep-alive monitoring |
| `broker.service` | `MqttBrokerService` + `MqttBrokerChannelHandler` — service-layer integration |
| `client` | Client: CONNECT/DISCONNECT lifecycle, subscribe/unsubscribe, publish, auto-reconnect, keep-alive (PINGREQ/PINGRESP) |
| `client.service` | `MqttClientService` + `MqttClientChannelHandler` — service-layer integration |
| `codec` | Stream-oriented packet codec: fixed header, variable header, payload encode/decode, internal accumulator for TCP stream reassembly |
| `protocol` | Packet types (15), QoS enum, ReasonCode, MqttProperties, MqttVersion, TopicSubscription, WillMessage |
| `topic` | Topic tree data structure, wildcard matching (+ single-level, # multi-level), retained message store (`RetainStore`) |

## MQTT-Specific Coding Conventions

### Packet Types (all 15)
- CONNECT (1), CONNACK (2), PUBLISH (3), PUBACK (4), PUBREC (5), PUBREL (6), PUBCOMP (7)
- SUBSCRIBE (8), SUBACK (9), UNSUBSCRIBE (10), UNSUBACK (11)
- PINGREQ (12), PINGRESP (13), DISCONNECT (14), AUTH (15, v5.0 only)

### QoS Flows
- **QoS 0**: PUBLISH (fire and forget, no acknowledgement)
- **QoS 1**: PUBLISH -> PUBACK (at least once)
- **QoS 2**: PUBLISH -> PUBREC -> PUBREL -> PUBCOMP (exactly once)

### Topic Matching Rules
- `/` is the level separator
- `+` matches exactly one level: `home/+/temperature` matches `home/kitchen/temperature`
- `#` matches zero or more levels (must be last): `home/#` matches `home/kitchen/temperature`
- Topics starting with `$` are system topics (e.g., `$SYS/broker/clients`)

### Session Lifecycle
- Clean session: subscriptions and queued messages discarded on disconnect
- Persistent session: subscriptions and QoS 1/2 messages survive reconnection
- Session expiry interval (v5.0): configurable TTL for persistent sessions

## Stream-Oriented Codec (MqttCodec)

MqttCodec uses an internal `ByteBuffer accumulator` to handle TCP stream semantics where MQTT packets may be split across multiple reads. Key points:

- **accumulator field**: holds leftover bytes from partial packet decodes
- **combineWithAccumulator()**: merges accumulator with new incoming ByteBuffer before decoding
- **decodeAll()**: decodes complete packets in a loop, catches `BufferUnderflowException` for partial packets, saves remainder to accumulator
- **hasBufferedData()**: checks whether the accumulator holds incomplete data
- **Per-connection instance**: MqttBroker creates one MqttCodec per connection, so accumulation state is isolated

This pattern is common across Lego Flow protocol codecs that operate over TCP byte streams (see also XmppCodec for a similar approach).

## Testing Practices

- Unit tests for packet codec: encode -> decode round-trip for each packet type
- QoS flow tests: verify state machine transitions and message ordering
- Topic tree tests: wildcard matching correctness with edge cases
- Broker integration tests: multi-client publish/subscribe with mixed QoS
- Session persistence tests: disconnect/reconnect with message delivery verification
- TLS config tests: builder validation, SSLContext creation, immutability
- Authentication tests: valid/invalid credentials, InMemoryAuthenticator CRUD, custom lambda authenticator
- Session expiry tests: expiry interval tracking, periodic sweep cleanup
- Clean Start tests: discard/resume session state, session present flag
- DISCONNECT reason code tests: all v5.0 reason codes, will suppression on normal disconnect
- QoS downgrade tests: broker delivers at min(pub QoS, subscriber max QoS)
- Keep-alive timeout tests: broker disconnects silent clients after 1.5x interval
- All tests use loopback transport (no external broker required)
- Test count: 193

### MQTT-Specific Testing

**`MqttEventListener`** — fires at `CLIENT_CONNECTED`, `CLIENT_DISCONNECTED`, `SESSION_CREATED`, `SESSION_RESUMED`, `SUBSCRIPTION_ADDED`, `WILL_DELIVERED`. Use `MqttEventListener.latchOnFirst(EventType)` to synchronize tests with broker-side protocol progress without `Thread.sleep`. (This is the generic protocol flow listener pattern — see [doc/AGENTS_protocol_accuracy.md](../../../doc/AGENTS_protocol_accuracy.md) §11.)

> **General test patterns**: See [../../../doc/AGENTS_test_patterns.md](../../../doc/AGENTS_test_patterns.md)
> **Protocol accuracy rules**: See [../../../doc/AGENTS_protocol_accuracy.md](../../../doc/AGENTS_protocol_accuracy.md)
