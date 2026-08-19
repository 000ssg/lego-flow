# network / terminals / xterm — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

XTERM terminal emulator. Extends ANSITerminal (which extends VT100) with modern terminal features: 256-color, true color, mouse tracking, bracketed paste, synchronized output, underline styles, and overline.

## Key Class

- `XTERMTerminal` — extends ANSITerminal, handles XTERM-specific DEC modes and SGR extensions

## XTERM DEC Private Modes

| Mode | Name | Effect |
|------|------|--------|
| 1000 | Button event tracking | Normal mouse |
| 1002 | Highlight tracking | Mouse on drag |
| 1003 | All motion tracking | Mouse on every move |
| 1004 | Focus event tracking | Send focus gained/lost |
| 1006 | SGR extended mouse | Extended mouse reporting |
| 1015 | URXVT mouse mode | URXVT-style mouse |
| 2004 | Bracketed paste | CSI 200~ ... CSI 201~ |
| 2026 | Synchronized output | CSI 2026~ ... CSI 2026~ |

## XTERM SGR Extensions

| Code | Meaning |
|------|---------|
| 38;5;n | 256-color foreground |
| 38;2;r;g;b | True color foreground (RGB) |
| 48;5;n | 256-color background |
| 48;2;r;g;b | True color background (RGB) |
| 4:n | Underline style (0-5) |
| 53 | Overline on |
| 55 | Overline off |

## Testing

- Tests: 25
- Test 256-color and true color SGR
- Test mouse mode toggling
- Test bracketed paste, sync mode, focus tracking
- Test underline styles and overline

Total tests: 25
