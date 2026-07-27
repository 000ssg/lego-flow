# Media Common Module -- Architecture

This document describes the architectural decisions for the media/common SDP module.

---

## Protocol Overview

SDP (Session Description Protocol, RFC 4566) is a text-based format for describing multimedia session parameters. It is used by signaling protocols (SIP, RTSP) to describe media sessions, negotiate codecs, and exchange transport addresses. This module provides the shared SDP infrastructure consumed by both the RTSP and SIP modules in Lego Flow.

## Layered Architecture

```mermaid
graph TD
    L1["Builders<br/>(SessionBuilder, MediaBuilder — fluent construction API)"]
    L2["Codec Layer<br/>(SdpParser, SdpWriter, SdpNegotiator)"]
    L3["SDP Model<br/>(SessionDescription, MediaDescription, Origin,<br/>ConnectionInfo, Timing, RtpMap, Attribute, ...)"]
    L4["Payload Registry<br/>(static RFC 3551 types, dynamic type registration)"]
    L5["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L3
    L2 --> L3
    L2 --> L4
    L3 --> L5
```

## Module Structure

```mermaid
graph LR
    subgraph "ssg.legoflow.media.common"
        subgraph codec["codec"]
            Parser["SdpParser"]
            Writer["SdpWriter"]
            Negotiator["SdpNegotiator"]
        end

        subgraph sdp["sdp (model)"]
            SD["SessionDescription"]
            MD["MediaDescription"]
            Origin["Origin"]
            CI["ConnectionInfo"]
            Timing["Timing"]
            RtpMap["RtpMap"]
            FmtP["FormatParameters"]
            Attr["Attribute"]
            Dir["Direction"]
            MT["MediaType"]
            TP["TransportProtocol"]
            BW["Bandwidth"]
            RT["RepeatTime"]
            ICE["IceCandidate"]
            FP["Fingerprint"]
        end

        subgraph payload["payload"]
            PT["PayloadType"]
            PR["PayloadRegistry"]
        end

        subgraph builder["builder"]
            SB["SessionBuilder"]
            MB["MediaBuilder"]
        end
    end

    Parser --> SD
    Parser --> MD
    Writer --> SD
    Negotiator --> SD
    SB --> SD
    MB --> MD
    PR --> PT
```

## SDP Document Structure

An SDP document consists of session-level fields followed by zero or more media descriptions. The parser processes this as a state machine:

```mermaid
graph TD
    Start["Start"] --> SL["Session-Level Lines<br/>v=, o=, s=, i=, u=, e=, p=,<br/>c=, b=, t=, r=, z=, k=, a="]
    SL -->|"m= line"| ML["Media-Level Lines<br/>i=, c=, b=, a="]
    ML -->|"m= line"| ML
    ML -->|"EOF"| End["Finalize"]
    SL -->|"EOF"| End
```

## Parse / Write Data Flow

```mermaid
flowchart LR
    SDP_Text["SDP Text<br/>(RFC 4566)"] -->|"SdpParser.parse()"| Model["SessionDescription<br/>+ MediaDescription[]"]
    Model -->|"SdpWriter.write()"| SDP_Out["SDP Text<br/>(CRLF)"]
    Model -->|"SdpNegotiator.negotiate()"| Answer["Answer SDP<br/>(RFC 3264)"]
```

## Offer/Answer Negotiation (RFC 3264)

```mermaid
sequenceDiagram
    participant Offerer
    participant SdpNegotiator
    participant Answerer

    Offerer->>SdpNegotiator: offer (SessionDescription)
    Note right of SdpNegotiator: For each offered media:<br/>1. Find matching type+protocol<br/>2. Intersect formats by codec+rate<br/>3. Reverse direction<br/>4. Reject with port=0 if no match
    SdpNegotiator->>Answerer: answererCapabilities
    SdpNegotiator-->>Offerer: answer (SessionDescription) or empty
```

## Model Class Hierarchy

```mermaid
classDiagram
    class SessionDescription {
        +int version
        +Origin origin
        +String sessionName
        +Optional~String~ sessionInfo
        +Optional~String~ uri
        +Optional~String~ email
        +Optional~String~ phone
        +Optional~ConnectionInfo~ connectionInfo
        +List~Bandwidth~ bandwidths
        +List~Timing~ timings
        +List~RepeatTime~ repeatTimes
        +Optional~String~ timezoneAdjustments
        +Optional~String~ encryptionKey
        +List~Attribute~ attributes
        +List~MediaDescription~ mediaDescriptions
        +findAttribute(name) Optional~Attribute~
        +effectiveConnectionInfo(media) Optional~ConnectionInfo~
    }

    class MediaDescription {
        +MediaType mediaType
        +int port
        +int portCount
        +TransportProtocol protocol
        +List~Integer~ formats
        +Direction direction
        +List~RtpMap~ rtpMaps
        +List~FormatParameters~ formatParameters
        +List~IceCandidate~ iceCandidates
        +Optional~Fingerprint~ fingerprint
        +List~Attribute~ attributes
        +findRtpMap(payloadType) Optional~RtpMap~
    }

    SessionDescription "1" *-- "0..*" MediaDescription
    SessionDescription "1" *-- "1" Origin
    SessionDescription "1" *-- "0..*" Timing
    SessionDescription "1" *-- "0..*" Bandwidth
    SessionDescription "1" *-- "0..*" RepeatTime
    SessionDescription "1" *-- "0..*" Attribute
    MediaDescription "1" *-- "0..*" RtpMap
    MediaDescription "1" *-- "0..*" FormatParameters
    MediaDescription "1" *-- "0..*" IceCandidate
    MediaDescription "1" *-- "0..1" Fingerprint
    MediaDescription "1" *-- "0..*" Bandwidth

    class Direction {
        <<enumeration>>
        SENDRECV
        SENDONLY
        RECVONLY
        INACTIVE
    }

    class MediaType {
        <<enumeration>>
        AUDIO
        VIDEO
        TEXT
        APPLICATION
        MESSAGE
    }

    class TransportProtocol {
        <<enumeration>>
        RTP_AVP
        RTP_SAVP
        RTP_AVPF
        RTP_SAVPF
        UDP
        TCP
        TCP_RTP_AVP
    }
```

## Payload Type Registry Architecture

```mermaid
graph TD
    PR["PayloadRegistry"]
    Static["Static Types (RFC 3551)<br/>PCMU=0, PCMA=8, G722=9, ...<br/>18 types, immutable"]
    Dynamic["Dynamic Types (96-127)<br/>Registered at runtime via SDP<br/>Mutable per-instance"]

    PR --> Static
    PR --> Dynamic

    Lookup["lookup(number)"] --> Dynamic
    Dynamic -->|"miss"| Static
```

## Design Decisions

### Immutable Model
All model classes produce immutable instances. `SessionDescription` and `MediaDescription` use `List.copyOf()` for all collection fields. Record types (`Origin`, `Timing`, etc.) are inherently immutable. This makes parsed SDP objects safe to share across threads without synchronization.

### Dual Representation of Attributes
Media-level attributes are stored both as typed objects (`rtpMaps`, `formatParameters`, `iceCandidates`, `fingerprint`, `direction`) and as generic `Attribute` instances in the `attributes` list. This dual representation allows:
- Type-safe access via dedicated getters (e.g., `findRtpMap(96)`)
- Lossless round-trip: the writer serializes from the generic `attributes` list, preserving original order and any unknown attributes

### Single-Pass Parser
The parser processes SDP text in a single pass, line by line. A state transition occurs on encountering an `m=` line, switching from session-level to media-level context. This keeps parsing simple, efficient, and memory-friendly for large SDP documents with many media sections.

### Static Utility Classes
`SdpParser`, `SdpWriter`, and `SdpNegotiator` are stateless utility classes with private constructors and static methods. No instance state is needed because SDP parsing and writing are pure transformations.

## Cross-Module Usage

| Consumer Module | Usage |
|----------------|-------|
| `media/rtsp` | Parses SDP from DESCRIBE responses, writes SDP for ANNOUNCE, uses negotiator for SETUP |
| `media/sip` | Parses SDP from INVITE/200 OK bodies, writes SDP offers/answers, negotiates codecs |

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../doc/ARCHITECTURE.md) | [Root README](../../README.md)

---

**Last Updated**: 2026-07-06
