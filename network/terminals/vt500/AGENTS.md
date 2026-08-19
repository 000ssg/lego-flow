# network / terminals / vt500 — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

VT500 terminal emulator. Extends VT400 with DEC character set support and window host commands.

## Key Class

- `VT500Terminal` — extends VT400Terminal, CharSet enum, G0/G1/active charset, DCS handler

## Character Sets

| CharSet | Description |
|---------|-------------|
| ASCII | Standard ASCII |
| DEC_SPECIAL | DEC Special Character and Line Drawing |
| UK | UK character set |
| FRENCH | French character set |
| FRENCH_CANADIAN | French-Canadian character set |
| INTERNATIONAL | International character set |
| SCANDINAVIAN | Scandinavian character set |
| GERMAN | German character set |
| USER_DEFINED | User-defined character set |

## Testing

- Tests: 6
- Test character set selection
- Test G0/G1 switching

Total tests: 6
