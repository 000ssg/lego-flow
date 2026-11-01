# AMQP 0-9-1 Wire Format — RabbitMQ 4.3.5 / Java Client 5.22.0

Decoded from pass-through socket capture of the official Java client.

## Verified Deviations from Published Spec

| Method | Field | Spec says | Reference sends | RabbitMQ accepts |
|--------|-------|-----------|-----------------|------------------|
| `channel.open` | reserved-1 | table (8B empty) | shortstr (1B `00`) | Both |
| `connection.start-ok` | client-properties | `size + entries + size-check` | `size + entries` (no check) | Both |
| `basic.qos` | prefetch-count | short (2B) | short (2B) | ✓ matches |
| `basic.publish` | reserved-1 | (none in spec) | short `00 00` | Required |
| `queue.purge` | reserved-1 | (none in spec) | short `00 00` | Required |
| `exchange.declare` | bits | 6 bits packed LSB | single octet, LSB | ✓ |
| `queue.declare` | bits | 6 bits packed LSB | single octet, LSB | ✓ |

## Bit Packing

**LSB-first.** Bit 0 = 0x01, bit 1 = 0x02, bit 2 = 0x04, etc.

- `exchange.declare` durable only: `02`
- `queue.declare` durable + auto-delete: `0A` (= 0x02 | 0x08)

## Table Encoding

Java client sends `int32-size + entries` only. No 4-byte size-check suffix. RabbitMQ accepts this form.

For null/empty: `00 00 00 00` (4 bytes, size 0).

## Source

Capture: `/tmp/amqp-reference/all-methods.txt` (51 frames, all methods exercised).
