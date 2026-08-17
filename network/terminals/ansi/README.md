# Lego Flow Terminals — ANSI

ANSI X3.64 standard terminal emulator.

## Features

- ANSI X3.64 standard subset of VT100
- CSI cursor motion (A, B, C, D, E, F, G, H, f, d)
- SGR attributes (0-7, 30-37, 40-47)
- Erase (J, K, X)
- Insert/delete (L, M, P, @)
- Device control (DC1-DC4)
- **No DEC private modes** — ESC [ ? sequences are silently ignored

## Compatibility

- **ANSI X3.64** — standardized terminal control sequences
- Subset of VT100 (without DEC private extensions)
- Base for XTERM

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Development Guide](AGENTS.md)
