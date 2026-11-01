---
name: amqp-header-spec-compliance
description: "AMQP 1.0 message header spec compliance rules."
---

# AMQP 1.0 Message Header Spec Compliance

## Critical Rule: Header Default Values

AMQP 1.0 spec (section 3.2.1) defines these **default values** for the message header section:

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `durable` | boolean | **true** | Message is durable (not lost on broker restart) |
| `priority` | ubyte | 4 | Relative message priority |
| `ttl` | uint (ms) | 0 | No expiry |
| `first-acquirer` | boolean | **true** | Message has not been acquired by any other link |
| `delivery-count` | uint | 0 | Number of unsuccessful delivery attempts |

## Common Bug: Wrong Defaults

**WRONG implementation:**
```java
public Header() {
    this(false, (short) 4, 0, false, 0);  // durable=false, firstAcquirer=false
}
```

**CORRECT implementation:**
```java
public Header() {
    // AMQP 1.0 spec (section 3.2.1): durable=true, first-acquirer=true
    this(true, (short) 4, 0, true, 0);
}
```

## Why This Matters

1. **Message durability**: `durable=false` means messages are NOT persisted by brokers. RabbitMQ 4.2+ explicitly changed to treat omitted header as `durable=false` (per their release notes).

2. **Broker compatibility**: Real brokers (RabbitMQ, Artemis) interpret `firstAcquirer=false` as "message has been seen before", affecting routing and redelivery behavior.

3. **Tests masking the bug**: Tests using `AmqpMessage.of("text")` create messages with `header=null`, causing the header section to be omitted entirely. The in-process server doesn't validate durability, and interop tests passed because the header was absent.

## Encoding Behavior

- When `header=null`: Header section is **omitted** from encoded message
- When `header` is set (even with defaults): Header section is **always encoded**
- The `Header()` constructor now creates a spec-compliant header with defaults
- The `AmqpMessage()` constructor now initializes `header = new Header()` so all messages have a proper header

## Broker-Specific Notes

### RabbitMQ 4.2+
- If header section is omitted, `durable` is assumed to be `false`
- Team RabbitMQ recommends client libraries send messages as durable by default
- All official RabbitMQ AMQP 1.0 client libraries send `durable=true`

### Apache Artemis
- Follows spec defaults: `durable=true`, `firstAcquirer=true`
- Accepts both explicit and omitted header sections

### Qpid Dispatch
- Uses different routing model (not direct queue consumption)
- Accepts spec-compliant headers

## Verification Checklist

- [ ] `Header()` constructor uses `durable=true, firstAcquirer=true`
- [ ] `Header.ofDurable()` preserves spec-compliant defaults
- [ ] `AmqpMessage()` constructor initializes `header = new Header()`
- [ ] Tests verify spec-compliant defaults, not wrong defaults
- [ ] Integration tests with real brokers (RabbitMQ, Artemis) pass

## References
- AMQP 1.0 Spec Section 3.2.1: https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging.html#type-header
- RabbitMQ AMQP 1.0 plugin: https://github.com/rabbitmq/rabbitmq-amqp1.0
- RabbitMQ 4.2.0 release notes: https://github.com/rabbitmq/rabbitmq-server/releases/tag/v4.2.0
