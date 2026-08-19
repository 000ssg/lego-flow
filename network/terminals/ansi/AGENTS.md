# network / terminals / ansi — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

ANSI X3.64 standard terminal. Extends VT100 but filters out DEC private modes (ESC [ ? ...). This provides a standardized terminal profile for applications that should not rely on DEC extensions.

## Key Class

- `ANSITerminal` — extends VT100Terminal, intercepts DEC private sequences

## Testing

- Tests: 6
- Test that DEC private modes are ignored
- Test standard ANSI sequences still work

Total tests: 6
