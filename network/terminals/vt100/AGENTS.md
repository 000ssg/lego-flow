# network / terminals / vt100 — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

VT100 terminal emulator. Extends AbstractTerminal with VT100-specific CSI sequences, DEC private modes, and SGR text attributes.

## Key Class

- `VT100Terminal` — extends AbstractTerminal, handles CSI 'm' (SGR), '?h/l' (DECSET/DECRST), '7/8/s/u' (cursor save/restore), 'r' (scroll region), 'b' (repeat char)

## VT100 SGR Codes

| Code | Meaning |
|------|---------|
| 0 | Reset all |
| 1 | Bold |
| 2 | Dim |
| 3 | Italic |
| 4 | Underline |
| 5 | Blink |
| 7 | Reverse |
| 8 | Hidden |
| 9 | Strikethrough |
| 30-37 | Foreground color (black through white) |
| 40-47 | Background color |
| 90-97 | Bright foreground |
| 100-107 | Bright background |

## DEC Private Modes

| Mode | Name | Effect |
|------|------|--------|
| 1 | DECCM | Application cursor keys |
| 4 | DECSLM | Smooth scroll |
| 5 | DECSCNM | Reverse video |
| 6 | DECORM | Origin mode |
| 7 | DECAWM | Auto-wrap |
| 40 | DECCOLM | Smooth scroll with wrap |

## Testing

- Tests: 30
- Test all cursor motion sequences
- Test SGR attribute combinations
- Test DECSET/DECRST mode toggling
- Test scroll region changes
- Test cursor save/restore

Total tests: 30
