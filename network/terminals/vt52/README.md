# Lego Flow Terminals — VT52

VT52 terminal emulator with the original DEC command set.

## Features

- VT52 command set (ESC + single letter)
- ESC Y row col — cursor addressing (value + 32 encoding)
- ESC I/F/S/R — cursor motion (right, left, up, down)
- ESC E — clear to end of line
- ESC D — line feed with scroll
- ESC J — clear display
- ESC = / &gt; / &lt; — keypad modes (application, numeric, normal)
- No color support (monochrome terminal)

## Quick Start

```java
TerminalConfig config = TerminalConfig.builder().rows(24).cols(80).build();
Terminal terminal = VT52Terminal.create(config);
terminal.feed("Hello\n".getBytes());
List<String> lines = terminal.render();
```

## Compatibility

- **DEC VT52** (1971) — original implementation
- Uses a completely different command set from VT100
- No CSI sequences, no SGR attributes, no color
- Cursor addressing: ESC Y (row+32) (col+32)

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md) | [Development Guide](AGENTS.md)
