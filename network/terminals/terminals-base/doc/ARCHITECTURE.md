# Terminals Base — Architecture

This document describes the architectural decisions for the terminals-base module.

---

## Module Purpose

The terminals-base module is the foundation of the terminal emulation framework. It provides the core abstractions that all terminal type implementations share: display rendering, cursor management, text attributes, escape sequence parsing, and the `Terminal` contract.

## Architecture Overview

```mermaid
graph TD
    T["Terminal (interface)"]
    AT["AbstractTerminal<br/>(escape parser, control chars, CSI routing, events)"]
    DM["DisplayModel<br/>(screen, cursor, attrs, scroll region, title)"]
    SC["Screen<br/>(2D char buffer, insert/delete, scroll)"]
    CU["Cursor<br/>(row/col position, 1-based)"]
    TA["TermAttr<br/>(SGR codes, color modes)"]
    EP["EscapeParser<br/>(state machine: ESC, CSI, DCS, OSC)"]
    CP["CSIParams<br/>(params, subparams, final byte, intermediates)"]
    TC["TerminalConfig<br/>(rows, cols, colorDepth, wrap, origin)"]
    TF["TerminalFactory<br/>(registry-based lookup)"]

    T --> AT
    AT --> DM
    AT --> EP
    AT --> TC
    DM --> SC
    DM --> CU
    DM --> TA
    EP --> CP
```

## Display Model

The `DisplayModel` is the central state holder. It contains:

```mermaid
classDiagram
    class DisplayModel {
        +Screen screen
        +Cursor cursor
        +TermAttr currentAttr()
        +TermAttr prevAttr()
        +boolean originMode
        +String title
        +String iconTitle
        +render() List~String~
        +putChar(int codepoint)
        +cursorPosition(int row, int col)
        +cursorUp(int n)
        +cursorDown(int n)
        +cursorForward(int n)
        +cursorBack(int n)
        +clear()
        +scrollDown()
        +scrollUp()
    }
    class Screen {
        +Character[][] buffer
        +int scrollTop
        +int scrollBottom
        +put(Character)
        +at(int row, int col) Character
        +insertLines(int row, int count)
        +deleteLines(int row, int count)
        +insertChars(int row, int col, int count)
        +deleteChars(int row, int col, int count)
        +eraseChars(int row, int col, int count)
    }
    class Cursor {
        +int row
        +int col
        +clone() Cursor
        +setPos(int row, int col)
        +back(int n)
    }
    class TermAttr {
        +boolean bold, dim, italic, blink, reverse, hidden, strikethrough
        +int underlineStyle
        +int fgMode, bgColor
        +int fgColor, bgColor
        +toBuilder() Builder
    }
    class Character {
        +int codepoint
        +TermAttr attr
    }

    DisplayModel --> Screen
    DisplayModel --> Cursor
    DisplayModel --> TermAttr
    Screen --> Character
```

## Escape Parser State Machine

```mermaid
stateDiagram-v2
    [*] --> INITIAL
    INITIAL --> INITIAL : non-ESC byte
    INITIAL --> ESCAPE : ESC (0x1B)
    ESCAPE --> CSI : '[' (0x5B)
    ESCAPE --> DCS_INT : 'P' (0x50)
    ESCAPE --> OSC : ']' (0x5D)
    ESCAPE --> INITIAL : other

    CSI --> CSI : param bytes (0x30-0x3F)
    CSI --> CSI_INT : intermediate (0x20-0x2F)
    CSI_INT --> CSI_INT : intermediate
    CSI_INT --> FINAL : final byte (0x40-0x7E)
    CSI --> FINAL : final byte

    DCS_INT --> DCS_INT : intermediate
    DCS_INT --> DCS_DATA : data byte
    DCS_DATA --> DCS_DATA : data
    DCS_DATA --> FINAL : ST (ESC \)

    OSC --> OSC : data
    OSC --> FINAL : BEL (0x07)
    OSC --> FINAL : ST (ESC \)

    FINAL --> INITIAL
```

## CSI Parameter Parsing

The parser handles the full CSI parameter format:
- Parameters are digits (0x30-0x39)
- Parameters are separated by semicolon (0x3B)
- Subparameters are separated by colon (0x3A) — XTERM extension
- Missing parameters default to sentinel (-1), resolved to 0 at emission time
- Intermediates (0x20-0x2F) modify meaning (e.g., `?` for DEC private modes)
- Final byte (0x40-0x7E) dispatches the handler

## Design Decisions

- **Immutable config**: `TerminalConfig` is immutable; changes require creating a new config
- **1-based indexing**: Cursor positions and screen coordinates use 1-based indexing (row 1, col 1 = top-left), matching terminal convention
- **Sentinel-based defaults**: CSI params use -1 as internal sentinel, resolved to 0 at emission, matching VT100 behavior where `CSI J` (erase display) defaults parameter to 0
- **Builder pattern**: `TermAttr` uses builder for fluent attribute construction
- **Registry-based factory**: `TerminalFactory` avoids compile-time cross-module dependencies; implementations register themselves at runtime
- **Event-driven**: `TerminalEventListener` allows external renderers to react to display changes without polling
- **Display separation**: `DisplayModel` holds all visual state, making it easy to swap renderers or inspect state

## Integration with Lego Flow

| Lego Flow Module | Usage in Terminals |
|------------------|-------------------|
| `blocks` | No direct dependency; terminals are stateful and self-contained |

## Thread Safety Model

- Terminals are **not thread-safe** by design
- All operations should occur on a single thread (e.g., the protocol handler thread)
- `listeners` list uses `CopyOnWriteArrayList` for safe iteration during event dispatch
- Rendering backends should synchronize access to terminal state

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md)
- [Root AGENTS.md](../../../../AGENTS.md)

---

**Last Updated**: 2026-08-17
