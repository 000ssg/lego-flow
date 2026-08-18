# TN3270 Terminal — Compliance

## RFC 1576 — TN3270 Enhanced Session Protocol

This module implements the IBM 3270 terminal protocol over TN3270, as
defined in RFC 1576 (TN3270 Enhanced Session Protocol) and the IBM 3270
Information Entry Protocol specification.

### Screen Model (RFC 1576 §2.1)

| Feature | Standard | Status |
|---------|----------|--------|
| 24×80 grid | Standard | ✅ Implemented |
| 24×132 grid | Wide | ✅ Implemented |
| 43×80 grid | Tall | ✅ Implemented |
| 43×132 grid | Wide tall | ✅ Implemented |
| 52×80 grid | Lettermode | ✅ Implemented |
| 32-byte keyboard area | Required | ✅ Implemented |
| Field-structured cells | Required | ✅ Implemented |

### Field Attributes (RFC 1576 §3.3)

| Primary Attribute | Code | Editable | Status |
|-------------------|------|----------|--------|
| Normal | 0x00 | Yes | ✅ Implemented |
| Read-only / Protect | 0x01 | No | ✅ Implemented |
| Bold | 0x02 | Yes | ✅ Implemented |
| Underline | 0x04 | Yes | ✅ Implemented |
| Reverse video | 0x08 | Yes | ✅ Implemented |
| Dark | 0x10 | Yes | ✅ Implemented |
| Flash/Blink | 0x20 | Yes | ✅ Implemented |
| Hole-punch | 0x40 | Yes | ✅ Implemented |

| Secondary Attribute | Code | Status |
|---------------------|------|--------|
| Normal intensity | 0x00 | ✅ Implemented |
| Not emphasized | 0x02 | ✅ Implemented |
| Italic | 0x03 | ✅ Implemented |
| Blue background | 0x11 | ✅ Implemented |
| Purple background | 0x12 | ✅ Implemented |
| Green background | 0x13 | ✅ Implemented |
| Cyan background | 0x14 | ✅ Implemented |
| Red background | 0x15 | ✅ Implemented |
| Yellow background | 0x16 | ✅ Implemented |
| White background | 0x17 | ✅ Implemented |

### Control Functions (RFC 1576 §4.2)

| Control Byte | Function | Status |
|--------------|----------|--------|
| 0x80 | PPI (Print Page Indicator) | ✅ Implemented |
| 0x81 | RTS (Reset to Screen) | ✅ Implemented |
| 0x82 | TSS (Top of Screen) | ✅ Implemented |
| 0x83 | ECD (Erase Change Detection) | ✅ Implemented |
| 0x84 | UNDO (Erase All Unchanged) | ✅ Implemented |
| 0x85 | Flash Screen | ✅ Implemented |
| 0x87 | RK (Request Keyboard) | ✅ Implemented |
| 0x88 | ATN (Attention) | ✅ Implemented |

### 3270 Data Stream Format

| Component | Encoding | Status |
|-----------|----------|--------|
| Keyboard data (32 bytes) | Raw bytes | ✅ Implemented |
| Field length | 1–3 bytes, bit 7 = continuation | ✅ Implemented |
| Field attributes (2 bytes) | Primary + secondary | ✅ Implemented |
| Field data | Variable length | ✅ Implemented |
| 5-bit character set | 0x80–0xBF → charset table | ✅ Implemented |

### Keyboard Map (RFC 1576 §4.3)

| Key Group | Codes | Status |
|-----------|-------|--------|
| PF keys (PF1–PF12) | 0xF1–0xFC | ✅ Implemented |
| PA keys (PA1–PA3) | 0xFD–0xFF | ✅ Implemented |
| Cursor keys | 0x1B–0x1E | ✅ Implemented |
| Edit keys (Insert, Erase, etc.) | 0x10–0x19 | ✅ Implemented |
| Navigation (Home, Page Up/Down) | 0x16–0x18 | ✅ Implemented |
| Assist keys (Attn, Help, Lock) | 0x08, 0x15, 0x1F | ✅ Implemented |
| Printable characters | 0x20–0x7E | ✅ Implemented |

## Limitations and Rationale

### Limitation: Change Detection Markers
3270 uses change detection (CD) markers to track which fields have been
modified since the last screen update. This module does not implement
the CD marker protocol.

**Reason:** The module operates in a **raw character mode** for unit
testing and simple integrations. Full CD tracking requires stateful
screen-change tracking that is the responsibility of the TN3270 gateway
layer, not the terminal emulator itself. The gateway is responsible for
packing CD markers into data stream records. This separation keeps the
terminal implementation focused.

### Limitation: Full-Screen Cell-by-Cell Addressing
The TN3270 data stream supports cell-by-cell cursor addressing for screen
updates, but this module processes input as a flat character stream with
cursor movement, not as 3270 cell-addressed field records.

**Reason:** Cell-addressed updates are a TN3270 **gateway** feature (the
server sends screens as structured fields, not individual cell updates).
The terminal emulator receives already-structured data from the gateway.
Cell-by-cell addressing is not needed for the emulator level — it is a
network protocol feature.

### Limitation:Extended TN3270E Features
TN3270E (RFC 2355) adds features like alternate screens, color cells,
and programmable keys. These are not implemented.

**Reason:** TN3270E features are enhancements beyond the base RFC 1576
specification. The core 3270 protocol is fully implemented. TN3270E
support can be added in a future enhancement phase if required by
specific host systems.

### Limitation: 3270 Data Stream Parsing Edge Cases
Some uncommon 3270 data stream patterns (e.g., length values exceeding
4095 bytes, reserved control bytes) are handled gracefully by truncating
or ignoring, rather than throwing exceptions.

**Reason:** Defensive handling is preferred over crashes for malformed
input. This is consistent with how reference implementations (x3270,
Open 3270) handle unexpected data.

## Reference Implementations

| Implementation | URL | Notes |
|---------------|-----|-------|
| x3270 | https://gitlab.com/freedomgames/x3270 | Classic 3270 terminal emulator |
| Open 3270 | https://github.com/open3270/open3270 | Modern Java-based 3270 |
| IBM 3270 Emulation | IBM internal spec SC30-8403 | Official IBM 3270 protocol |
| RFC 1576 | https://tools.ietf.org/html/rfc1576 | TN3270 Enhanced Session Protocol |
| RFC 2355 | https://tools.ietf.org/html/rfc2355 | TN3270E Enhanced Session Protocol |
