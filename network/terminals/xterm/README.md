# Lego Flow Terminals — XTERM

XTERM terminal emulator with modern terminal extensions.

## Features

- All ANSI/VT100 features (inherited)
- **256-color palette** (SGR 38;5;n / 48;5;n)
- **True color RGB** (SGR 38;2;r;g;b / 48;2;r;g;b)
- Mouse tracking modes (DECSET 1000–1006, 1015–1016)
- Bracketed paste mode (DECSET 2024)
- Synchronized output mode (DECSET 2026)
- Focus event tracking (DECSET 1004)
- Underline styles (SGR 4:0–4:5)
- Extended text decorations (SGR 53 — overline)
- Icon/window title (OSC 0, 1, 2)
- DCS handling for DECRQSS

## Quick Start

```java
TerminalConfig config = TerminalConfig.builder()
        .rows(24).cols(80).colorDepth(256).build();
Terminal terminal = XTERMTerminal.create(config);

// 256-color foreground
terminal.feed("\u001B[38;5;196mRed\u001B[0m".getBytes());

// True color RGB
terminal.feed("\u001B[38;2;255;128;0mOrange\u001B[0m".getBytes());

// Enable mouse tracking
terminal.feed("\u001B[?1006h".getBytes());
```

## Compatibility

- **xterm** — the original X Window System terminal emulator
- Compatible with modern terminals: GNOME Terminal, Konsole, iTerm2, Terminal.app, Windows Terminal
- Superset of ANSI X3.64 + VT100 DEC private modes + XTERM extensions

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md) | [Development Guide](AGENTS.md)
