# network / telnet / base — Module-Specific Notes

> **Project-wide conventions**: See [root AGENTS.md](../../../../AGENTS.md).

## Module Purpose

Telnet protocol core (RFC 854). Provides the parser, connection abstraction, and command/option enums. No terminal emulation — pure protocol.

## Key Classes

- `TelnetCommand` — enum of 14 commands, `hasOption()` helper
- `TelnetOption` — enum of standard options with code numbers
- `ParserState` — DATA, COMMAND, NEGOTIATE, SUBNEGOTIATION
- `TelnetListener` — callback interface (onData, onCommand, onNegotiate, onSubnegotiation)
- `TelnetParser` — byte-level state machine, IAC handling, SB...SE subnegotiation
- `TelnetConnection` — high-level connection, builder API, IAC-escaping writer
- `TelnetOutputStream` — wraps OutputStream with automatic IAC doubling
- `TelnetException` — unchecked exception

## Parser Data Delivery

The parser delivers data incrementally. When IAC (255) is encountered in DATA state, accumulated bytes are flushed to the listener BEFORE entering the COMMAND state. After processing the escape sequence, remaining bytes accumulate fresh. Callers must call `flush()` after `feed()` to deliver remaining buffered data.

Example: feeding `"a\xFF\xFFb"` produces two `onData` events: `[97]` and `[255, 98]`.

## Testing

- Tests: ~28
- Test all command types
- Test IAC escaping/doubling
- Test SB...SE subnegotiation
- Test connection send/receive

Total tests: ~28
