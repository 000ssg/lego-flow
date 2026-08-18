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

### DECRQM (Query DEC Private Mode)

| Sequence | Description | Status |
|----------|-------------|--------|
| CSI ? $ p | DECRQM — passed through to VT100 (intermediates are `?$`, not `?`) | ✅ Inherited |

## Known Limitations

### All DEC Private Modes Filtered

**Status**: Sequences starting with ESC [ ? are silently ignored, ensuring
strict ANSI compliance.

**Reason**: The ANSI X3.64/ECMA-48 standard defines control functions without
the DEC private mode extensions (ESC [ ? Ps h/l). The ANSI terminal strictly
filters these out to maintain compliance with the ANSI standard. This is the
intended behavior — the ANSI terminal is designed for applications that need
portable, standards-compliant output without DEC extensions. Applications that
need DEC private modes should use VT100, VT200, or XTERM instead.

### No DEC Device Attributes

**Status**: DA1/DSR not available (DEC extension).

**Reason**: Device Attributes (DA1) and Device Status Report (DSR) are DEC
extensions, not part of the ANSI/ECMA-48 standard. The ANSI terminal does not
generate responses to these queries. The VT100 and XTERM variants provide
these features for applications that need device identification.

### Origin Mode Always Off

**Status**: DECORM not available in ANSI mode.

**Reason**: DECORM (DEC Origin Mode, mode 6) is a DEC private mode that changes
the cursor coordinate origin from (1,1) to the scroll region top-left. It is
not part of the ANSI standard. In ANSI mode, cursor coordinates are always
relative to the screen origin (1,1). Applications needing relative positioning
can use explicit cursor positioning (CUP) instead.

### No Application Keypad Mode

**Status**: DECKPAM not available in ANSI mode.

**Reason**: DECKPAM (Application Keypad Mode) is a DEC extension that maps
function keys to escape sequences. Keypad mode is an input-layer concern
(the keyboard driver determines what sequences function keys generate), not
a terminal emulation concern. The ANSI terminal filters DEC private modes
including DECKPAM.

### No HPT/VPA Decimal

**Status**: H'P/T (0x60) not implemented.

**Reason**: The horizontal/vertical tab (HPT/VTA) are non-standard extensions
that some DEC terminals used for decimal alignment. They are not part of
ECMA-48 or ANSI X3.64. The VT100+ variants recognize them but the ANSI
terminal (being strict-compliant) does not.

---

**Last Updated**: 2026-08-18
