# network / telnet / gateway — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

Bridges Telnet protocol to terminal emulators. The gateway sits between the network transport and the terminal: it strips IAC commands from inbound data before feeding the terminal, and escapes IAC bytes in outbound data from the terminal.

## Key Class

- `TelnetGateway` — bridges TelnetConnection + OptionNegotiation + Terminal
- `GatewayListener` — callback interface for gateway events
- `GatewayEvent` — CONNECTED, NEGOTIATED, RESIZED, TTYPE_EXCHANGED, DISCONNECTED

## Dependencies

- `telnet-base` — TelnetConnection, TelnetParser, TelnetCommand, TelnetOption
- `telnet-negotiation` — OptionNegotiator, TTYPEHandler, NAWSHandler
- `terminals-base` — Terminal interface, TerminalConfig

## Testing

- Tests: 9
- Test plain text feed/echo
- Test echo disable
- Test negotiation response
- Test TTYPE subnegotiation
- Test NAWS subnegotiation
- Test send with IAC escaping
- Test terminal access

Total tests: 9
