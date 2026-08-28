# Phase 4: Client Compatibility
**Status:** ☐ Not started
**Started:** —
**Completed:** —

## Goal
Fix `AmqpClient` to work against all major brokers with per-broker configuration.

## Architecture
```
AmqpClient
├── ClientConfig
│   ├── brokerType: AUTO | RABBITMQ | ARTEMIS | QPID_DISPATCH | SOLACE | IBM_MQ
│   └── saslConfig: SaslConfig
│       ├── mechanism: PLAIN | ANONYMOUS | EXTERNAL | AUTO
│       └── authzidPolicy: EMPTY | MATCH_AUTHCID | CUSTOM
├── ConnectionNegotiator (broker-aware)
└── SessionManager (broker-aware)
```

## Broker Modes

| Behavior | RABBITMQ | ARTEMIS | QPID_DISPATCH | SOLACE | IBM_MQ | AUTO |
|----------|----------|---------|---------------|--------|--------|------|
| Header order | SASL proto-3 | Auto | ANONYMOUS SASL | SASL proto-3 | Auto | Try proto-3, fallback proto-0 |
| SASL authzid | Empty | Match authcid | Empty | Empty | Empty | Empty |
| OPEN frame | Client-first | Client-first | Client-first | Client-first | Client-first | Client-first |
| Address format | `/queues/:queue` | `queueName` | `closest:q` | `queue://q` | `queueName` | Plain |
| Settle mode | unsettled(0) | unsettled(0) | unsettled(0) | unsettled(0) | unsettled(0) | unsettled(0) |
| channel-max | 65535 | 32767 | 65535 | 65535 | 32767 | 32767 |
| Idle timeout | 0 | 0 | 0 | 500ms | 30s | 0 |
| Credit model | Wait for broker | Wait for broker | Self-grant fallback | Wait for broker | Wait for broker | Wait, fallback |

## Sub-tasks (chained)

### 4.1 Refactor ClientConfig with broker type detection
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.2 Fix SASL header negotiation — proto-3 first for RabbitMQ
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.3 Fix SASL authzid encoding — configurable per broker type
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.4 Fix OPEN frame ordering — handle server-first response
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.5 Fix address resolution — vendor-specific format converters
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.6 Fix settle mode negotiation — unsettled(0) with mixed(2) fallback
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.7 Fix channel-max default — 32767 for safety
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.8 Add credit model: wait for broker FLOW, fallback to self-grant
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

### 4.9 Verify each broker mode against Phase 2 captures
**Plan:** —
**Result:** —
**Sub-tasks spawned:** —

## References
- Phase 1 protocol variances: phase1-protocol-variances.md
- Phase 2 captures: phase2-traffic-capture.md
- AmqpClient.java: `../src/main/java/ssg/legoflow/messaging/amqp/client/AmqpClient.java`
- ClientConfig.java: `../src/main/java/ssg/legoflow/messaging/amqp/client/ClientConfig.java`

## Summary doc: — (created after phase completion)
