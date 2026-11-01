# AMQP 0-9-1: Spec vs RabbitMQ 4.x Reality

Captured during lego-flow AMQP 0-9-1 client development (Aug 2026).

## Queue Declare (`queue.declare`)

### Spec says
```
reserved-1: short
queue-name: longstr
passive: bit
durable: bit
exclusive: bit
auto-delete: bit
internal: bit
no-wait: bit
arguments: table
```

### RabbitMQ / Java client actually sends

```
reserved-1: short (0)
queue-name: shortstr (1-byte length, NOT 4-byte longstr)
bits packed into 1 byte, LSB-first (0x01 << index)
arguments: table (4-byte size prefix)
```

### Example: durable=false, exclusive=true, autoDelete=true

| Byte offset | Official (Java client) | What we sent before fix |
|-------------|----------------------|------------------------|
| 0-1 | `00 00` (reserved-1) | `00 00` (first 2 bytes of 4-byte longstr length) |
| 2 | `12` (shortstr len=18) | `00 16` (longstr len=22, spills across fields) |
| 3-20 | queue name bytes | more of our long name, misaligned |
| 21 | `0c` = bits `00001100` (exclusive=bit2, autoDelete=bit3) | reads queue name bytes as bits — garbage |
| 22-25 | `00 00 00 00` (empty table) | reads more queue bytes → huge table size → INTERNAL_ERROR |

### Queue Declare-OK response

```
queue-name: shortstr (1-byte length)
message-count: long (8 bytes)
consumer-count: long (8 bytes)
```

RabbitMQ sends two 8-byte `long` values. The spec says `long` for both. Our initial code read 4-byte `int` — off by 4 bytes, causing `BufferUnderflowException`.

## General Pattern

Every AMQP 0-9-1 method that the spec describes with:

- **`longstr` for entity names** (queue, exchange, consumer tag) → RabbitMQ uses `shortstr`
- **Bit fields** → RabbitMQ packs LSB-first (`0x01 << index`), NOT MSB-first (`0x80 >> index`)
- **`reserved-1: short`** → RabbitMQ requires it to be present (2 zero bytes), even though the spec marks it optional/reserved

## Bit Packing Detail

The AMQP spec describes bits packed MSB-first (left-to-right, first bit = 0x80). However, RabbitMQ and the Java client pack LSB-first (first argument = 0x01, second = 0x02, etc.).

```java
// WRONG (spec literal):
byte packBitsMSB(boolean... bits) {
    byte packed = 0;
    for (int i = 0; i < bits.length; i++)
        if (bits[i]) packed |= (byte) (0x80 >> i);
    return packed;
}

// CORRECT (RabbitMQ / Java client):
byte packBitsLSB(boolean... bits) {
    byte packed = 0;
    for (int i = 0; i < bits.length; i++)
        if (bits[i]) packed |= (byte) (0x01 << i);
    return packed;
}
```

For `exclusive=true, autoDelete=true`:
- MSB-first: `(0x80>>2)|(0x80>>3)` = `0x20|0x10` = `0x30`
- LSB-first: `(0x01<<2)|(0x01<<3)` = `0x04|0x08` = `0x0c` ← RabbitMQ sends this

## Sources

- AMQP 0-9-1 spec: https://www.rabbitmq.com/resources/specs/amqp0-9-1.pdf
- RabbitMQ errata: https://www.rabbitmq.com/amqp-0-9-1-errata
- Java client source: https://github.com/rabbitmq/java-client
