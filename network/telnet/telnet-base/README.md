# Lego Flow Telnet — Base

Core Telnet protocol implementation (RFC 854).

## Features

- `TelnetCommand` — all 14 Telnet commands (SB, SE, NOP, DM, BRK, IP, AO, AYT, EC, EL, GA, WILL, WONT, DO, DONT)
- `TelnetOption` — standard option codes (BINARY, ECHO, SUPPRESS_GO_AHEAD, TTYPE, NAWS, LINEMODE, etc.)
- `TelnetParser` — RFC 854 byte-level state machine (DATA, COMMAND, NEGOTIATE, SUBNEGOTIATION)
- `TelnetListener` — callback interface for data, commands, negotiation, subnegotiation
- `TelnetConnection` — high-level connection with builder API, automatic IAC escaping on output
- `TelnetOutputStream` — wraps OutputStream with IAC doubling
- `TelnetException` — unchecked protocol exception

## Quick Start

```java
// Parse incoming bytes
TelnetParser parser = new TelnetParser(new TelnetListener() {
    public void onData(List<Integer> data) { /* process */ }
    public void onCommand(TelnetCommand cmd) { /* handle */ }
    public void onNegotiate(TelnetCommand cmd, int opt) { /* negotiate */ }
    public void onSubnegotiation(int opt, List<Integer> data) { /* subneg */ }
});

// High-level connection
TelnetConnection conn = TelnetConnection.builder()
        .writer(bytes -> socket.getOutputStream().write(bytes))
        .onData(bytes -> System.out.println(new String(bytes)))
        .onNegotiate((cmd, opt) -> {/* respond */})
        .build();

// Feed incoming data
conn.feed(buffer);
conn.flush();  // deliver buffered application data

// Send data (auto-escapes IAC bytes)
conn.send("Hello, world!\n");
conn.sendNegotiate(TelnetCommand.WILL, TelnetOption.ECHO.code());
```

## Compliance

- **RFC 854** — Telnet Protocol (full state machine)
- IAC escaping (doubled IAC bytes)
- SB...SE subnegotiation with nested IAC IAC

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md) | [Development Guide](AGENTS.md)
