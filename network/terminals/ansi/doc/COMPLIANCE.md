# ANSI Terminal — Compliance

## ANSI X3.64 / ECMA-48 Standard

The ANSI terminal implements the standardized ECMA-48 subset, filtering out
DEC private extensions (those starting with ESC [ ?). It extends VT100 but
silently ignores all DEC private mode sequences.

### CSI Cursor Motion (ECMA-48 §9.2)

| Sequence | Final | Function | Status |
|----------|-------|----------|--------|
| CSI n A | A | Cursor Up n | ✅ Implemented (from VT100) |
| CSI n B | B | Cursor Down n | ✅ Implemented (from VT100) |
| CSI n C | C | Cursor Forward n | ✅ Implemented (from VT100) |
| CSI n D | D | Cursor Back n | ✅ Implemented (from VT100) |
| CSI n E | E | Cursor Next Line | ✅ Implemented (from VT100) |
| CSI n F | F | Cursor Prev Line | ✅ Implemented (from VT100) |
| CSI n G | G | Cursor Horizontal Absolute | ✅ Implemented (from VT100) |
| CSI r;c H | H | Cursor Position | ✅ Implemented (from VT100) |
| CSI r;c f | f | HVP | ✅ Implemented (from VT100) |
| CSI n d | d | Cursor Vertical Absolute | ✅ Implemented (from VT100) |

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
| 90–97 | Bright foreground | ✅ Implemented (from VT100) |
| 100–107 | Bright background | ✅ Implemented (from VT100) |

### DEC Private Modes Filtered

| Mode | Number | Status |
|------|--------|--------|
| DECCM | 1 | ❌ Filtered (not in ANSI standard) |
| DECSCNM | 5 | ❌ Filtered (not in ANSI standard) |
| DECORM | 6 | ❌ Filtered (not in ANSI standard) |
| DECAWM | 7 | ❌ Filtered (not in ANSI standard) |
| DECCOLM | 40 | ❌ Filtered (not in ANSI standard) |

### Inherited Features

| Feature | Status |
|---------|--------|
| Cursor save/restore (ESC 7/8, CSI s/u) | ✅ Implemented (from VT100) |
| Scroll region (CSI r) | ✅ Implemented (from VT100) |
| Tab stops (HTS/TBC) | ✅ Implemented (from VT100) |
| EUT (repeat preceding char) | ✅ Implemented (from VT100) |
| OSC title (0, 1, 2) | ✅ Implemented (from VT100) |

#
### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — passed through to VT100 (intermediates are `?$`, not `?`) | ✅ Inherited |
# Known Limitations

1. **All DEC private modes filtered** — Sequences starting with ESC [ ? are
   silently ignored, ensuring strict ANSI compliance
2. **No DEC device attributes** — DA1/DSR not available (DEC extension)
3. **Origin mode always off** — DECORM not available in ANSI mode
4. **No application keypad mode** — DECKPAM not available in ANSI mode
5. **No HPT/VPA decimal** — H'P/T (0x60) not implemented

## Verification

| Feature | Test Verification |
|---------|-----------------|
| DEC private mode filtering | `ANSITerminalTest.testDecPrivateFiltered` |
| Inherited VT100 features | `ANSITerminalTest.testCursorMotion`, etc. |
| Reset | `ANSITerminalTest.testReset` |

---

**Last Updated**: 2026-08-17
