# Lego Flow Terminals — Base

Core terminal emulation abstractions: display model, screen buffer, cursor, text attributes, escape sequence parser, and the `Terminal` interface.

## Features

- `Terminal` interface — unified contract for all terminal emulators
- `AbstractTerminal` — base implementation with escape parsing, control chars, CSI handling, events
- `DisplayModel` — mutable display state: screen, cursor, attributes, scroll region, title
- `Screen` — 2D character buffer with scroll region, insert/delete line/char operations
- `Cursor` — position tracking with relative and absolute movement
- `TermAttr` — text attributes: SGR codes, 8-color, 256-color, true RGB color, bold, dim, italic, underline, blink, reverse, hidden, strikethrough
- `EscapeParser` — state machine for ESC, CSI, DCS, OSC sequences with subparameter (colon) support
- `CSIParams` — parameter list with sentinels, final byte, intermediates, subparameter-aware
- `TerminalConfig` — configuration: dimensions, colors, wrap mode, origin mode, tab stops
- `KeyTranslator` — convert raw key codes to terminal-specific escape sequences
- `TerminalFactory` — registry-based terminal type lookup
- `TerminalEventListener` — callback for display changes, cursor movement, title changes

## Quick Start

```java
// Create a terminal
TerminalConfig config = TerminalConfig.builder()
        .rows(24).cols(80).build();
Terminal terminal = TerminalFactory.create("vt100", config);

// Feed data
terminal.feed("\u001B[31mHello\u001B[0m\n".getBytes());

// Render output
List<String> lines = terminal.render();

// Listen for events
terminal.addEventListener(event -> {
    switch (event) {
        case CURSOR_MOVED -> System.out.println("cursor: " + terminal.cursor());
        case TITLE_CHANGED -> System.out.println("title: " + terminal.title());
        case DISPLAY_CHANGED -> /* redraw */;
    }
});
```

## Architecture

```
Terminal (interface)
├── AbstractTerminal — escape parser, control chars, CSI, events
│   ├── VT100Terminal — VT100 protocol + DEC private modes + SGR
│   │   ├── VT200Terminal — video reverse, function keys
│   │   │   └── VT400Terminal — 2-window, extended SGR, scroll history
│   │   │       └── VT500Terminal — DEC charsets, window commands
│   │   └── ANSITerminal — ANSI X3.64 (no DEC private)
│   │       └── XTERMTerminal — 256/true color, mouse, bracketed paste
│   └── VT52Terminal — independent, ESC+letter commands
├── TerminalFactory — registry lookup by type string
└── TerminalConfig — immutable configuration
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) — design decisions, data flow, escape parser state machine
- [Requirements](doc/REQUIREMENTS.md) — technical requirements and commit history
- [Development Guide](AGENTS.md) — coding conventions for this module
