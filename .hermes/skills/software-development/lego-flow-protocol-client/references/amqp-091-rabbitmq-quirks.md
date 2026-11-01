# AMQP 0-9-1 RabbitMQ 4.x deviations from spec

## String encoding (shortstr vs longstr)

AMQP 0-9-1 spec defines `longstr` (4-byte length prefix) for vhost, queue names, exchange names, and consumer tags. RabbitMQ strictly expects `shortstr` (1-byte length prefix) for ALL of these. Using `longstr` causes the server to misparse the frame and close the connection with EOF.

**Rule:** Always use `shortstr` (1-byte length prefix) for names in AMQP 0-9-1 method payloads when talking to RabbitMQ.

## reserved-1 field placement

The spec places `reserved-1` fields at the beginning of the payload. RabbitMQ actually cares about the position of these fields within each method. Blind insertion at the start of every payload causes parsing failures.

Known positions verified against RabbitMQ Java client wire capture:
- `connection.open` — NO reserved-1. Payload: `shortstr(vhost) + shortstr(capabilities) + bit(insist)`
- `channel.open` — payload is minimal (no reserved-1 needed, RabbitMQ ignores it)
- `queue.declare` — `reserved-1(1) + queue-name(shortstr) + passive(1) + durable(1) + exclusive(1) + auto-delete(1) + internal(1) + nowait(1) + arguments(table)`
- `basic.consume` — `reserved-1(1) + queue-name(shortstr) + consumer-tag(shortstr) + no-local(1) + no-ack(1) + exclusive(1) + no-wait(1) + arguments(table)`

## Client greeting byte count

The AMQP 0-9-1 client greeting sent before the protocol handshake is EXACTLY 8 bytes:
```
AMQP\0\0\9\1  // 0x41 0x4D 0x51 0x50 0x00 0x00 0x09 0x01
```
A 9-byte greeting causes RabbitMQ to reject the connection immediately with EOF.

## Bit fields

RabbitMQ expects individual bits as separate bytes (1 or 0), not packed into a single byte. Use `payload.put((byte) (flag ? 1 : 0))` for each bit field.

## Handshake sequencing

RabbitMQ 4.x may skip `connection.start` negotiation and send `tune` immediately after the greeting. The client should handle both the standard AMQP 0-9-1 greeting response (starting with "AMQP") and a direct frame (starting with frame type byte 0x0A = method frame).

## Heartbeat tuning

Server may offer heartbeat=0 (disabled). Client should use `min(clientHeartbeat, serverHeartbeat)`. If either side offers 0, heartbeat is disabled.
