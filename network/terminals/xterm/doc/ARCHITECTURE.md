# XTERM Terminal — Architecture

## Overview

The XTERM terminal emulator extends ANSI/VT100 with modern terminal features. It is the most feature-rich terminal in the hierarchy.

## Inheritance

```mermaid
graph TD
    T["Terminal (interface)"]
    AT["AbstractTerminal"]
    VT100["VT100Terminal"]
    ANSI["ANSITerminal"]
    XT["XTERMTerminal"]
    
    T --> AT --> VT100 --> ANSI --> XT
    
    XT --- C["Color: 256-palette, true RGB"]
    XT --- M["Mouse: normal, highlight, cell motion"]
    XT --- P["Paste: bracketed paste mode"]
    XT --- S["Sync: synchronized output"]
    XT --- F["Focus: focus event tracking"]
    XT --- U["Underline: styles 0-5"]
    XT --- O["Overline: SGR 53/55"]
```

## Extended SGR Processing

XTERM reinterprets SGR parameters 38/48 to handle extended color:
- `38;5;n` — 256-color palette index
- `38;2;r;g;b` — true RGB color
- Same pattern for background (48;5;n, 48;2;r;g;b)

The handler uses subparameter-aware parsing: after consuming 38 or 48, it reads the next parameter to determine the mode (5 for 256-color, 2 for RGB).

## Mouse Mode Model

```mermaid
stateDiagram-v2
    [*] --> OFF
    OFF --> NORMAL : DECSET 1000
    OFF --> HIGHLIGHT : DECSET 1002
    OFF --> CELL_MOTION : DECSET 1003
    NORMAL --> OFF : DECRST 1000
    HIGHLIGHT --> OFF : DECRST 1002
    CELL_MOTION --> OFF : DECRST 1003
```

---

**Last Updated**: 2026-08-17
