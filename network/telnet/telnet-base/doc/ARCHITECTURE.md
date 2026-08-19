# Telnet Base — Architecture

## Overview

The telnet-base module provides a transport-agnostic Telnet protocol implementation per RFC 854. It handles the parser state machine, IAC escaping on output, and exposes a callback-based API.

## Parser State Machine

```mermaid
stateDiagram-v2
    [*] --> DATA
    DATA --> DATA : non-IAC byte
    DATA --> COMMAND : IAC (255)
    COMMAND --> DATA : IAC (255 → literal 255)
    COMMAND --> NEGOTIATE : WILL/WONT/DO/DONT
    COMMAND --> SUBNEG : SB
    COMMAND --> DATA : single-byte command
    NEGOTIATE --> DATA : option byte
    SUBNEG --> SUBNEG : data / IAC
    SUBNEG --> DATA : SE (after IAC)
```

## IAC Escaping

RFC 854 Section 1 defines the escaping mechanism:
- IAC byte (255) in data stream → interpreted as command prefix
- To send literal 255 as data → transmit IAC IAC (255 255)
- Parser handles this in COMMAND state: if next byte is also IAC, emit literal 255

## Connection Model

```mermaid
sequenceDiagram
    participant Peer as Remote Peer
    participant Conn as TelnetConnection
    participant App as Application

    Peer->>Conn: feed(bytes)
    Conn->>Conn: parser.process(bytes)
    parser->>App: onData, onCommand, onNegotiate, onSubnegotiate
    App->>Conn: send(text)
    Conn->>Conn: escapeIac()
    Conn->>Peer: write(escaped bytes)
```

## Design Decisions

- **Records for events** — `SubnegotiationEvent(option, data)` for type-safe callbacks
- **Builder pattern** — `TelnetConnection.builder()` for flexible configuration
- **Functional interfaces** — `Consumer<byte[]>` for simple callbacks
- **Parser is not thread-safe** — single-threaded usage assumed
- **No buffering on send** — `send()` writes immediately via the writer callback

---

**Last Updated**: 2026-08-17
