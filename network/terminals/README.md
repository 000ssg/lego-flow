# Lego Flow Terminals

Reusable terminal emulation framework for Java. Provides implementations for VT52, VT100, VT200, VT400, VT500, ANSI, and XTERM terminals with a shared display model and escape parser.

## Modules

| Module | Description | Tests |
|--------|-------------|-------|
| [terminals-base](terminals-base/) | Core abstractions: Terminal, DisplayModel, Screen, Cursor, TermAttr, EscapeParser | 83 |
| [vt52](vt52/) | VT52 terminal — ESC+letter commands, no CSI | 15 |
| [vt100](vt100/) | VT100 terminal — full DEC protocol, SGR, DEC private modes | 30 |
| [vt200](vt200/) | VT200 terminal — video reverse, function keys | 6 |
| [vt400](vt400/) | VT400 terminal — 2-window, extended SGR | 6 |
| [vt500](vt500/) | VT500 terminal — DEC charsets, window commands | 6 |
| [ansi](ansi/) | ANSI X3.64 — standardized subset, no DEC private | 6 |
| [xterm](xterm/) | XTERM — 256/true color, mouse, bracketed paste, sync | 25 |

## Architecture

```
Terminal (interface)
├── AbstractTerminal — escape parser, control chars, CSI routing, events
│   ├── VT100Terminal — full VT100 + DEC private modes + SGR
│   │   ├── VT200Terminal — video reverse
│   │   │   └── VT400Terminal — 2-window, extended SGR
│   │   │       └── VT500Terminal — DEC charsets
│   │   └── ANSITerminal — ANSI X3.64 (no DEC private)
│   │       └── XTERMTerminal — 256/true color, mouse, sync
│   └── VT52Terminal — independent, ESC+letter commands
└── TerminalFactory — registry-based creation by type string
```

## Inheritance Hierarchy

- **VT52** — standalone, different command set (no CSI)
- **VT100 → VT200 → VT400 → VT500** — each variant adds capabilities
- **ANSI** extends VT100, filters DEC private modes
- **XTERM** extends ANSI, adds color/mouse/sync/title extensions

## Usage

```java
// Create any terminal type
TerminalConfig config = TerminalConfig.builder()
        .rows(24).cols(80).colorDepth(256).build();

Terminal terminal = TerminalFactory.create("xterm", config);

// Or use the type-specific factory
Terminal xterm = XTERMTerminal.create(config);
Terminal vt100 = VT100Terminal.create(config);

// Feed data
terminal.feed(data);

// Render
List<String> lines = terminal.render();

// Events
terminal.addEventListener(event -> {
    // React to display changes, cursor movement, title changes
});
```

## Use Cases

- **Telnet server** — terminal emulation behind the Telnet protocol (see telnet-gateway)
- **SSH terminal** — reuse in SSH protocol implementations
- **CLI applications** — programmatic terminal output
- **Swing/JavaFX** — terminal widget for desktop apps
- **Web-based** — terminal emulation for browser-based consoles
