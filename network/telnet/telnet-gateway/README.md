# Lego Flow Telnet — Gateway

Bridges Telnet protocol to terminal emulators.

## Features

- `TelnetGateway` — high-level bridge between Telnet protocol and terminal emulation
- Automatic IAC stripping on input (protocol → terminal)
- Automatic IAC escaping on output (terminal → peer)
- Automatic option negotiation (ECHO, SUPPRESS_GO_AHEAD, TTYPE, NAWS, BINARY, LINEMODE, NEW_ENV, TERMINAL_SPEED)
- Echo control (enable/disable)
- **Sealed event hierarchy** — type-safe events with structured data (replaces enum-based events)
- DM (Data Mark) synchronization per RFC 854
- Null-safe operations (feed/null, send/null, forTerminal(null))
- Auto-flush on `feed()` — data flows through immediately
- Accessor methods for all handler components
- Custom negotiator support

## Gateway Events (Sealed Interface)

All events implement `TelnetGateway.GatewayEvent` sealed interface:

```java
sealed interface GatewayEvent permits
    ConnectedEvent, DisconnectedEvent, NegotiatedEvent,
    ResizeEvent, TtyEvent, CommandEvent, DmEvent,
    BinaryEvent, EnvEvent, LineEvent,
    LinemodeActiveEvent, LinemodeInactiveEvent {
    String typeLabel();
}
```

Each event carries structured data:

```java
// Pattern match on events
gateway.addListener(event -> {
    if (event instanceof GatewayEvent.ResizeEvent e) {
        System.out.println("Resized to " + e.cols() + "x" + e.rows());
    } else if (event instanceof GatewayEvent.TtyEvent e) {
        System.out.println("Terminal type: " + e.type());
    } else if (event instanceof GatewayEvent.BinaryEvent e) {
        System.out.println("Binary: local=" + e.localBinary() + " remote=" + e.remoteBinary());
    } else if (event instanceof GatewayEvent.EnvEvent e) {
        System.out.println("Env: " + e.name() + "=" + e.variable());
    } else if (event instanceof GatewayEvent.LineEvent e) {
        System.out.println("Line: " + e.line());
    } else if (event instanceof GatewayEvent.DmEvent e) {
        System.out.println("DM: " + e.typeLabel());
    }
});
```

| Event | Data | When Fired |
|-------|------|------------|
| `ConnectedEvent` | — | Connection established |
| `DisconnectedEvent` | — | Connection closed |
| `NegotiatedEvent` | `optionCode` | Option negotiation complete |
| `ResizeEvent` | `cols`, `rows` | NAWS resize via peer |
| `TtyEvent` | `type` | Peer sends terminal type (TTYPE IS) |
| `CommandEvent` | `command` | Single-byte Telnet command received |
| `DmEvent` | `isSync` | DM received / DM sync confirmed |
| `BinaryEvent` | `localBinary`, `remoteBinary` | Binary mode state change |
| `EnvEvent` | `name`, `variable` | New environment variable received |
| `LineEvent` | `line` | Line submitted via LINEMODE |
| `LinemodeActiveEvent` | — | LINEMODE activated |
| `LinemodeInactiveEvent` | — | LINEMODE deactivated |

## Quick Start

```java
TerminalConfig config = TerminalConfig.builder()
        .rows(24).cols(80).build();
Terminal terminal = VT100Terminal.create(config);

TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
        .writer(bytes -> socket.getOutputStream().write(bytes))
        .build();

// Feed data from socket (auto-flushed)
gateway.feed(socket.read(buffer));

// Terminal receives clean application data (IAC stripped)
// Responses are automatically IAC-escaped

// Send application data to peer
gateway.send("Welcome!\n");

// Listen for events using pattern matching
gateway.addListener(event -> {
    if (event instanceof GatewayEvent.ResizeEvent e) {
        System.out.println("Resized to " + e.cols() + "x" + e.rows());
    } else if (event instanceof GatewayEvent.TtyEvent e) {
        System.out.println("Terminal type: " + e.type());
    }
});
```

## Null Safety

```java
gateway.feed(null);      // No-op, no crash
gateway.feed(new byte[0]); // No-op, no crash
gateway.send(null);      // No-op, no crash
TelnetGateway.forTerminal(null); // Throws NullPointerException
```

## Terminal Types Supported

| Type | Class | Features |
|------|-------|----------|
| VT52 | `VT52Terminal` | Basic VT52 control sequences |
| VT100 | `VT100Terminal` | ANSI escape sequences, color support |
| VT200 | `VT200Terminal` | VT100 + alternate character set |
| VT400 | `VT400Terminal` | VT100 + soft reset, extended functions |
| VT500 | `VT500Terminal` | VT400 + DEC Peacock, true type setting |
| ANSI | `ANSITerminal` | ANSI X3.64 standard |
| XTERM | `XTERMTerminal` | 256-color, true color, mouse tracking, clipboard |

## Supported Telnet Options

| Option | Code | RFC | Handler |
|--------|------|-----|---------|
| BINARY | 0 | RFC 856 | `BinaryHandler` |
| ECHO | 1 | RFC 857 | Gateway echo control |
| SUPPRESS_GO_AHEAD | 3 | RFC 858 | Gateway suppress control |
| STATUS | 5 | RFC 859 | Negotiated but not processed |
| TIMING_MARK | 6 | RFC 860 | Negotiated but not processed |
| LINEMODE | 34 | RFC 1143 | `LinemodeHandler` |
| TTYPE | 24 | RFC 1091 | `TTYPEHandler` |
| NAWS | 31 | RFC 1073 | `NAWSHandler` |
| TERMINAL_SPEED | 32 | RFC 1079 | `SpeedHandler` |
| NEW_ENV | 252 | RFC 1408 | `NewEnvHandler` |

## Architecture

```
Remote Peer ←→ [Telnet Connection] ←→ [Gateway] ←→ [Terminal Emulator]
                    ↑                          ↑
              IAC stripping               IAC escaping
              Option negotiation          Auto ECHO/TTYPE/NAWS
              Subnegotiation              Binary/Linemode/NewEnv
```

## Demos

Run all telnet demos:
```bash
mvn test -pl demos -Dtest="**/telnet/**"
```

- `GatewayDemo` — Full gateway features with all terminal types
- `NegotiationDemo` — All negotiation handlers (TTYPE, NAWS, Speed, Binary, Linemode, NewEnv)
- `TelnetDemo` — Base protocol (IAC escaping, commands, negotiation, subnegotiation)

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md) | [Development Guide](AGENTS.md)
