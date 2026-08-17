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

1. **No CSI sequences** — VT52 uses ESC+letter protocol only (historically correct)
2. **No SGR** — Visual attributes via ESC # n only (historically correct)
3. **No color support** — VT52 had no color capability (historically correct)
4. **No double-width/double-height** — ESC # 6 not implemented (display-dependent)
5. **No line drawing character set** — VT52 line drawing not supported
6. **No inverted character** — ESC 9 not implemented
7. **Keypad modes are tracked but not translated** — =/> keys set state but don't
   affect key translation output (output layer responsibility)
8. **No scroll region** — VT52 has no scroll region concept (entire screen scrolls)
9. **No character sets** — VT52 uses only ASCII (DEC Special requires VT100+)

## Verification

| Feature | Test Verification |
|---------|-----------------|
| Cursor motion (I/F/S/R) | `VT52TerminalTest.testCursorMotion<direction>` |
| Cursor address (ESC Y) | `VT52TerminalTest.testCursorAddress` |
| Clear operations (J/K/E) | `VT52TerminalTest.testClear<Operation>` |
| Line feed / reverse line feed | `VT52TerminalTest.testLineFeed`, `testReverseLineFeed` |
| Reverse video / bold | `VT52TerminalTest.testReverseVideo`, `testBold` |
| Character output | `VT52TerminalTest.testCharacterOutput` |
| Reset | `VT52TerminalTest.testReset` |

---

**Last Updated**: 2026-08-17
