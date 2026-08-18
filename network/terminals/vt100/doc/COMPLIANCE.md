# VT100 Terminal — Compliance

## DEC VT100 Reference

The VT100 introduced the CSI (Control Sequence Introducer) protocol that became
the basis for all modern terminal emulators. This module implements the full
VT100 command set.

### CSI Cursor Motion

| Sequence | Final | Function | Status |
|----------|-------|----------|--------|
| CSI n A | A | Cursor Up n | ✅ Implemented |
| CSI n B | B | Cursor Down n | ✅ Implemented |
| CSI n C | C | Cursor Forward n | ✅ Implemented |
| CSI n D | D | Cursor Back n | ✅ Implemented |
| CSI n E | E | Cursor Next Line n (CNL) | ✅ Implemented |
| CSI n F | F | Cursor Prev Line n (CPL) | ✅ Implemented |
| CSI n G | G | Cursor Horizontal Absolute (CHA) | ✅ Implemented |
| CSI r;c H | H | Cursor Position (CUP) | ✅ Implemented |
| CSI r;c f | f | Horizontal Vertical Position (HVP) | ✅ Implemented |
| CSI n d | d | Cursor Vertical Absolute (VPA) | ✅ Implemented |

### CSI Display Operations

| Sequence | Final | Function | Status |
|----------|-------|----------|--------|
| CSI n J | J | Erase in Display (ED) | ✅ Implemented |
| CSI n K | K | Erase in Line (EL) | ✅ Implemented |
| CSI n L | L | Insert Line (IL) | ✅ Implemented |
| CSI n M | M | Delete Line (DL) | ✅ Implemented |
| CSI n P | P | Delete Character (DCH) | ✅ Implemented |
| CSI n @ | @ | Insert Blank (ICH) | ✅ Implemented |
| CSI n X | X | Erase Character (ECH) | ✅ Implemented |

### SGR — Select Graphic Rendition

| Code | Attribute | Status |
|------|-----------|--------|
| 0 | Reset all | ✅ Implemented |
| 1 | Bold | ✅ Implemented |
| 2 | Dim | ✅ Implemented |
| 3 | Italic | ✅ Implemented |
| 4 | Underline | ✅ Implemented |
| 5 | Blink | ✅ Implemented |
| 7 | Reverse | ✅ Implemented |
| 8 | Hidden | ✅ Implemented |
| 9 | Strikethrough | ✅ Implemented |
| 22 | Bold/Dim off | ✅ Implemented |
| 23 | Italic off | ✅ Implemented |
| 24 | Underline off | ✅ Implemented |
| 25 | Blink off | ✅ Implemented |
| 27 | Reverse off | ✅ Implemented |
| 28 | Hidden off | ✅ Implemented |
| 29 | Strikethrough off | ✅ Implemented |
| 30–37 | Foreground colors (8-color) | ✅ Implemented |
| 39 | Default foreground | ✅ Implemented |
| 40–47 | Background colors (8-color) | ✅ Implemented |
| 49 | Default background | ✅ Implemented |
| 90–97 | Bright foreground | ✅ Implemented |
| 100–107 | Bright background | ✅ Implemented |

### DEC Private Modes (CSI ? Ps h/l)

| Mode | Name | Function | Status |
|------|------|----------|--------|
| 1 | DECCM | Application cursor keys | ✅ Implemented |
| 5 | DECSCNM | Reverse video | ✅ Implemented |
| 6 | DECORM | Origin mode | ✅ Implemented |
| 7 | DECAWM | Auto-wrap | ✅ Implemented |
| 40 | DECCOLM | Smooth scroll (clears screen, resets cursor) | ✅ Implemented |

### Device Attributes / Status

| Sequence | Function | Status |
|----------|----------|--------|
| CSI ? c | DA1 (Device Attributes) | ✅ Implemented |
| CSI 5 n | DSR (Device Status Report) | ✅ Implemented (response handled by transport) |
| CSI 6 n | CPR (Cursor Position Report) | ✅ Implemented (response handled by transport) |
| CSI ? $ p | DECRQM (query DEC private mode) | ✅ Implemented |

### Cursor Save/Restore

| Sequence | Function | Status |
|----------|----------|--------|
| ESC 7 | DECSC (save cursor) | ✅ Implemented |
| ESC 8 | DECRC (restore cursor) | ✅ Implemented |
| CSI s | DECSC (alternate form) | ✅ Implemented |
| CSI u | DECRC (alternate form) | ✅ Implemented |
| CSI 7 | DECSC (CSI form) | ✅ Implemented |
| CSI 8 | DECRC (CSI form) | ✅ Implemented |

### Other Sequences

| Sequence | Function | Status |
|----------|----------|--------|
| CSI r;c R | Scroll Region (DECSTBM) | ✅ Implemented |
| HTS (ESC H) | Tab Stop Set | ✅ Implemented |
| CSI n T | TBC (Tab Clear) | ✅ Implemented |
| CSI n b | EUT (Repeat Preceding Char) | ✅ Implemented |
| ESC = | DECKPAM (Application Keypad) | ✅ Implemented |
| ESC > | DECKPNM (Numeric Keypad) | ✅ Implemented |
| ESC # 3 | DECSED (Reversed Video) | ✅ Implemented |
| ESC # 8 | DECDBL (Bold/Double-Strike) | ✅ Implemented |

### Control Characters

| Character | Code | Function | Status |
|-----------|------|----------|--------|
| NUL | 0x00 | No effect | ✅ Implemented |
| BS | 0x08 | Backspace | ✅ Implemented |
| HT | 0x09 | Tab | ✅ Implemented |
| LF | 0x0A | Line Feed | ✅ Implemented |
| VT | 0x0B | Vertical Tab (→ LF) | ✅ Implemented |
| FF | 0x0C | Form Feed (→ LF) | ✅ Implemented |
| CR | 0x0D | Carriage Return | ✅ Implemented |
| IND | 0x84 | Index | ✅ Implemented |
| RI | 0x85 | Reverse Index | ✅ Implemented |
| DEL | 0x7F | Delete (no effect) | ✅ Implemented |

### OSC Sequences

| OSC | Function | Status |
|-----|----------|--------|
| 0;title | Window Title | ✅ Implemented |
| 1;icon | Icon Title | ✅ Implemented |
| 2;title | Window Title | ✅ Implemented |

## Known Limitations

### DA1 Response — Transport Layer Responsibility

**Status**: DA1 sequence is recognized but response generation is transport-layer
responsibility (emulator stores device type).

**Reason**: Device Attributes (DA1) is a terminal→host query. The emulator
knows its device type, but sending the response requires access to the transport
layer (socket, SSH channel, etc.). The base terminal framework separates the
emulation engine from the transport layer, so response generation is delegated.
Applications access the response via `readOutput()` on the terminal instance.

### DSR/CPR Responses — Transport Layer Responsibility

**Status**: Status reports trigger internally; actual response strings must be
sent by the transport layer.

**Reason**: Similar to DA1 — DSR (Device Status Report) and CPR (Cursor Position
Report) require transport-layer access to send the response bytes. The terminal
emulator generates the response string into its output buffer; the transport
layer reads and sends it. This separation ensures the emulation logic is
independent of transport implementation.

### DECSTBM Cursor Positioning

**Status**: Cursor reset to (1,1) on region change (some emulators use scroll
region top).

**Reason**: The VT100 spec states that changing the scroll region repositions
the cursor to row 1, column 1 of the new region. This behavior matches the
original VT100 hardware. Some later emulators (xterm, GNOME Terminal) position
the cursor at the current row within the new region. The difference is
negligible in practice because most applications reset cursor position after
changing scroll regions.

### No Line Wrapping Tracking

**Status**: Auto-wrap flag managed internally but not exposed via DSR queries.

**Reason**: The VT100 hardware does not expose the auto-wrap state as a
queryable register. It is an internal state. The DEC protocol does not define
a DSR sub-function for auto-wrap state. Only the DECAWM mode (mode 7) is
queryable via DECRQM, which returns 1 when auto-wrap is enabled.

### No Scroll History

**Status**: VT100 has no scrollback buffer concept.

**Reason**: The original VT100 hardware had no scrollback capability — it was
a pure display device with a fixed 24×80 or 24×132 character buffer.
Scrollback is a terminal-emulator feature added by software terminals (xterm,
GNOME Terminal, etc.) to support browsing command output. Implementing
scrollback would require a fundamentally different screen model with infinite
height, which conflicts with the VT100's fixed-display architecture.

**Comparison**: xterm has scrollback (configurable), but the VT100 specification
does not include it. Adding scrollback would be an extension beyond the VT100
spec.

---

**Last Updated**: 2026-08-18
