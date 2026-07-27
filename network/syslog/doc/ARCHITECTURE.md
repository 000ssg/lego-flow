# Syslog Module — Architecture

This document describes the architectural decisions for the syslog module.

---

## Protocol Overview

Syslog (RFC 5424) is a standardized protocol for conveying event notification messages. It defines a structured message format with facility, severity, timestamp, hostname, application name, process ID, message ID, structured data, and free-form message text. The Lego Flow implementation provides full RFC 5424 message encoding/decoding plus three transport options: UDP (RFC 5426), TCP (RFC 6587), and TLS (RFC 5425).

## Layered Architecture

```mermaid
graph TD
    L1["SyslogSender / SyslogCollector<br/>(high-level API, transport abstraction, builder pattern)"]
    L2["Transport Layer<br/>UdpSender/UdpCollector | TcpSender/TcpCollector | TlsSender/TlsCollector<br/>(framing, socket management, virtual threads)"]
    L3["Protocol Layer<br/>SyslogCodec + SyslogMessage<br/>(RFC 5424 encode/decode, field validation)"]
    L4["Message Model<br/>Facility | Severity | StructuredData<br/>(enums, records, builders)"]
    L5["service module (TCP)<br/>(socket infrastructure)"]
    L6["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4
    L2 --> L5 --> L6
```

## Package Structure

```mermaid
graph TD
    ROOT["ssg.legoflow.network.syslog"]
    PROTO["protocol"]
    TRANS["transport"]

    ROOT --> PROTO
    ROOT --> TRANS

    ROOT --- SS["SyslogSender<br/>(sealed Transport interface,<br/>UDP/TCP/TLS factory methods)"]
    ROOT --- SC["SyslogCollector<br/>(Builder, aggregates UDP+TCP)"]

    PROTO --- SM["SyslogMessage<br/>(immutable record, Builder)"]
    PROTO --- SC2["SyslogCodec<br/>(encode/decode, MessageParser)"]
    PROTO --- FAC["Facility<br/>(24 codes, 0-23)"]
    PROTO --- SEV["Severity<br/>(8 levels, 0-7)"]
    PROTO --- SD["StructuredData<br/>(record, Builder, escaping)"]
    PROTO --- SPE["SyslogParseException"]

    TRANS --- FM["FramingMode<br/>(OCTET_COUNTING,<br/>NON_TRANSPARENT)"]
    TRANS --- US["UdpSender"]
    TRANS --- UC["UdpCollector"]
    TRANS --- TS["TcpSender"]
    TRANS --- TC["TcpCollector"]
    TRANS --- TLS["TlsSender"]
    TRANS --- TLC["TlsCollector"]
```

## Message Encoding/Decoding Flow

### Encoding (Send Path)

```mermaid
sequenceDiagram
    participant App as Application
    participant SS as SyslogSender
    participant SM as SyslogMessage.Builder
    participant SC as SyslogCodec
    participant T as Transport

    App->>SS: send(facility, severity, message)
    SS->>SM: builder(facility, severity).timestamp().hostname().build()
    SM-->>SS: SyslogMessage
    SS->>T: transport.send(message)
    T->>SC: encodeToBytes(message)
    SC-->>T: byte[]
    Note over T: UDP: send as datagram<br/>TCP: prepend length or append LF<br/>TLS: prepend length, write to SSLSocket
```

### Decoding (Receive Path)

```mermaid
sequenceDiagram
    participant Net as Network
    participant T as Transport Collector
    participant SC as SyslogCodec
    participant H as Handler

    Net->>T: incoming data
    Note over T: UDP: one datagram = one message<br/>TCP: deframe (octet counting or LF)<br/>TLS: deframe (octet counting)
    T->>SC: decode(text)
    SC->>SC: parsePri, parseVersion, parseFields,<br/>parseStructuredData, remaining
    SC-->>T: SyslogMessage
    T->>H: handler.accept(message)
```

## Transport Architecture

### Transport Selection (Sealed Interface)

```mermaid
graph TD
    TI["Transport<br/>(sealed interface)"]
    UDP["UdpTransport<br/>(DatagramSocket)"]
    TCP["TcpTransport<br/>(Socket + OutputStream)"]
    TLS["TlsTransport<br/>(SSLSocket + OutputStream)"]

    TI --> UDP
    TI --> TCP
    TI --> TLS
```

`SyslogSender` uses a sealed `Transport` interface with three permitted record implementations. This ensures exhaustive matching and type safety at compile time.

### TCP Framing Auto-Detection (Collector)

```mermaid
flowchart TD
    READ["Read first byte"]
    CHECK{"First byte<br/>is digit?"}
    OC["Octet Counting:<br/>read length prefix,<br/>then read N bytes"]
    NT["Non-Transparent:<br/>read until LF delimiter"]
    DECODE["SyslogCodec.decode(text)"]

    READ --> CHECK
    CHECK -->|Yes| OC
    CHECK -->|No| NT
    OC --> DECODE
    NT --> DECODE
```

The `TcpCollector` auto-detects framing mode per message by inspecting the first byte. This allows a single collector to handle connections from senders using either framing method.

### Collector Thread Model

```mermaid
graph TD
    UC["UdpCollector"]
    TC["TcpCollector"]
    TLC["TlsCollector"]

    UC --> UT["Virtual thread:<br/>syslog-udp-collector<br/>(reads datagrams in loop)"]

    TC --> TA["Virtual thread:<br/>syslog-tcp-acceptor<br/>(accepts connections)"]
    TA --> TC1["Virtual thread:<br/>syslog-tcp-client<br/>(per connection)"]
    TA --> TC2["Virtual thread:<br/>syslog-tcp-client<br/>(per connection)"]

    TLC --> TLA["Virtual thread:<br/>syslog-tls-acceptor<br/>(accepts TLS connections)"]
    TLA --> TLC1["Virtual thread:<br/>syslog-tls-client<br/>(per connection)"]
    TLA --> TLC2["Virtual thread:<br/>syslog-tls-client<br/>(per connection)"]
```

## RFC 5424 Message Structure

```mermaid
graph LR
    PRI["&lt;PRI&gt;<br/>facility*8+severity"]
    VER["VERSION<br/>(always 1)"]
    TS["TIMESTAMP<br/>(ISO 8601 or -)"]
    HN["HOSTNAME<br/>(max 255 or -)"]
    AN["APP-NAME<br/>(max 48 or -)"]
    PID["PROCID<br/>(max 128 or -)"]
    MID["MSGID<br/>(max 32 or -)"]
    SDE["STRUCTURED-DATA<br/>([sdID p=v...] or -)"]
    MSG["MSG<br/>(UTF-8 text)"]

    PRI --> VER --> TS --> HN --> AN --> PID --> MID --> SDE --> MSG
```

## Integration with Lego Flow

| Lego Flow Module | Usage in Syslog |
|------------------|-----------------|
| `blocks` | DP<I,O> for message processing pipelines, DF<T> for filtering, Statistics for metrics |
| `service` | TCP/UDP socket infrastructure, virtual thread pools, lifecycle management |

## Design Decisions

- **Records for immutable data**: `SyslogMessage` and `StructuredData` are Java records, providing immutability and compact constructors with validation
- **Sealed interface for transports**: compile-time exhaustive transport handling in `SyslogSender`
- **Builder pattern**: both `SyslogMessage` and `SyslogCollector` use builders for flexible construction
- **Virtual threads**: all collectors use `Thread.ofVirtual()` for lightweight concurrent connection handling
- **Auto-detection over configuration**: `TcpCollector` auto-detects framing mode rather than requiring it as a parameter
- **Consumer-based handler**: collectors deliver messages via `Consumer<SyslogMessage>` for simple integration

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../doc/ARCHITECTURE.md) | [Root README](../../../README.md)

---

**Last Updated**: 2026-07-06
