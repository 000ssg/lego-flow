# RTP Module -- Architecture

This document describes the architectural decisions for the RTP/RTCP module.

---

## Protocol Overview

RTP (Real-time Transport Protocol) provides end-to-end delivery services for real-time media data such as audio and video. RTCP (RTP Control Protocol) provides out-of-band control information including sender/receiver statistics, source identification, and session membership. Both are defined in RFC 3550. The Lego Flow implementation provides packet-level encode/decode, session management, jitter buffering, and UDP transport.

## Layered Architecture

```mermaid
graph TD
    L1["Sender / Receiver<br/>(send RTP packets, receive with jitter buffer,<br/>SSRC collision handling, virtual-thread I/O)"]
    L2["Session Management<br/>(participant table, SSRC tracking,<br/>collision detection/resolution, sender/receiver stats)"]
    L3["Jitter Buffer<br/>(adaptive playout delay, packet reordering,<br/>duplicate/late detection, overflow handling)"]
    L4["Packet Codec<br/>(RTP: header + payload + extensions<br/>RTCP: SR/RR/SDES/BYE/APP + compound)"]
    L5["UDP Transport<br/>(paired RTP/RTCP ports, DatagramChannel,<br/>non-blocking I/O, 1500-byte MTU)"]
    L6["service module<br/>(lifecycle, virtual threads)"]
    L7["blocks module<br/>(DP&lt;I,O&gt;, DF&lt;T&gt;, Context, State, Statistics)"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7
```

## RTP Packet Structure

The RTP packet consists of a fixed 12-byte header followed by optional CSRC list, optional header extension, and payload:

```mermaid
graph LR
    subgraph "RTP Packet"
        H["Fixed Header<br/>12 bytes<br/>V, P, X, CC, M, PT<br/>seq, timestamp, SSRC"]
        C["CSRC List<br/>0-15 x 4 bytes"]
        E["Header Extension<br/>profile (2B) + length (2B)<br/>+ extension data"]
        P["Payload<br/>(media data)"]
    end
    H --> C --> E --> P
```

### Fixed Header Fields

| Field | Bits | Description |
|-------|------|-------------|
| Version (V) | 2 | Always 2 |
| Padding (P) | 1 | Packet contains padding octets |
| Extension (X) | 1 | Header extension present |
| CSRC Count (CC) | 4 | Number of CSRC identifiers (0-15) |
| Marker (M) | 1 | Profile-specific marker (e.g., frame boundary) |
| Payload Type (PT) | 7 | Media encoding identifier (0-127) |
| Sequence Number | 16 | Increments per packet (wrap at 65535) |
| Timestamp | 32 | Sampling instant of the first octet (unsigned) |
| SSRC | 32 | Synchronization source identifier (unsigned) |

## RTCP Packet Type Hierarchy

```mermaid
graph TD
    RtcpPacket["sealed interface RtcpPacket<br/>packetType(), ssrc()"]
    SR["SenderReport (200)<br/>NTP/RTP timestamps,<br/>packet/octet counts,<br/>reception reports"]
    RR["ReceiverReport (201)<br/>reception reports only"]
    SDES["SourceDescription (202)<br/>SDES chunks with items:<br/>CNAME, NAME, EMAIL, etc."]
    BYE["Goodbye (203)<br/>SSRC list + optional reason"]
    APP["ApplicationDefined (204)<br/>subtype + 4-char name + data"]
    CP["CompoundPacket<br/>List&lt;RtcpPacket&gt;<br/>(must start with SR or RR)"]

    RtcpPacket --> SR
    RtcpPacket --> RR
    RtcpPacket --> SDES
    RtcpPacket --> BYE
    RtcpPacket --> APP
    CP -->|"contains"| RtcpPacket
```

All RTCP types share a 4-byte common header: version (2 bits), padding (1 bit), count/subtype (5 bits), packet type (8 bits), length in 32-bit words (16 bits).

## RTCP Compound Packet Rules

RFC 3550 Section 6.1 requires RTCP packets to be sent as compound packets:

```mermaid
graph LR
    subgraph "Compound RTCP Packet"
        First["SR or RR<br/>(mandatory first)"]
        SDES2["SDES<br/>(with CNAME)"]
        Others["BYE / APP<br/>(optional)"]
    end
    First --> SDES2 --> Others
```

- First packet must be SR (if sender) or RR (if receiver only)
- SDES with at least CNAME should follow
- Additional packets (BYE, APP) may be appended

## Session Architecture

```mermaid
graph TD
    Session["RtpSession<br/>localSsrc, cname"]
    PT["ConcurrentHashMap&lt;Long, RtpParticipant&gt;<br/>(participant table)"]
    Local["Local Participant<br/>send stats, CNAME"]
    Remote1["Remote Participant 1<br/>recv stats, jitter, seq"]
    Remote2["Remote Participant 2<br/>recv stats, jitter, seq"]
    CD["Collision Detection<br/>(compare incoming SSRC<br/>against localSsrc)"]
    CR["Collision Resolution<br/>(generate new SSRC<br/>via SecureRandom)"]

    Session --> PT
    PT --> Local
    PT --> Remote1
    PT --> Remote2
    Session --> CD
    CD -->|"collision"| CR
```

- **Participant Table**: `ConcurrentHashMap` keyed by SSRC for O(1) lookup
- **Local Participant**: created on session construction with CNAME
- **Collision Detection**: checks incoming SSRC against local SSRC (RFC 3550 Section 8.2)
- **SSRC Generation**: `SecureRandom` for random 32-bit unsigned values

## Jitter Buffer Architecture

```mermaid
graph TD
    Insert["insert(RtpPacket)"]
    DupCheck{"Duplicate?<br/>(seq in buffer)"}
    LateCheck{"Late?<br/>(seq < expected)"}
    CapCheck{"Buffer full?"}
    Accept["ACCEPTED<br/>(put in TreeMap)"]
    DupResult["DUPLICATE"]
    LateResult["LATE"]
    Overflow["Drop oldest<br/>then insert"]
    Poll["poll() -> next expected seq"]
    Skip["skip() -> next available"]
    Adapt["adaptDelay(jitter)<br/>EWMA alpha=1/16"]

    Insert --> DupCheck
    DupCheck -->|yes| DupResult
    DupCheck -->|no| LateCheck
    LateCheck -->|yes| LateResult
    LateCheck -->|no| CapCheck
    CapCheck -->|yes| Overflow --> Accept
    CapCheck -->|no| Accept
    Accept -.-> Poll
    Accept -.-> Skip
    Accept -.-> Adapt
```

- **Data Structure**: `TreeMap<Integer, RtpPacket>` with custom comparator for 16-bit sequence number wrap-around
- **Thread Safety**: `ReentrantLock` for concurrent producer (receiver) and single consumer (playout)
- **Sequence Wrap-Around**: comparator computes `(a - b) & 0xFFFF` and checks if delta < 0x8000
- **Adaptive Delay**: EWMA with alpha=1/16 per RFC 3550 Appendix A.8, clamped to [minDelay, maxDelay]

## RTCP Interval Calculation

```mermaid
flowchart LR
    BW["Session Bandwidth"] --> RTCP_BW["RTCP BW = 5%"]
    RTCP_BW --> SenderCheck{"Senders <= 25%<br/>of members?"}
    SenderCheck -->|yes, sender| SBW["n = senders<br/>bw = 25% of RTCP"]
    SenderCheck -->|yes, receiver| RBW["n = receivers<br/>bw = 75% of RTCP"]
    SenderCheck -->|no| ABW["n = all<br/>bw = 100% of RTCP"]
    SBW --> Interval["T = max(Tmin, n * avgSize * 8 / bw)"]
    RBW --> Interval
    ABW --> Interval
    Interval --> Random["T_rand = T * [0.5, 1.5] / 1.21828"]
```

Parameters from RFC 3550 Section 6.3:
- Minimum interval: 5 seconds (2.5 for initial report)
- Compensation factor: e/(e-1) = 1.21828
- Average packet size updated via EWMA with weight 1/16

## Transport Architecture

```mermaid
graph TD
    Transport["RtpTransport<br/>bind(addr, port)"]
    RTPC["DatagramChannel<br/>RTP (even port)"]
    RTCPC["DatagramChannel<br/>RTCP (odd port = RTP+1)"]
    Sender["RtpSender<br/>encode + send"]
    Receiver["RtpReceiver<br/>virtual thread loop"]
    Codec["RtpCodec / RtcpCodec"]
    JB["JitterBuffer"]
    Session2["RtpSession"]

    Transport --> RTPC
    Transport --> RTCPC
    Sender --> Transport
    Sender --> Codec
    Sender --> Session2
    Receiver --> Transport
    Receiver --> Codec
    Receiver --> JB
    Receiver --> Session2
```

- **RtpTransport**: manages paired `DatagramChannel` for RTP and RTCP
- **RtpSender**: encodes via `RtpCodec`, sends via transport, updates session stats
- **RtpReceiver**: runs on `Thread.ofVirtual()`, decodes packets, checks SSRC collision, inserts into jitter buffer, notifies consumer callback
- **AutoCloseable**: transport and receiver implement proper cleanup

## Receive Pipeline

```mermaid
sequenceDiagram
    participant Net as UDP Network
    participant Recv as RtpReceiver<br/>(virtual thread)
    participant Codec as RtpCodec
    participant Sess as RtpSession
    participant JB as JitterBuffer
    participant App as Consumer

    loop receive loop
        Net->>Recv: DatagramChannel.receive()
        Recv->>Codec: decode(ByteBuffer)
        Codec-->>Recv: RtpPacket
        Recv->>Sess: detectCollision(ssrc)
        alt no collision
            Recv->>Sess: getOrCreateParticipant(ssrc)
            Recv->>Sess: recordReceived(seq, size)
            Recv->>JB: insert(packet)
            alt ACCEPTED
                Recv->>App: packetConsumer.accept(packet)
            end
        else collision
            Recv->>Recv: drop packet, log warning
        end
    end
```

## Thread Safety Model

| Component | Strategy |
|-----------|----------|
| `RtpSession.participants` | `ConcurrentHashMap` -- lock-free concurrent reads/writes |
| `RtpSession.collisionCount` | `AtomicLong` |
| `RtpParticipant` fields | `AtomicLong`, `AtomicReference`, `volatile` |
| `JitterBuffer.buffer` | `ReentrantLock` guarding `TreeMap` |
| `RtpTransport.closed` | `volatile boolean` |
| `RtpReceiver.running` | `AtomicBoolean` |
| `RtpSender` counters | `AtomicLong` |

## Integration with Lego Flow

| Lego Flow Module | Usage in RTP |
|------------------|--------------|
| `blocks` | DP<I,O> for packet processing pipeline, DF<T> for filtering, Statistics for metrics |
| `service` | Lifecycle management, virtual thread pools |
| `media-common` | Shared SDP parser for session description exchange |

---

## Related Documentation

- [Module README](../README.md) | [Requirements](REQUIREMENTS.md) | [Compliance](COMPLIANCE.md)
- [Root Architecture](../../../doc/ARCHITECTURE.md) | [Root README](../../../README.md)

---

**Last Updated**: 2026-07-06
