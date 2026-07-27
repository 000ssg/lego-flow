# RTP Module -- Development Guide

## Module Purpose

The `rtp` module implements RTP (Real-time Transport Protocol) and RTCP (RTP Control Protocol) as defined in RFC 3550. It provides packet encoding/decoding, session management with SSRC tracking, adaptive jitter buffering, and UDP transport with paired RTP/RTCP ports. Built on the `service` module for UDP transport and `blocks` for data processing primitives.

## Key Classes

- `RtpPacket` -- complete RTP packet (header + payload), immutable record
- `RtpHeader` -- fixed header with CSRC list and optional header extension (12+ bytes)
- `HeaderExtension` -- profile-specific header extension (RFC 3550 Section 5.3.1)
- `RtcpPacket` -- sealed interface for all RTCP packet types (SR, RR, SDES, BYE, APP)
- `CompoundPacket` -- compound RTCP packet (must begin with SR or RR)
- `RtpCodec` -- binary codec for RTP packets to/from ByteBuffer
- `RtcpCodec` -- binary codec for RTCP packets to/from ByteBuffer (including compound)
- `RtpSession` -- session state: participant table, SSRC collision detection, statistics
- `RtpParticipant` -- per-SSRC participant with sender/receiver statistics and jitter tracking
- `RtcpIntervalCalculator` -- RTCP transmission interval per RFC 3550 Section 6.3
- `JitterBuffer` -- adaptive jitter buffer with reordering, duplicate/late detection, overflow handling
- `RtpTransport` -- paired UDP channels for RTP (even port) and RTCP (odd port)
- `RtpSender` -- encodes and sends RTP/RTCP packets over UDP
- `RtpReceiver` -- virtual-thread receiver loop with decode, collision check, jitter buffer insertion

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `packet` | RTP packet model: `RtpPacket`, `RtpHeader`, `HeaderExtension` -- immutable records with validation |
| `rtcp` | RTCP packet types: `SenderReport`, `ReceiverReport`, `SourceDescription`, `Goodbye`, `ApplicationDefined`, `CompoundPacket`, `ReceptionReport`, `SdesChunk`, `SdesItem` |
| `codec` | Binary codecs: `RtpCodec` (encode/decode RTP), `RtcpCodec` (encode/decode all RTCP types including compound packets) |
| `session` | Session management: `RtpSession` (participant table, SSRC collision), `RtpParticipant` (per-SSRC stats), `RtcpIntervalCalculator` (bandwidth-based interval) |
| `buffer` | Jitter buffer: `JitterBuffer` (adaptive playout delay, reordering, duplicate/late/overflow handling) |
| `transport` | UDP transport: `RtpTransport` (paired ports), `RtpSender` (encode + send), `RtpReceiver` (virtual-thread receive loop) |

## RTP-Specific Coding Conventions

### Packet Types
- **RTP** (RFC 3550 Section 5): version=2, padding, extension, CC, marker, PT, seq, timestamp, SSRC, CSRC list, header extension
- **RTCP** (RFC 3550 Section 6): SR (200), RR (201), SDES (202), BYE (203), APP (204)
- Compound packets must start with SR or RR, followed by SDES (with CNAME)

### SSRC and Collision Detection
- SSRC generated via `SecureRandom` (32-bit unsigned)
- Collision detection checks incoming SSRC against local SSRC
- Resolution generates a new non-colliding SSRC

### RTCP Interval Calculation (RFC 3550 Section 6.3)
- 5% of session bandwidth allocated to RTCP
- 25% of RTCP bandwidth to senders (if senders <= 25% of members)
- Minimum interval: 5 seconds (2.5 for initial)
- Randomization: 0.5 to 1.5 times deterministic interval
- Compensation factor: e/(e-1) = 1.21828
- Average packet size tracked via EWMA (1/16 weight)

### Jitter Buffer
- `TreeMap<Integer, RtpPacket>` with sequence-number comparator handling 16-bit wrap-around
- `ReentrantLock` for thread safety (concurrent producer, single consumer)
- Insert results: ACCEPTED, DUPLICATE, LATE
- Adaptive delay via EWMA (alpha = 1/16 per RFC 3550 A.8)
- Default: capacity=500, min delay=20ms, max delay=200ms

### Transport
- Even RTP port + odd RTCP port (RTP + 1) per RFC 3550 Section 11
- Non-blocking `DatagramChannel` for both channels
- Maximum UDP datagram size: 1500 bytes
- `RtpReceiver` runs on a virtual thread via `Thread.ofVirtual()`

### Unsigned Integer Handling
- All 32-bit fields (SSRC, timestamp, packet counts) stored as `long` masked with `0xFFFFFFFFL`
- Sequence numbers stored as `int` masked with `0xFFFF`
- NTP timestamp stored as 64-bit `long`

## Testing Practices

- Codec round-trip tests: encode -> decode for RTP and all RTCP packet types
- Jitter buffer tests: insertion, duplicate detection, late packet handling, overflow, adaptive delay, sequence wrap-around
- Session tests: participant management, SSRC collision detection/resolution, sender counting
- RTCP interval calculator tests: deterministic and randomized interval computation, EWMA update
- Participant tests: send/receive statistics tracking, jitter update
- Transport tests: bind, send/receive, close lifecycle
- Test count: 102

---

**Last Updated**: 2026-07-06
**For AI assistant versions**
