# Telnet Base — Requirements

## Requirements

### RFC 854 Parser
1. DATA state — accumulate bytes, IAC triggers command parsing
2. COMMAND state — IAC IAC → literal 255; WILL/WONT/DO/DONT → negotiation; SB → subnegotiation; others → single-byte commands
3. NEGOTIATE state — read option byte, dispatch to listener
4. SUBNEGOTIATION state — read option code, accumulate data until IAC SE
5. Nested IAC IAC within SB...SE → literal 255 in sub data
6. Flush accumulated data on IAC boundary
7. Explicit flush() to deliver remaining buffered data

### TelnetConnection
1. Builder API with writer, onData, onCommand, onNegotiate, onSubnegotiate
2. feed(bytes) — parse incoming data
3. flush() — deliver remaining buffered data
4. send(data) — write with IAC auto-escaping
5. sendCommand(cmd) — send single-byte commands
6. sendNegotiate(cmd, opt) — send negotiation
7. sendSubnegotiation(opt, data) — send SB...SE

### TelnetOutputStream
1. Wraps OutputStream with automatic IAC doubling
2. Pass-through for non-IAC bytes
3. Write IAC IAC for each IAC byte

## Test Coverage
- TelnetCommandTest — enum values
- TelnetOptionTest — option codes
- TelnetParserTest — state machine, IAC handling, subnegotiation
- TelnetConnectionTest — send, receive, escaping, negotiation, subnegotiation
- TelnetOutputStreamTest — IAC doubling
- Total tests: 60

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (terminal-framework agent) |
| Agent tokens | ~60K |
| Agent tool calls | ~45 |
| Agent wall time | ~25 min |
| Files created/modified | 9 |
| Lines added/removed | +700 / -0 |
| Tests added | 60 (total: 60) |

---

**Last Updated**: 2026-08-17
