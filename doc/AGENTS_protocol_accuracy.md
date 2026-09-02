# Protocol Accuracy Rules

Load when writing protocol interop tests, debugging wire format issues, or verifying RFC compliance.

> **Root AGENTS.md**: See [../AGENTS.md](../AGENTS.md) for project-wide conventions.

---

## 1. Verify Enum Codes Against RFC

When tests reference enum values by numeric code, always verify against the actual RFC specification.

**Example**: Telnet option code 34 is LINEMODE (RFC 1143), not 32. Code 32 is unassigned.

```java
// BAD: Wrong code
assertThat(TelnetOption.fromCode(32)).isEqualTo(TelnetOption.LINEMODE);

// GOOD: Verify against RFC
assertThat(TelnetOption.fromCode(34)).isEqualTo(TelnetOption.LINEMODE);
```

## 2. Byte-to-Integer AssertJ Comparison

AssertJ's `isEqualTo()` fails when comparing `byte` to `int` due to strict type matching.

```java
// BAD: AssertJ strict type comparison fails (byte 24 ≠ Integer 24)
assertThat(msg[2]).isEqualTo(24);

// GOOD: Promote byte to unsigned int
assertThat(msg[2] & 0xFF).isEqualTo(24);
```

## 3. Protocol Handler Suboptions

Protocol handlers that process subnegotiation data require actual data bytes. An empty list returns null.

```java
// BAD: Empty list → handler returns null
byte[] response = handler.handle(List.of());

// GOOD: Include suboption type (SEND = 1)
byte[] response = handler.handle(List.of(1));
```

## 4. NAWS Data Format (Telnet)

NAWS (RFC 1073) carries exactly 4 bytes: `colsHi, colsLo, rowsHi, rowsLo` (big-endian).

```java
// BAD: 5 elements → cols=0
handler.handle(List.of(0, 0, 132, 0, 50));

// GOOD: Exactly 4 bytes (132 cols, 50 rows)
handler.handle(List.of(0, 132, 0, 50));
```

## 5. VT100 SGR Color Mode

SGR codes 30-37 set foreground COLOR INDEX (0-7), not the SGR mode.

```java
// BAD: fgMode is 0 (8-color mode), not the SGR code
t.feed("\u001b[31m");
assertThat(t.currentAttr().fgMode()).isEqualTo(31);

// GOOD: Check color index via foreground()
t.feed("\u001b[31m");
assertThat(t.currentAttr().foreground()).isEqualTo(1);  // red = index 1
assertThat(t.currentAttr().fgMode()).isEqualTo(0);  // 0 = 8-color
```

## 6. VT100 vs DEC Extension SGR Codes

VT100 only supports SGR 7 for reverse video. SGR 52 (Enable Image) is a DEC extension.

```java
// GOOD: VT100 reverse video
t.feed("\u001b[7m");
assertThat(t.currentAttr().reverse()).isTrue();
```

## 7. CSI DeleteLine Semantics

CSI `1M` (DL) deletes the line at cursor position and shifts lines BELOW upward. It does NOT clear the line.

```java
// GOOD: DL shifts content from below up
t.feed("\u001b[1M");
assertThat(lines.get(1)).contains("C");  // Row 2 now has "C" from row 3
```

## 8. SSH Version Byte Count

The SSH version string `"SSH-2.0-legoflow_1.0\r\n"` is 22 bytes: 20 chars + 2 (CR LF).

```java
assertThat(wire).hasSize(22);
```

## 9. Cursor Motion Math

CUU/CUD are RELATIVE movements. Verify starting position after prior movements.

```java
// Cursor starts at row 1, moves down 5 → row 6, moves up 3 → row 3
t.feed("\u001b[5B"); // row 6
t.feed("\u001b[3A"); // row 3
assertThat(t.cursor().row()).isEqualTo(3);
```

## 10. ByteBuffer Reuse Without Clear

Protocol handlers reuse `ByteBuffer` instances. After `flip()` and reading, call `clear()` before reuse.

```java
// BAD: buffer position at limit — readFully() reads zero bytes
buf.flip();
byte[] data = new byte[8];
buf.get(data);
readFully(buf);  // does nothing!

// GOOD: clear before reuse
headerBuf.clear();
readFully(headerBuf);
```

## 11. Protocol Flow Listener Pattern

Every protocol module should provide a lightweight flow listener for testing and debugging:

```java
// Interface with NO_OP default (zero overhead when null)
public interface ProtocolEventListener {
    void onEvent(EventType type, String context, String detail);
    ProtocolEventListener NO_OP = (type, ctx, detail) -> {};
    default ProtocolEventListener orElse(ProtocolEventListener listener) {
        return listener != null ? listener : this;
    }
}

// Container: fire at protocol transitions, not hot path
container.setListener(listener); // null = no-ops
```

**Rules**:
- Zero overhead when disabled (no `if (listener != null)` in hot path)
- Fires at protocol transitions only (connection start, session create, etc.)
- Never in the hot data path (frame reads/writes)
- Provide a factory method for latch-based testing

## 12. Rule

**All interop test assertions must be verified against the actual RFC or specification document, not guessed from implementation. When in doubt, capture and compare the reference server's actual protocol bytes.**
