# Terminal Base — Compliance

## Escape Sequence Parsing

### ECMA-48 / ISO 6429 (Control Functions for Coded Character Sets)

| Section | Feature | Status |
|---------|---------|--------|
| 8.1.1 | CSI (ESC [) parsing | ✅ Implemented |
| 8.1.1 | Parameter bytes (0x30–0x3F) | ✅ Implemented |
| 8.1.1 | Intermediate bytes (0x20–0x2F) | ✅ Implemented |
| 8.1.1 | Final byte (0x40–0x7E) | ✅ Implemented |
| 8.1.2 | DCS (ESC P) strings | ✅ Implemented |
| 8.1.3 | OSC (ESC ]) strings | ✅ Implemented |
| 8.1.4 | APC (ESC _) strings | ✅ Implemented |
| 8.1.5 | PM (ESC ^) strings | ✅ Implemented |
| 8.2 | String Terminator (ST = ESC \) | ✅ Implemented |
| 8.2 | BEL terminator for OSC | ✅ Implemented |
| 9.1 | CSI parameter separator ';' | ✅ Implemented |
| 9.2 | CSI subparameter separator ':' | ✅ Implemented (flat list) |

### Control Characters

| Character | Code | Feature | Status |
|-----------|------|---------|--------|
| NUL | 0x00 | Null (ignored) | ✅ Implemented |
| BS | 0x08 | Backspace | ✅ Implemented |
| HT | 0x09 | Horizontal Tab | ✅ Implemented |
| LF | 0x0A | Line Feed | ✅ Implemented |
| VT | 0x0B | Vertical Tab (→ LF) | ✅ Implemented |
| CR | 0x0D | Carriage Return | ✅ Implemented |
| ESC | 0x1B | Escape introducer | ✅ Implemented |

### Display Model

| Feature | Status |
|---------|--------|
| 2D character grid | ✅ Implemented |
| Cursor position (row, col) | ✅ Implemented |
| Scroll region | ✅ Implemented |
| Auto-wrap tracking | ✅ Implemented |
| Saved cursor (DECSC/DECRC) | ✅ Implemented |

## Known Limitations

### No G0/G1 Character Set Switching

**Status**: SSO/SI (SO/SI) handled at terminal variant level.

**Reason**: Character set switching (G0/G1) is a DEC extension specific to
VT100+ terminals. The base module provides the infrastructure (escape parser,
event dispatch) but does not implement charset selection because:
1. G0/G1 switching requires a charset lookup table, which is terminal-specific
2. The base module should remain terminal-agnostic
3. Terminal variants (VT100, VT200, VT400, VT500) each have their own
   charset mapping strategy (VT500 has Unicode line-drawing, VT100 has
   standard ASCII)
4. The escape parser already provides the hooks (`handleCharSetSelector()`)
   for terminal variants to implement charset selection

### No DECSLRG (Large Characters)

**Status**: Parser recognizes the sequence but does not implement double-height
or double-width character display.

**Reason**: DECSLRG (DEC Large Character) is a deprecated VT300+ feature that
was never widely adopted. No modern terminal emulator implements it. Implementing
it would require a fundamentally different screen model (characters that span
multiple display rows/columns), which conflicts with the single-cell-per-character
model used throughout the framework.

**Comparison**: xterm, GNOME Terminal, and Windows Terminal do not support
DECSLRG. iTerm2 deprecated it in favor of font-level scaling.

### Tab Stop Management

**Status**: Implicit (every 8 columns); no explicit tab clear/set.

**Reason**: The base module provides tab stop management via `BitSet` (every
8 columns by default), which is the standard VT100 behavior. Explicit tab
clear/set (HTS, TBC) is implemented at the terminal variant level because:
1. The display model in the base module provides a simple line-buffer model
2. Terminal variants that need more sophisticated tab handling can override
3. Most applications assume standard 8-column tab stops
4. HTS/TBC are implemented in VT100+ variants; the base module provides the
   default behavior

### No Device Reporting

**Status**: DECRQCRA, DSR queries not implemented.

**Reason**: Device reporting sequences (DSR, DECRQCRA) are terminal-emulator
responsibilities, not base-module concerns. The base module provides:
1. An output buffer (`outputBuffer`) for terminals to generate responses
2. `readOutput()` method for transport layer to send responses
3. Recognition of device-reporting sequences via `handleDSR()` hook

The actual response generation is done by terminal variants (VT100, XTERM,
etc.) which know their device identity. The base module cannot generate correct
responses without knowing which terminal it emulates.

---

**Last Updated**: 2026-08-18
