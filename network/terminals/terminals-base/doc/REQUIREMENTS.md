# Terminals Base — Requirements

## Timeline Overview

- **Module Added**: August 2026
- **Tests**: 83
- **Dependencies**: blocks (DP/DF, Statistics)
- **Standards**: VT100 terminal protocol, ANSI X3.64

---

## Requirements

### Terminal Interface
1. Define `Terminal` interface with feed, render, cursor, config, events, reset
2. Support feeding data as bytes or strings
3. Provide cursor position and text attribute queries
4. Support event listeners for display changes, cursor movement, title changes
5. Provide terminal type identifier and color support check

### AbstractTerminal
1. Implement escape sequence parser integration
2. Handle control characters: NUL, BS, HT, LF/VT/FF, CR, IND, RI, DEL
3. Route printable characters through the parser
4. Handle CSI sequences: cursor motion, erase, insert/delete, SGR base
5. Handle OSC sequences for title setting
6. Provide protected hooks for subclass customization

### DisplayModel
1. Manage Screen, Cursor, and current text attributes
2. Support origin mode (cursor positioning relative to scroll region)
3. Support title and icon title
4. Render visible lines as strings
5. Handle cursor positioning with scroll region awareness
6. Support scroll down and scroll up operations
7. Handle character placement with wrapping

### Screen
1. 2D character buffer (rows × cols) with Character cells
2. Scroll region (top/bottom boundaries)
3. Insert lines within scroll region (shift down, discard bottom)
4. Delete lines within scroll region (shift up, clear new)
5. Insert chars within a line (shift right, discard end)
6. Delete chars within a line (shift left, clear end)
7. Erase chars within a line (replace with spaces)
8. Set/extend tab stops

### Cursor
1. 1-based position (row, col)
2. Relative movement: up, down, forward, back
3. Absolute positioning via setPos
4. Clone for save/restore

### TermAttr
1. SGR text attributes: bold, dim, italic, underline, blink, reverse, hidden, strikethrough
2. Underline styles: none, single, double, curly, dashed, dotted
3. Foreground/background color: 8-color (0-7), 256-color (0-255), true RGB (0xRRGGBB)
4. Mode tracking: 0=8-color, 1=256-color, 2=RGB
5. Builder pattern with fluent API
6. DEFAULT constant for reset state

### EscapeParser
1. State machine: INITIAL, ESCAPE, CSI, CSI_INTERMEDIATE, DCS, OSC
2. CSI parameter parsing with subparameter (colon) support
3. CSI sentinel (-1) for missing parameters
4. Intermediate byte handling
5. DCS data accumulation until ST (ESC \)
6. OSC string parsing until BEL or ST
7. Delegate to SequenceHandler for character output and sequence dispatch

### CSIParams
1. Parameter list with sentinel (-1) support
2. get(index) returns raw value (may be sentinel)
3. get(index, default) resolves sentinel to default
4. Final byte access
5. Intermediates string access
6. Subparameter list access (colons)

### TerminalConfig
1. Builder pattern with sensible defaults (80×24, 8-color, auto-wrap on)
2. Rows and columns (8-256 range)
3. Color depth (0-256 for 256-color, >256 for RGB)
4. Auto-wrap mode
5. Origin mode
6. Tab stops

### KeyTranslator
1. Convert raw key codes to terminal escape sequences
2. Support function keys (F1-F12)
3. Support arrow keys (normal and application mode)
4. Support modifier keys (Shift, Ctrl, Alt)
5. Support PageUp/PageDown, Home/End, Insert/Delete
6. Tab and backtab (Shift-Tab)

### TerminalFactory
1. Registry-based terminal type lookup
2. Register terminal types by string key
3. Create terminals from config using registered implementations

---

## Test Coverage

- TerminalConfigTest — config builder, defaults, validation
- ScreenTest — buffer operations, scroll, insert/delete
- CursorTest — position, movement, cloning
- TermAttrTest — attributes, color modes, builder, DEFAULT
- CharacterTest — char cell, default
- DisplayModelTest — state management, render, cursor positioning
- EscapeParserTest — CSI parsing, subparams, intermediates, OSC, DCS
- CSIParamsTest — parameters, sentinels, subparams
- KeyTranslatorTest — key to escape sequence translation

Total tests: 83

---

## Document Maintenance

- This document is append-only for commit sections
- Requirements updated with each feature addition

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (terminal-framework agent) |
| Agent tokens | ~120K |
| Agent tool calls | ~85 |
| Agent wall time | ~45 min |
| Files created/modified | 14 |
| Lines added/removed | +1800 / -0 |
| Tests added | 165 (total: 165) |

---

**Last Updated**: 2026-08-17
