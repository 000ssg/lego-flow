# network / terminals / vt400 — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

VT400 terminal emulator. Extends VT200 with extended SGR codes and 2-window support.

## Key Class

- `VT400Terminal` — extends VT200Terminal, handles CSI n t (window select), SGR 82-89/92-99

## Testing

- Tests: 6
- Test window selection
- Test extended SGR codes

Total tests: 6
