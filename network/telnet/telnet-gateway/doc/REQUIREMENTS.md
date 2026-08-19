# Telnet Gateway — Requirements

## Requirements

### Gateway Core
1. Bridge TelnetConnection to Terminal
2. Strip IAC commands from inbound data before feeding terminal
3. Escape IAC bytes in outbound data from terminal
4. Automatic flush after each feed

### Option Negotiation
1. Auto-negotiate ECHO (enabled by default)
2. Auto-negotiate SUPPRESS_GO_AHEAD (enabled by default)
3. TTYPE negotiation — respond with terminal type
4. NAWS negotiation — update terminal dimensions

### Echo Control
1. Echo enabled by default
2. setEchoEnabled(false) to disable
3. Echo responds to DO/DONT negotiation

### Events
1. GatewayListener for CONNECTED, NEGOTIATED, RESIZED, TTYPE_EXCHANGED, DISCONNECTED

### Configuration
1. Custom OptionNegotiator via builder
2. Custom writer via builder

## Test Coverage
- TelnetGatewayTest — feed/echo, negotiation, TTYPE, NAWS, send, IAC escape
- Total tests: 9

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (terminal-framework agent) |
| Agent tokens | ~45K |
| Agent tool calls | ~35 |
| Agent wall time | ~18 min |
| Files created/modified | 5 |
| Lines added/removed | +350 / -0 |
| Tests added | 9 (total: 9) |

---

**Last Updated**: 2026-08-17
