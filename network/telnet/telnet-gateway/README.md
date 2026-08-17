# Lego Flow Telnet — Gateway

Bridges Telnet protocol to terminal emulators.

## Features

- `TelnetGateway` — high-level bridge between Telnet protocol and terminal emulation
- Automatic IAC stripping on input (protocol → terminal)
- Automatic IAC escaping on output (terminal → peer)
- Automatic option negotiation (ECHO, SUPPRESS_GO_AHEAD, TTYPE, NAWS)
- Echo control (enable/disable)
- Gateway event listeners (CONNECTED, NEGOTIATED, RESIZED, TTYPE_EXCHANGED, DISCONNECTED)
- Custom negotiator support

## Quick Start

```java
TerminalConfig config = TerminalConfig.builder()
        .rows(24).cols(80).build();
Terminal terminal = VT100Terminal.create(config);

TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
        .writer(bytes -> socket.getOutputStream().write(bytes))
        .build();

// Feed data from socket
gateway.feed(socket.read(buffer));

// Terminal receives clean application data (IAC stripped)
// Responses are automatically IAC-escaped

// Send application data to peer
gateway.send("Welcome!\n");

// Listen for events
gateway.addEventListener(event -> {
    if (event == GatewayEvent.RESIZED) {
        // Terminal was resized by peer
    }
});
```

## Architecture

```
Remote Peer ←→ [Telnet Connection] ←→ [Gateway] ←→ [Terminal Emulator]
                    ↑                          ↑
              IAC stripping               IAC escaping
              Option negotiation          Auto ECHO/TTYPE/NAWS
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md) | [Development Guide](AGENTS.md)
