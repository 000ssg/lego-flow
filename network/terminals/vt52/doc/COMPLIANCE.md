# VT52 Terminal — Compliance

## DEC VT52 Reference

The VT52 is the simplest DEC terminal, using ESC+letter commands for control.
Unlike later DEC terminals, the VT52 has no CSI sequences and no SGR support.
Visual attributes (reverse video, bold) are set via ESC # n sequences.

### ESC+Letter Commands

| Command | Sequence | Function | Status |
|---------|----------|----------|--------|
| I | ESC I | Cursor Forward 1 | ✅ Implemented |
| F | ESC F | Cursor Back 1 | ✅ Implemented |
| S | ESC S | Cursor Up 1 | ✅ Implemented |
| R | ESC R | Cursor Down 1 | ✅ Implemented |
| E | ESC E | Clear to End of Line | ✅ Implemented |
| D | ESC D | Line Feed + CR (scrolls at bottom) | ✅ Implemented |
| J | ESC J | Clear Screen + Home Cursor | ✅ Implemented |
| K | ESC K | Clear from Cursor to End of Screen | ✅ Implemented |
| U | ESC U | Reverse Line Feed (scrolls at top) | ✅ Implemented |
| Y | ESC Y p q | Cursor Address (row, col, value+32) | ✅ Implemented |
| = | ESC = | Application Keypad Mode | ✅ Implemented |
| > | ESC > | Numeric Keypad Mode | ✅ Implemented |
| < | ESC < | Normal Keypad Mode | ✅ Implemented |

### ESC # n Sequences

| Sequence | Function | Status |
|----------|----------|--------|
| ESC # 3 | Reverse Video (affects subsequent output) | ✅ Implemented |
| ESC # 8 | Bold (affects subsequent output) | ✅ Implemented |
| ESC # 4 | Single-width line (no-op) | ✅ Implemented |
| ESC # 6 | Double-height characters | ❌ Not implemented (display-dependent) |

### Character Encoding

| Feature | Status |
|---------|--------|
| VT52 Y-address encoding (value + 32) | ✅ Implemented |
| Printable ASCII range (0x20–0x7E) | ✅ Implemented |

### Control Characters

| Character | Behavior | Status |
|-----------|----------|--------|
| CR (0x0D) | Move to column 1 | ✅ Implemented |
| LF (0x0A) | Line Feed + Carriage Return (VT52-specific) | ✅ Implemented |
| BS (0x08) | Cursor Back 1 | ✅ Implemented |
| HT (0x09) | Advance to next tab stop | ✅ Implemented |

### Character Output

| Feature | Status |
|---------|--------|
| Printable characters (0x20–0x7E) | ✅ Implemented |
| Reverse video attribute applied to output | ✅ Implemented |
| Bold attribute applied to output | ✅ Implemented |

## Known Limitations

### No CSI Sequences

**Status**: VT52 uses ESC+letter protocol only (historically correct).

**Reason**: The VT52 predated CSI (DEC introduced CSI with the VT100 in 1978).
The VT52 was designed as a low-cost terminal using simple 2-byte escape sequences
(ESC + letter). Implementing CSI sequences would make it a VT100, not a VT52.
This is a deliberate design decision, not a limitation.

### No SGR

**Status**: Visual attributes via ESC # n only (historically correct).

**Reason**: SGR (Select Graphic Rendition) was introduced with the VT100.
The VT52 only supports reverse video (ESC # 3) and bold (ESC # 8) via the
ESC # n mechanism. There is no color support or fine-grained attribute
control. This is historically accurate.

### No Color Support

**Status**: VT52 had no color capability (historically correct).

**Reason**: The VT52 was a monochrome terminal. Color support was introduced
with the VT52+ color variant and fully realized in the VT220/VT320 series.
Implementing color would make this a different terminal.

### No Double-Width/Double-Height

**Status**: ESC # 6 not implemented (display-dependent).

**Reason**: ESC # 6 (double-height) requires the terminal to render characters
at twice their normal height. This is fundamentally a display feature that
depends on the rendering backend (terminal emulator, web view, etc.). The
VT52 hardware had a specific display driver that supported this, but in a
software emulator it requires knowing the display dimensions and font metrics,
which the terminal emulator does not have access to. The escape sequence
is parsed and acknowledged, but the visual effect cannot be produced without
display-specific knowledge.

### No Line Drawing Character Set

**Status**: VT52 line drawing not supported.

**Reason**: The VT52 had no alternate character set with line-drawing
characters. Line drawing (box-drawing, special symbols) was introduced with
the VT100's DEC Special charset. Applications needing line drawing should
use VT100 or later terminal types.

### No Inverted Character

**Status**: ESC 9 not implemented.

**Reason**: ESC 9 (inverted character) is a DEC VT100+ feature that toggles
inverted display mode. The VT52 only has reverse video (ESC # 3) which
inverts the entire screen or output stream. ESC 9 inverts individual
characters, which is a different mechanism.

### Keypad Modes Tracked But Not Translated

**Status**: =/> keys set state but don't affect key translation output.

**Reason**: The VT52 supports three keypad modes (application, numeric,
normal) but the output layer (keyboard driver, SSH channel, etc.) is
responsible for generating the appropriate escape sequences. The terminal
emulator receives already-encoded input — it does not generate key events.
The keypad mode state is tracked for protocol compliance (the correct
state can be reported to the host) but does not affect the emulator's
internal state.

### No Scroll Region

**Status**: VT52 has no scroll region concept (entire screen scrolls).

**Reason**: The VT52 hardware scrolls the entire 24-line screen. It has no
configurable scroll region like the VT100's DECSTBM (ESC[r;r). This is
a hardware limitation of the VT52 that is historically correct.

### No Character Sets

**Status**: VT52 uses only ASCII (DEC Special requires VT100+).

**Reason**: The VT52 uses a fixed 7-bit ASCII character set. It has no
mechanism for charset switching (no ESC ( or ESC ) sequences). DEC Special
and other character sets were introduced with the VT100. Applications
requiring non-ASCII characters should use VT100 or later.

---

**Last Updated**: 2026-08-18
