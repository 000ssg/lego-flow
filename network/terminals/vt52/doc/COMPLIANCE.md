# VT52 Terminal — Compliance

## DEC VT52 Reference

### ESC+Letter Commands

| Command | Sequence | Function | Status |
|---------|----------|----------|--------|
| I | ESC I | Cursor Forward 1 | ✅ Implemented |
| F | ESC F | Cursor Back 1 | ✅ Implemented |
| S | ESC S | Cursor Up 1 | ✅ Implemented |
| R | ESC R | Cursor Down 1 | ✅ Implemented |
| E | ESC E | Clear to End of Line | ✅ Implemented |
| D | ESC D | Line Feed (LF + CR) | ✅ Implemented |
| J | ESC J | Clear to End of Screen | ✅ Implemented |
| K | ESC K | Clear Screen + Home | ✅ Implemented |
| Y | ESC Y p q | Cursor Address (row, col) | ✅ Implemented |
| = | ESC = | Application Keypad Mode | ✅ Implemented |
| > | ESC > | Numeric Keypad Mode | ✅ Implemented |
| < | ESC < | Normal Keypad Mode | ✅ Implemented (same as >) |

### Character Encoding

| Feature | Status |
|---------|--------|
| VT52 encoding (value + 32) for Y command | ✅ Implemented |
| Printable ASCII range (0x20–0x7E) | ✅ Implemented |

### Control Characters

| Character | Behavior | Status |
|-----------|----------|--------|
| CR (0x0D) | Move to column 1 | ✅ Implemented |
| LF (0x0A) | Line Feed + Carriage Return | ✅ Implemented |
| BS (0x08) | Cursor Back 1 | ✅ Implemented |
| HT (0x09) | Advance to next tab stop | ✅ Implemented |

### Known Limitations

1. **No S/R (reverse video)** — ESC # 3 not implemented
2. **No Bold intensity** — ESC # 8 not implemented
3. **No double-width/double-height** — not implemented
4. **No line drawing character set** — VT52 line drawing not supported
5. **No reverse line feed** — ESC U not implemented
6. **No inverted character** — ESC 9 not implemented
7. **Keypad modes are stateless** — =/> keys tracked but no key translation output
