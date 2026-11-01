---
name: protocol-reverse-engineering
description: Capture reference wire bytes and implement to match exactly.
---

# Protocol Implementation from Reference Capture

When the published spec has ambiguities or the working reference implementation deviates from it, reverse-engineer wire format from captured traffic.

## Capture

Wrap the reference implementation in a **pass-through interceptor** at the socket layer. Capture every byte sent/received as hex. Save the full log.

## Decode

For each method frame:
1. Extract payload after any framing metadata
2. Walk fields in order: shortstr (1B len), longstr (4B len), table (int32 size + entries), bits (LSB-packed), short (2B), long (4B)
3. Count remaining bytes — leftover = missed field
4. Extra bytes between expected fields = reserved field spec omitted

## Implement from scratch

Do NOT fix existing broken code. Write each encoder to match decoded bytes exactly. Match field order, types, and encoding.

## Gotchas

- **Table:** Spec may say `size + entries + size-check` but reference sends `size + entries`. Match reference.
- **Bits:** Verify LSB vs MSB packing by checking a known pattern (`0x02` = bit 1 LSB-first).
- **Reserved fields:** Always present on wire (zero-valued). Don't skip even if spec omits.
- **Empty vs null:** Can produce different byte counts. Verify both.

## Verify

Encode same inputs as reference and compare wire bytes byte-for-byte. Any mismatch = bug.

## Document

For each spec deviation: what spec says, what reference does, what server accepts. This is the protocol guide.

## Support

See `references/amqp-091-rabbitmq-wire-format.md` for decoded deviations.