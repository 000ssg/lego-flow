
# Lego Flow RTP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-102-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-1.0.0--SNAPSHOT-blue.svg)]()

RTP/RTCP protocol module for the Lego Flow framework, providing real-time media transport with packet encoding/decoding, session management, jitter buffering, and UDP transport.

## Overview

This module implements the Real-time Transport Protocol (RTP) and RTP Control Protocol (RTCP) as defined in RFC 3550. It enables Java applications to send and receive real-time media streams over UDP with full session management and quality-of-service monitoring. The architecture layers protocol handling on top of the framework's service and blocks abstractions:

```
RTP Sender / Receiver (application layer)
  -> Session Management (participant table, SSRC tracking, collision detection)
    -> Jitter Buffer (reordering, adaptive playout delay, duplicate/late handling)
      -> Packet Codec (RTP + RTCP binary encode/decode)
        -> UDP Transport (paired RTP/RTCP datagram channels)
```

## Features

- **RTP Packet Model** -- immutable records for RTP header, packet, and header extensions (RFC 3550 Section 5)
- **RTCP Packet Types** -- sealed interface hierarchy: Sender Report, Receiver Report, Source Description, Goodbye, Application-Defined (RFC 3550 Section 6)
- **Compound Packets** -- RTCP compound packet support with SR/RR-first validation (RFC 3550 Section 6.1)
- **Binary Codec** -- bit-level encode/decode for RTP and all 5 RTCP packet types via ByteBuffer
- **Session Management** -- participant table indexed by SSRC, collision detection and resolution (RFC 3550 Section 8)
- **RTCP Interval Calculator** -- bandwidth-based transmission interval with randomization (RFC 3550 Section 6.3)
- **Adaptive Jitter Buffer** -- packet reordering, duplicate/late detection, overflow handling, EWMA-based adaptive playout delay
- **UDP Transport** -- paired RTP/RTCP ports (even/odd), DatagramChannel-based I/O
- **Virtual Thread Receiver** -- receive loop on virtual threads for efficient blocking I/O
- **SDES Items** -- all 9 source description item types (CNAME, NAME, EMAIL, PHONE, LOC, TOOL, NOTE, PRIV, END)
- **Thread Safety** -- ConcurrentHashMap, AtomicLong, ReentrantLock, volatile fields throughout

## Quick Start

### Create and encode an RTP packet

```java
var packet = RtpPacket.of(
    96,             // payload type (dynamic)
    1,              // sequence number
    160L,           // timestamp (20ms at 8kHz)
    0x12345678L,    // SSRC
    audioFrame      // payload bytes
);
ByteBuffer encoded = RtpCodec.encode(packet);
```

### Decode an RTP packet

```java
ByteBuffer received = ByteBuffer.wrap(udpData);
RtpPacket packet = RtpCodec.decode(received);
int seq = packet.header().sequenceNumber();
long ssrc = packet.header().ssrc();
byte[] payload = packet.payload();
```

### Set up a session and jitter buffer

```java
var session = new RtpSession("user@example.com");
var jitterBuffer = new JitterBuffer(500, 20, 200);

// Insert received packets
var result = jitterBuffer.insert(packet);
if (result == JitterBuffer.InsertResult.ACCEPTED) {
    // Poll for playout
    jitterBuffer.poll().ifPresent(this::playAudio);
}
```

### Send and receive over UDP

```java
// Bind transport to paired ports
var transport = RtpTransport.bind(
    new InetSocketAddress("0.0.0.0", 5004), 5004);

// Send packets
var sender = new RtpSender(transport, session, remoteAddress);
sender.send(packet);

// Receive packets on virtual thread
var receiver = new RtpReceiver(transport, session, jitterBuffer);
receiver.start(pkt -> System.out.println("Received: " + pkt));
```

### Build and encode RTCP compound packets

```java
var sr = new SenderReport(ssrc, ntpTimestamp, rtpTimestamp,
    packetCount, octetCount, List.of(receptionReport));
var sdes = new SourceDescription(List.of(
    new SdesChunk(ssrc, List.of(new SdesItem(SdesItem.Type.CNAME, "user@host")))));
var compound = new CompoundPacket(List.of(sr, sdes));
ByteBuffer encoded = RtcpCodec.encodeCompound(compound);
```

## Package Structure

```
ssg.legoflow.media.rtp/
+-- packet/            -- RTP packet model: RtpPacket, RtpHeader, HeaderExtension
+-- rtcp/              -- RTCP packets: SR, RR, SDES, BYE, APP, CompoundPacket, ReceptionReport
+-- codec/             -- Binary codecs: RtpCodec, RtcpCodec (encode/decode via ByteBuffer)
+-- session/           -- Session management: RtpSession, RtpParticipant, RtcpIntervalCalculator
+-- buffer/            -- Jitter buffer: JitterBuffer (adaptive reordering, duplicate/late detection)
+-- transport/         -- UDP transport: RtpTransport, RtpSender, RtpReceiver (virtual threads)
```

## Dependencies

This module depends on:
- `lego-flow-blocks` -- DP/DF data processing primitives
- `lego-flow-service` -- service lifecycle management, virtual threads
- `lego-flow-media-common` -- shared SDP parser (RFC 4566)

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
