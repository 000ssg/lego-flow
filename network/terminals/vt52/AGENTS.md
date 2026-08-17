# network / terminals / vt52 — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

VT52 terminal emulator. Unlike other terminal types, VT52 does NOT extend AbstractTerminal — it has its own independent state machine using ESC+letter commands.

## Key Class

- `VT52Terminal` — implements Terminal, own parser (3 states: DATA, ESCAPE, Y_ADDRESS)

## VT52 Command Reference

| Sequence | Function |
|----------|----------|
| ESC I | Cursor right 1 |
| ESC F | Cursor left 1 |
| ESC S | Cursor up 1 |
| ESC R | Cursor down 1 |
| ESC E | Clear to end of line |
| ESC D | Line feed (scroll if at bottom) |
| ESC J | Clear display |
| ESC Y row col | Cursor address (row/col = value + 32) |
| ESC = | Application keypad |
| ESC &gt; | Numeric keypad |
| ESC &lt; | Normal keypad |

## Testing

- Tests: 15
- Test cursor addressing with boundary values
- Test clear and erase operations
- Test scroll on line feed at bottom

Total tests: 15
