# RTP Module -- Requirements

## Timeline Overview

- **Module Added**: June 2026
- **Tests**: 102
- **Dependencies**: blocks (DP/DF), service (lifecycle, virtual threads), media-common (SDP)
- **Standards**: RFC 3550 (RTP: A Transport Protocol for Real-Time Applications)

---

## Requirements

### RTP Packet Model
1. Represent RTP packets as immutable records: header (fixed fields + CSRC + extension) and payload
2. Fixed header: version (2), padding, extension, CC, marker, payload type (0-127), sequence number (0-65535), timestamp (32-bit unsigned), SSRC (32-bit unsigned)
3. CSRC list: 0 to 15 contributing source identifiers (32-bit unsigned each)
4. Header extension: 16-bit profile identifier and variable-length data (multiple of 4 bytes)
5. Validate all field ranges in record compact constructors
6. Defensive copying for byte array fields (payload, extension data)
7. Factory methods for common creation patterns: `RtpPacket.of()`, `RtpPacket.withMarker()`

### RTCP Packet Types
1. Sealed interface `RtcpPacket` with five permitted implementations: SenderReport, ReceiverReport, SourceDescription, Goodbye, ApplicationDefined
2. Sender Report (PT=200): SSRC, NTP timestamp (64-bit), RTP timestamp, sender packet count, sender octet count, reception report blocks (0-31)
3. Receiver Report (PT=201): SSRC, reception report blocks (0-31)
4. Source Description (PT=202): list of SDES chunks (1-31), each with SSRC and list of items
5. SDES item types: END (0), CNAME (1), NAME (2), EMAIL (3), PHONE (4), LOC (5), TOOL (6), NOTE (7), PRIV (8)
6. Goodbye (PT=203): list of SSRC/CSRC identifiers (1-31) with optional reason string (max 255 bytes)
7. Application-Defined (PT=204): SSRC, subtype (0-31), 4-character ASCII name, data (multiple of 4 bytes)
8. Reception Report block: SSRC, fraction lost (0-255), cumulative lost (24-bit signed), highest sequence number received, interarrival jitter, last SR timestamp, delay since last SR
9. Compound Packet: ordered list of RTCP packets; first must be SR or RR

### Binary Codec
1. RTP codec: encode RtpPacket to ByteBuffer and decode ByteBuffer to RtpPacket
2. Bit-level manipulation: first byte (V, P, X, CC), second byte (M, PT)
3. Handle 32-bit unsigned fields via `long` with masking (`& 0xFFFFFFFFL`)
4. Handle 16-bit unsigned fields via `int` with masking (`& 0xFFFF`)
5. Padding support: decode strips padding bytes indicated by last byte of padding
6. RTCP codec: encode/decode all 5 RTCP packet types with common 4-byte header (V, P, count, PT, length)
7. Compound packet codec: encode/decode multiple RTCP packets sequentially in a single ByteBuffer
8. SDES encoding: type + length + value for each item, END marker, 4-byte boundary padding
9. Goodbye encoding: SSRC list + optional length-prefixed reason with padding
10. APP encoding: SSRC + 4-byte ASCII name + data
11. Reject packets with unsupported version or invalid structure

### Session Management
1. Maintain participant table as ConcurrentHashMap indexed by SSRC
2. Generate local SSRC via SecureRandom (32-bit unsigned)
3. Automatic creation of local participant with CNAME on session creation
4. Lazy creation of remote participants on first packet (getOrCreateParticipant)
5. SSRC collision detection: compare incoming SSRC against local SSRC
6. SSRC collision resolution: generate new non-colliding SSRC
7. Prevent removal of local participant
8. Track session-level statistics: sender count, participant count, collision count

### Participant Statistics
1. Per-participant send statistics: packets sent, bytes sent
2. Per-participant receive statistics: packets received, bytes received
3. Track highest sequence number received (with wrap-around handling)
4. Track interarrival jitter estimate
5. Track packets lost count
6. Record last activity time and last SR received time
7. Sender flag: mark participant as sender after first sent packet
8. Thread-safe via AtomicLong, AtomicReference, and volatile fields

### RTCP Interval Calculator
1. Compute deterministic interval based on session bandwidth, participant/sender count, and role
2. RTCP bandwidth fraction: 5% of session bandwidth
3. Sender bandwidth fraction: 25% of RTCP bandwidth (when senders <= 25% of members)
4. Minimum interval: 5 seconds (2.5 for initial report)
5. Compute randomized interval: 0.5 to 1.5 times deterministic, divided by compensation factor e/(e-1)
6. Update average RTCP packet size via EWMA with weight 1/16
7. Track initial transmission state

### Jitter Buffer
1. Buffer incoming RTP packets ordered by sequence number
2. Handle 16-bit sequence number wrap-around in comparator
3. Detect and reject duplicate packets
4. Detect and reject late packets (sequence number below expected)
5. Handle capacity overflow by dropping oldest packet
6. Poll: return next expected sequence number packet
7. Skip: return next available packet (for missed deadlines)
8. Adaptive playout delay via EWMA (alpha=1/16), clamped to [minDelay, maxDelay]
9. Thread-safe via ReentrantLock for concurrent producer/consumer
10. Statistics: total received, total played, duplicates, late packets, overflows
11. Default settings: capacity=500, min delay=20ms, max delay=200ms

### UDP Transport
1. Paired UDP ports: even port for RTP, odd port (RTP+1) for RTCP per RFC 3550 Section 11
2. Bind to specified local address and port pair
3. Validate RTP port is even
4. Send and receive RTP data via DatagramChannel
5. Send and receive RTCP data via separate DatagramChannel
6. Maximum packet size: 1500 bytes
7. AutoCloseable with proper cleanup of both channels
8. Closed state check on all operations

### RTP Sender
1. Encode RTP packets via RtpCodec and send over transport
2. Update session statistics (local participant recordSent)
3. Send RTCP packets to destination RTCP port (RTP port + 1)
4. Track send statistics: packets sent, bytes sent

### RTP Receiver
1. Run receive loop on virtual thread (Thread.ofVirtual)
2. Decode incoming packets via RtpCodec
3. Check for SSRC collision and drop colliding packets
4. Update participant table and statistics
5. Insert packets into jitter buffer
6. Notify consumer callback for accepted packets
7. Graceful shutdown via AtomicBoolean flag and thread interrupt
8. Handle IOException and decode exceptions without crashing the loop

---

## Related Documentation

- [Module README](../README.md) | [Architecture](ARCHITECTURE.md) | [Compliance](COMPLIANCE.md)
- [Root README](../../../README.md) | [Root Architecture](../../../doc/ARCHITECTURE.md)

---

**Last Updated**: 2026-07-06
