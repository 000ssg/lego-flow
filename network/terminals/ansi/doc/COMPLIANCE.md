# ANSI Terminal — Compliance

## ANSI X3.64 / ECMA-48 Standard

The ANSI terminal implements the standardized ECMA-48 subset, filtering out DEC private extensions.

### CSI Cursor Motion (ECMA-48 §9.2)

| Sequence | Final | Function | Status |
|----------|-------|----------|--------|
| CSI n A | A | Cursor Up n | ✅ Implemented (from VT100) |
| CSI n B | B | Cursor Down n | ✅ Implemented (from VT100) |
| CSI n C | C | Cursor Forward n | ✅ Implemented (from VT100) |
| CSI n D | D | Cursor Back n | ✅ Implemented (from VT100) |
| CSI r;c H | H | Cursor Position | ✅ Implemented (from VT100) |
| CSI r;c f | f | HVP | ✅ Implemented (from VT100) |

### CSI Display (ECMA-48 §9.3)

| Sequence | Final | Function | Status |
|----------|-------|----------|--------|
| CSI n J | J | Erase in Display | ✅ Implemented (from VT100) |
| CSI n K | K | Erase in Line | ✅ Implemented (from VT100) |
| CSI n L | L | Insert Line | ✅ Implemented (from VT100) |
| CSI n M | M | Delete Line | ✅ Implemented (from VT100) |
| CSI n P | P | Delete Character | ✅ Implemented (from VT100) |
| CSI n @ | @ | Insert Blank | ✅ Implemented (from VT100) |
| CSI n X | X | Erase Character | ✅ Implemented (from VT100) |

### SGR (ECMA-48 §9.3.5)

| Code | Attribute | Status |
|------|-----------|--------|
| 0 | Reset all | ✅ Implemented (from VT100) |
| 1 | Bold | ✅ Implemented (from VT100) |
| 4 | Underline | ✅ Implemented (from VT100) |
| 5 | Blink | ✅ Implemented (from VT100) |
| 7 | Reverse | ✅ Implemented (from VT100) |
| 8 | Hidden | ✅ Implemented (from VT100) |
| 30–37 | Foreground colors | ✅ Implemented (from VT100) |
| 40–47 | Background colors | ✅ Implemented (from VT100) |

### DEC Private Modes Filtered

| Mode | Number | Status |
|------|--------|--------|
| DECCM | 1 | ❌ Filtered (not in ANSI standard) |
| DECORM | 6 | ❌ Filtered (not in ANSI standard) |
| DECAWM | 7 | ❌ Filtered (not in ANSI standard) |

### Device Control

| Sequence | Function | Status |
|----------|----------|--------|
| DC1 (0x11) | XON (resume) | ✅ Implemented |
| DC2 (0x12) | Group select | ✅ Implemented (no-op) |
| DC3 (0x13) | XOFF (pause) | ✅ Implemented |
| DC4 (0x14) | Restart | ✅ Implemented (no-op) |

### Known Limitations

1. **No HPT/VPA decimal** — H'P/T (0x60) not implemented
2. **No device reporting** — DSR queries not in standard scope
3. **No TAB manipulation** — CTLabs (HTS, TBC) not implemented
4. **Origin mode always off** — DECORM not available
