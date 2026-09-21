# XMPP Module — Architecture

This document describes the architectural decisions for the XMPP module.

---

## Protocol Overview

XMPP (Extensible Messaging and Presence Protocol) is an XML-based protocol for real-time communication. The Lego Flow implementation covers XMPP core (RFC 6120), XMPP IM (RFC 6121), and IoT extensions (XEP-0323/0325/0347) over TCP transport.

## Layered Architecture

```mermaid
graph TD
    L1["IoT Extensions<br/>XEP-0323 Sensor Data | XEP-0325 Control | XEP-0347 Discovery"]
    L2["IM Features<br/>Presence | Messaging | Roster | MUC | PubSub"]
    L3["Stanza Processing<br/>(message/presence/iq routing, extension dispatch,<br/>error generation, namespace handlers)"]
    L4["Stream Management (XEP-0198)<br/>(stanza counting, ack requests, session resumption)"]
    L5["SASL Authentication + TLS<br/>(PLAIN, SCRAM-SHA-1/256, STARTTLS, stream restart)"]
    L6["XML Stream<br/>(stream:stream, feature negotiation,<br/>stanza framing, incremental XML parsing)"]
    L7["XmppTransport SPI<br/>(InMemoryXmppTransport for tests/demos,<br/>PipelineXmppTransport for production)"]
    L75["service module<br/>(SelectableChannelManager, virtual threads)"]
    L8["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7 --> L75 --> L8
```

## Headless Core / Transport SPI

The protocol core is **socket-free**: `XmppClient` and `XmppServer` contain no `SocketChannel`/`Selector` usage. All I/O flows through the `XmppTransport` byte-level SPI, which the core owns and the service layer implements.

```mermaid
graph LR
    subgraph Core["Protocol core (socket-free)"]
        C[XmppClient]
        S[XmppServer]
        CO[XmppCodec]
    end
    subgraph SPI["XmppTransport SPI"]
        T[XmppTransport]
    end
    subgraph Impl["Implementations"]
        IM[InMemoryXmppTransport<br/>tests + demos]
        PL[PipelineXmppTransport<br/>production]
    end
    subgraph SL["Service layer (only owner of real channels)"]
        SS[XmppServerService]
        CS[XmppClientService]
        M[SelectableChannelManager]
    end

    C --> T
    S --> T
    T --> IM
    T --> PL
    SS --> PL
    CS --> PL
    SS --> M
    CS --> M
    SS -. "handleConnection(transport)" .-> S
```

- **Client**: `new XmppClient(transport)` wires the protocol to an injected transport; a virtual-thread read loop feeds `transport.onRead` bytes into the codec and the stream, and outbound stream/stanza bytes are flushed through the transport after every send. The legacy no-arg `XmppClient` keeps the in-memory buffer for backward compatibility.
- **Server**: `XmppServer.handleConnection(XmppTransport)` accepts a per-connection transport and drives a non-blocking read loop (virtual thread) that decodes stanzas and broadcasts them to registered handlers; it never opens a socket itself.
- **InMemoryXmppTransport**: paired in-memory queues with `onRead`/`onWrite` callbacks — used by unit tests and the demo suite (no TCP, fully deterministic).
- **PipelineXmppTransport**: backed by a `DataChannel`; the `SelectableChannelManager` selector thread calls `onRead`/`onWrite` with `ByteBuffer`s — used by the service layer for production TCP.
- **Why**: the same pattern applied in the NATS and MQTT modules — protocol logic is testable without a socket, and only the service layer touches non-blocking channels.

## XML Stream Lifecycle

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server

    C->>S: TCP connect
    C->>S: stream:stream to="example.com"
    S->>C: stream:stream from="example.com"
    S->>C: stream:features (starttls required)

    C->>S: TLS Negotiation (STARTTLS)
    S->>C: TLS Established

    C->>S: stream:stream (restart)
    S->>C: stream:features (SCRAM-SHA-256)

    C->>S: SASL Authentication
    S->>C: success

    C->>S: stream:stream (restart)
    S->>C: stream:features (bind, sm)

    C->>S: Resource Binding
    S->>C: Bound JID

    C->>S: Enable Stream Management
    S->>C: SM Enabled

    Note over C,S: Stanza Exchange

    C->>S: /stream:stream
    S->>C: /stream:stream
```

## Stanza Architecture

### Three Stanza Types

```mermaid
graph TD
    Stanza["Stanza (sealed)"]
    Stanza --> Message
    Stanza --> Presence
    Stanza --> IQ

    Message["Message<br/>types: chat, groupchat,<br/>headline, normal, error<br/>Has: body, subject, thread"]
    Presence["Presence<br/>types: available, unavailable,<br/>subscribe, subscribed,<br/>unsubscribe, unsubscribed,<br/>probe, error<br/>Has: show, status, priority"]
    IQ["IQ<br/>types: get, set,<br/>result, error<br/>Must have unique id<br/>Has: query/command child"]
```

- Stanza is a sealed interface; pattern matching for exhaustive dispatch
- Each stanza has: from, to, id, type, optional error, extension elements
- Extension elements keyed by XML namespace, dispatched to registered handlers

### Stanza Routing

```mermaid
graph TD
    XML["Incoming XML"] --> Parser["XML Parser (streaming)"]
    Parser -->|"stream:error"| SEH["Stream Error Handler"]
    Parser -->|"stanza"| Router["Stanza Router"]
    Router -->|"message"| MH["Message Handlers<br/>(chat, MUC, etc.)"]
    Router -->|"presence"| PE["Presence Engine<br/>(roster, directed)"]
    Router -->|"iq"| IQ["IQ Dispatcher<br/>(roster, disco, IoT, etc.)"]
```

## SASL Authentication

### SCRAM-SHA-256 Flow
```mermaid
sequenceDiagram
    Client->>Server: auth mechanism="SCRAM-SHA-256"<br/>base64(client-first-message)
    Server->>Client: challenge<br/>base64(server-first-message)
    Client->>Server: response<br/>base64(client-final-message)
    Server->>Client: success<br/>base64(server-final-message)
```

- Client-first: `n,,n=user,r=clientNonce`
- Server-first: `r=clientNonce+serverNonce,s=salt,i=iterations`
- Client-final: `c=biws,r=combinedNonce,p=clientProof`
- Server-final: `v=serverSignature` (client verifies server)

## IoT Extensions Architecture

### Sensor Data (XEP-0323)

```mermaid
sequenceDiagram
    Requester->>Sensor: iq type="get" (req seqnr=1, momentary=true)
    Sensor->>Requester: iq type="result" (accepted seqnr=1)
    Sensor->>Requester: message (fields seqnr=1:<br/>temperature=22.5C, humidity=45.2%)
    Sensor->>Requester: message (done seqnr=1)
```

### Control (XEP-0325)

```mermaid
sequenceDiagram
    Controller->>Actuator: iq type="set" (targetTemperature=22.5)
    Actuator->>Controller: iq type="result" (responseCode=OK)
```

### Discovery (XEP-0347)

```mermaid
sequenceDiagram
    Thing->>Registry: iq type="set" (register:<br/>SN=ABC123, MAN=Lego Flow, MODEL=TempSensor)
    Searcher->>Registry: iq type="get" (search: MAN=Lego Flow)
```

## Stream Management (XEP-0198)

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server

    C->>S: enable xmlns="urn:xmpp:sm:3"
    S->>C: enabled id="sm-123"

    C->>S: message (h=1 locally)
    C->>S: message (h=2 locally)
    C->>S: r/ (request ack)
    S->>C: a h="2" (server acked 2)

    Note over C,S: *** connection drops ***

    C->>S: TCP reconnect
    C->>S: stream:stream
    Note over C,S: ... auth ...
    C->>S: resume h="5" previd="sm-123"
    S->>C: resumed h="2" previd="sm-123"
    Note over C: redeliver stanzas 3,4,5
```

- `h` counter tracks number of stanzas handled (received and processed)
- `<r/>` requests the peer to report its `h` value
- `<a h="N"/>` acknowledges N stanzas have been handled
- Unacknowledged stanzas are queued and redelivered on session resumption

## Stream-Oriented Codec Design

XMPP operates over TCP, which is a byte-stream transport. XML stanzas may be split across multiple TCP reads or arrive concatenated. The XmppCodec handles this with internal string accumulation and regex-based stanza boundary detection.

### Buffer Cleanup Strategy

```mermaid
graph LR
    TCP["TCP Read<br/>(ByteBuffer → String)"] --> Append["Append to<br/>internal buffer"]
    Append --> Match["Regex match<br/>stanza boundaries"]
    Match -->|"Complete stanza found"| Emit["Emit Stanza"]
    Match -->|"No complete stanza"| Wait["Wait for<br/>next read"]
    Emit --> Trim["Trim buffer at<br/>Matcher.end() position"]
    Trim --> Match
```

- **Matcher.end() position tracking**: after extracting a complete stanza, the buffer is trimmed at the exact position reported by `Matcher.end()`, rather than using fragile `lastIndexOf("</message>")` string search
- **Why position tracking is better**: the previous `lastIndexOf` approach only worked for `</message>` stanzas, could break with nested elements or content containing `</message>` substrings, and did not generalize to `<presence>` or `<iq>` stanzas
- **hasBufferedData()**: returns whether the codec holds incomplete stanza data, useful for connection cleanup and diagnostics

## Integration with Lego Flow

| Lego Flow Module | Usage in XMPP |
|------------------|---------------|
| `blocks` | DP<I,O> for stanza processing pipeline, DF<T> for stanza filtering, Statistics for metrics |
| `service` | `SelectableChannelManager` channels for the service layer (XmppClientService/XmppServerService); virtual threads for read loops |

The XMPP module follows the framework's dual API convention: XmppClient and all feature managers (roster, presence, MUC, pubsub, IoT) expose both sync and async (CompletableFuture) variants, with functional-style builders for configuration and stanza handling. The service layer (`XmppClientService`, `XmppServerService`) is the only code in the module that owns real channels; the protocol core (`XmppClient`, `XmppServer`, `XmppCodec`, `XmppStream`) is socket-free and reads/writes exclusively through the `XmppTransport` SPI.

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
