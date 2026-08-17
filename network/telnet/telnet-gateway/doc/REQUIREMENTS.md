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
