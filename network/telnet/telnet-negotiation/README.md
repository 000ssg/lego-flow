# Lego Flow Telnet — Negotiation

Telnet option negotiation (RFC 855) with handlers for TTYPE, NAWS, and Speed.

## Features

- `OptionState` — 4-state machine per RFC 855 (OFF, OFF_DEF, ON_DEF, ON)
- `OptionRecord` — tracks local/remote state per option with state transition logic
- `OptionNegotiator` — manages all options, default-accepts, extensible via override hooks
- `TTYPEHandler` — RFC 1091 terminal type negotiation (IS/SEND suboptions)
- `NAWSHandler` — RFC 1073 window size negotiation (4-byte big-endian cols/rows)
- `SpeedHandler` — RFC 1079 terminal speed negotiation (IS/SEND suboptions)

## Quick Start

```java
OptionNegotiator negotiator = new OptionNegotiator();

// Handle incoming negotiation
TelnetCommand response = negotiator.negotiate(TelnetCommand.DO, TelnetOption.ECHO.code());

// TTYPE negotiation
TTYPEHandler ttype = TTYPEHandler.localType("xterm")
        .onRemoteType(type -> System.out.println("Remote type: " + type));
byte[] response = ttype.handle(data);

// NAWS negotiation (window resize)
NAWSHandler naws = NAWSHandler.localSize(80, 24)
        .onRemoteSize((cols, rows) -> System.out.println("Resized: " + cols + "x" + rows));
naws.handle(data);
```

## Compliance

- **RFC 855** — Telnet Option Specifications (4-state machine)
- **RFC 1091** — Telnet Terminal Type Option (TTYPE)
- **RFC 1073** — Negotiating Window Size (NAWS)
- **RFC 1079** — Telnet Terminal Speed Option

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md) | [Development Guide](AGENTS.md)
