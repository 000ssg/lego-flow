# Lego Flow Terminals — VT100

VT100 terminal emulator with full DEC protocol support.

## Features

- Full VT100 escape sequence support
- CSI cursor motion (CUU, CUD, CUF, CUB, CUP, HVP, CHA, VPA, CNL, CPL)
- SGR text attributes (0–7): reset, bold, dim, italic, underline, blink, reverse, hidden, strikethrough
- ANSI foreground/background colors (30–37, 40–47)
- Bright colors (90–97, 100–107)
- DECSET/DECRST for DEC private modes (origin mode, auto-wrap, DECCM, DECSCNM)
- DECKPAM/DECKPNM (application/numeric keypad)
- Device attributes (DA1)
- Scroll region (DECSTBM)
- Line operations (IL, DL)
- Character operations (ICH, DCH, ECH)
- Cursor save/restore (DECSC/DECRC)
- Repeat preceding character (EUT)
- OSC title setting (OSC 0, 1, 2)

## Quick Start

```java
TerminalConfig config = TerminalConfig.builder().rows(24).cols(80).build();
Terminal terminal = VT100Terminal.create(config);
terminal.feed("\u001B[31;1mBold Red\u001B[0m\n".getBytes());
List<String> lines = terminal.render();
```

## Compatibility

- **DEC VT100** (1978) — primary target
- VT101, VT102 — compatible (same character set, more memory)
- Superset of ANSI X3.64 (with DEC private extensions)
- Foundation for VT200, VT400, VT500, ANSI, and XTERM

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md) | [Development Guide](AGENTS.md)
