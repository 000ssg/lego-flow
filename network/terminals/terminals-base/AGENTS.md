# network / terminals / base — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md) for requirements documentation, architecture practices, git commit rules, coding conventions, testing practices, JDK 25 features, and dual API design.
>
> This file only covers module-specific details not covered by the root guide.

## Module Purpose

The `terminals-base` module provides the core abstractions for terminal emulation. All terminal type modules (vt52, vt100, vt200, vt400, vt500, ansi, xterm) depend on this module. It defines the `Terminal` interface, `AbstractTerminal` base class, display model, screen buffer, cursor, text attributes, and escape sequence parser.

## Key Interfaces

- `Terminal` — core contract: feed, render, cursor, config, events
- `AbstractTerminal` — base class with escape parser, control char handling, CSI routing, event system
- `TerminalConfig` — immutable config: rows, cols, colorDepth, autoWrap, originMode, tabStops
- `DisplayModel` — mutable state: screen, cursor, current attributes, scroll region, title, iconTitle
- `Screen` — 2D char buffer with scroll region, insert/delete operations, rendering
- `Cursor` — 1-based position (row, col) with relative movement
- `TermAttr` — text attributes with builder: bold, dim, italic, underline, blink, reverse, hidden, strikethrough, fg/bg color (8-color, 256-color, RGB)
- `EscapeParser` — byte-level state machine for ESC, CSI (with subparams), DCS, OSC sequences
- `CSIParams` — parsed CSI parameters with final byte, intermediates, subparameter support
- `KeyTranslator` — raw key → escape sequence translation
- `TerminalFactory` — registry-based terminal creation by type string
- `TerminalEventListener` — callback for display changes, cursor movement, title changes

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `config` | `TerminalConfig` — immutable terminal configuration |
| `display` | `DisplayModel`, `Screen`, `Cursor`, `TermAttr`, `Character` — visual state |
| `escape` | `EscapeParser`, `CSIParams` — escape sequence parsing |
| `event` | `TerminalEvent`, `TerminalEventListener` — event callbacks |
| `io` | `Terminal`, `AbstractTerminal`, `KeyTranslator`, `TerminalFactory` — I/O contract and factory |

## TermAttr API

- `fgMode()`/`bgMode()`: 0=8-color, 1=256-color, 2=RGB
- `fgColor()`/`bgColor()`: 256-color index (0-255) or 0xRRGGBB
- `foreground()`/`background()`: 0-7 for 8-color mode
- Builder: `foreground(int)`, `foreground256(int)`, `foregroundRgb(int)` (same for bg)

## Escape Parser States

- `INITIAL` — waiting for ESC (0x1B)
- `ESCAPE` — received ESC, waiting for intro char (`[`, `P`, `]`, etc.)
- `CSI` — parsing CSI parameters (digits, semicolons, colons for subparams)
- `CSI_INTERMEDIATE` — parsing intermediate bytes (0x20-0x2F)
- `CSI_FINAL` — received final byte (0x40-0x7E), dispatch handler
- `DCS_INTERMEDIATE` — parsing DCS intermediate bytes
- `DCS_DATA` — accumulating DCS data until ST (ESC \)
- `OSC` — parsing OSC string until BEL (0x07) or ST

## Sentinel Convention

CSI parameters use `-1` as an internal sentinel for "not specified". When parameters are accessed via `CSIParams.get(index, default)`, a sentinel (-1) resolves to the provided default. This matches VT100 terminal behavior where omitted parameters use defaults.

## Testing Practices

- No Thread.sleep — use latch-based synchronization
- Test all escape sequences with byte-level input
- Verify cursor position after every operation
- Test boundary conditions (row 1, row max, col 1, col max)
- Test scroll region boundaries

## Test Counts

- `TerminalConfigTest` — config builder, defaults
- `ScreenTest` — 2D buffer, scroll, insert/delete
- `CursorTest` — position, movement
- `TermAttrTest` — attributes, color modes
- `CharacterTest` — char cell model
- `DisplayModelTest` — display state, render
- `EscapeParserTest` — CSI parsing, subparams, OSC, DCS
- `CSIParamsTest` — parameters, sentinels, subparams
- `KeyTranslatorTest` — key → escape sequence translation

Total tests: ~83
