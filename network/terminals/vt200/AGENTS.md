# network / terminals / vt200 — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

VT200 terminal emulator. Extends VT100 with video reverse (SGR 52/55) and function key support.

## Key Class

- `VT200Terminal` — extends VT100Terminal, intercepts SGR 52/55

## Testing

- Tests: 6
- Test video reverse toggle
- Test inheritance of VT100 features

Total tests: 6
