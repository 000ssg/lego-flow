# Telnet Gateway — Architecture

## Overview

The TelnetGateway bridges the Telnet protocol parser with a terminal emulator, handling protocol-specific concerns (IAC escaping, option negotiation) transparently.

## Data Flow

```mermaid
sequenceDiagram
    Peer->>Gateway: raw bytes (with IAC commands)
    Gateway->>Parser: feed bytes
    Parser->>Gateway: onData(clean data)
    Gateway->>Terminal: feed clean data
    alt echo enabled
        Gateway->>Peer: echo bytes (IAC escaped)
    end
    Peer->>Gateway: IAC WILL ECHO
    Gateway->>Negotiator: negotiate(WILL, ECHO)
    Negotiator-->>Gateway: DO ECHO
    Gateway->>Peer: IAC DO ECHO
    
    Terminal->>Gateway: application output
    Gateway->>Peer: output (IAC escaped)
```

## Protocol Bridge

```mermaid
graph TD
    SUB["Subnegotiation Handler"]
    NEG["OptionNegotiator"]
    TTYPE["TTYPEHandler"]
    NAWS["NAWSHandler"]
    TELNET["TelnetConnection"]
    GW["TelnetGateway"]
    TERM["Terminal"]
    
    TELNET --> GW
    GW --> TERM
    GW --> NEG
    GW --> TTYPE
    GW --> NAWS
    TTYPE --> SUB
    NAWS --> SUB
```

## Design Decisions

- **Auto-negotiation** — ECHO and SUPPRESS_GO_AHEAD enabled by default
- **TTYPE auto-response** — responds with terminal type when peer requests
- **NAWS auto-resize** — updates terminal config on peer resize
- **Feed + Flush** — `gateway.feed()` calls `connection.feed()` then `connection.flush()` to ensure data delivery
- **Test dependency on VT100** — tests use VT100Terminal as a concrete Terminal implementation

---

**Last Updated**: 2026-08-17
