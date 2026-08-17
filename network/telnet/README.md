# Lego Flow Telnet

Telnet protocol implementation with terminal emulation gateway.

## Modules

| Module | Description | Tests |
|--------|-------------|-------|
| [telnet-base](telnet-base/) | RFC 854 protocol core: parser, connection, commands, options | 60 |
| [telnet-negotiation](telnet-negotiation/) | RFC 855 option negotiation: TTYPE, NAWS, Speed | 72 |
| [telnet-gateway](telnet-gateway/) | Protocol ↔ terminal bridge: IAC stripping, auto-negotiation | 32 |

## Architecture

```
Remote Peer ←→ [telnet-base: parser + connection]
                        ↓
                   [telnet-negotiation: option state machine]
                        ↓
                   [telnet-gateway: bridge]
                        ↓
                   [terminals: terminal emulator]
```

## Usage

```java
// Full gateway: Telnet + terminal
TerminalConfig config = TerminalConfig.builder()
        .rows(24).cols(80).build();
Terminal terminal = VT100Terminal.create(config);

TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
        .writer(bytes -> socket.getOutputStream().write(bytes))
        .build();

// Feed socket data (IAC commands auto-stripped)
gateway.feed(socketData);

// Terminal receives clean application data
List<String> output = terminal.render();

// Send to peer (IAC auto-escaped)
gateway.send("Welcome!\n");
```

## Standards

- **RFC 854** — Telnet Protocol (telnet-base)
- **RFC 855** — Option Specifications (telnet-negotiation)
- **RFC 1091** — Terminal Type Option (telnet-negotiation)
- **RFC 1073** — Negotiating Window Size (telnet-negotiation)
- **RFC 1079** — Terminal Speed Option (telnet-negotiation)
