# AMQP 0-9-1 Wire Format Reference

## Frame Structure

```
Non-heartbeat: TYPE(1) + CHAN(2) + SIZE(4) + PAYLOAD(N) + END(1=0xCE)
Heartbeat:     TYPE(1) + END(1=0xCE)
```

Frame types: `0x01`=method, `0x02`=header, `0x03`=body, `0x08`=heartbeat

## Method Payload

```
[CLASS_ID(2)] [METHOD_ID(2)] [ARGUMENTS(N)]
```

Class IDs: Connection=10, Channel=20, Exchange=40, Queue=50, Basic=60

## Common Pitfalls Found in Lego-Flow Client

### Missing `reserved-1` Fields

AMQP 0-9-1 methods nearly all start with a 2-byte `reserved-1` field (short, zero value). RabbitMQ's Java client sends these, and RabbitMQ 4.x rejects frames without them.

**Methods affected:** `queue.declare`, `queue.bind`, `queue.unbind`, `queue.purge`, `queue.delete`, `exchange.declare`, `exchange.delete`, `basic.publish`, `basic.consume`, `basic.get`, `basic.cancel`, `basic.qos`, `channel.open`, `channel.close`, `connection.close`

**Fix:** Every method writer must write `buf.putShort((short)0)` as the first 2 bytes of the arguments.

### Bit Packing

AMQP 0-9-1 packs bits left-to-right into a single byte:
```java
byte bitField = 0;
bitField |= (passive ? 0x80 : 0);        // bit 0
bitField |= (durable ? 0x40 : 0);         // bit 1
bitField |= (exclusive ? 0x20 : 0);       // bit 2
bitField |= (autoDelete ? 0x10 : 0);      // bit 3
bitField |= (internal ? 0x08 : 0);        // bit 4
bitField |= (noWait ? 0x04 : 0);          // bit 5
buf.put(bitField);
```

**Bug in Lego-Flow:** Each boolean was written as a separate byte (`buf.put((byte)(flag ? 1 : 0))`). This expands 1 byte into 6.

### String Length Encoding

- `shortstr`: 1-byte length prefix (max 255 chars)
- `longstr`: 4-byte length prefix (max 2^32 chars)

**Bug in `connection.open`:** Spec says `virtual-host` is `longstr` (4-byte len), but code used `shortstr` (1-byte len).

**Bug in `basic.consume`:** `consumer-tag` is `shortstr` (correct).

### Integer Sizes

- `short` = 2 bytes (e.g., `channel-max`, `reserved-1`)
- `long` = 8 bytes (e.g., `prefetch-size` in `basic.qos`)
- `long-long-int` = 8 bytes unsigned

**Bug in `basic.qos`:** Spec says `[prefetch-size:long][prefetch-count:short][global:bit]` but code sent `[prefetch-count:int][global:bit][reserved:bit]`. Wrong field order, wrong sizes.

### `channel.open` Payload

Spec: `[virtual-host:longstr][reserved:bit]`
Bug: Code sent empty bytes (`new byte[0]`), missing both fields.
