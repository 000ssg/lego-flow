
# Lego Flow RTSP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-203-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-SNAPSHOT-blue.svg)]()

RTSP 2.0 protocol module for the Lego Flow framework, providing real-time media streaming control with server, client, and interleaved binary transport.

## Overview

This module implements RTSP 2.0 (RFC 7826) from scratch, enabling Java applications to build streaming media servers and clients. The architecture layers protocol handling with interleaved RTP-over-TCP support:

```
Demo Applications (StreamingServerDemo, ClientPlaybackDemo)
  → Client / Server (RtspClient, RtspServer, RtspHandler)
    → Interleaved Transport (RTP-over-TCP framing)
      → Protocol (Codec, Request/Response, Headers, Methods, Status)
```

## Features

- **RTSP 2.0 (RFC 7826)** — full protocol implementation for media streaming control
- **Protocol codec** — dual-mode: static one-shot encode/decode (thread-safe) and stream-oriented ByteBuffer accumulation (per-connection)
- **Server** — virtual-thread-per-connection with `RtspHandler` callback pattern, session management, media source registry
- **Client** — OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN, GET_PARAMETER with automatic CSeq numbering
- **Session management** — server-side `RtspSession` with unique session IDs, client-side `RtspClientSession` tracking
- **Stream controller** — media stream lifecycle with state transitions (INIT, READY, PLAYING, PAUSED)
- **Interleaved transport** — RTP-over-TCP binary frame interleaving ($ prefix, channel ID, length, payload)
- **Transport header** — parsing for RTP/AVP, unicast/multicast, client/server port ranges
- **Range header** — NPT (Normal Play Time) range parsing for seek/playback control
- **Case-insensitive headers** — multi-value `RtspHeaders` collection with format/parse support

## Quick Start

### Start a streaming server

```java
var server = new RtspServer(8554);
server.registerMedia(myMediaSource);
server.start();
```

### Control a media stream with the client

```java
var client = new RtspClient("rtsp://server:8554/media");
var options = client.options();
var sdp = client.describe();
var setup = client.setup("RTP/AVP;unicast;client_port=8000-8001");
var play = client.play();
// ... streaming in progress ...
client.pause();
client.teardown();
```

### Interleaved RTP-over-TCP

```java
var frame = new InterleavedFrame(channel, payload);
byte[] encoded = InterleavedFrameCodec.encode(frame);
InterleavedFrame decoded = InterleavedFrameCodec.decode(ByteBuffer.wrap(encoded));
```

## Package Structure

```
ssg.legoflow.media.rtsp/
├── protocol/          — Codec, request/response records, headers, methods, status codes, transport/range headers
├── server/            — RTSP server, handler, session, stream controller, media source
├── client/            — RTSP client, session tracking, setup result
├── interleaved/       — RTP-over-TCP binary frame interleaving
└── demo/              — Streaming server and client playback demos
```

## Demo Applications

1. **StreamingServerDemo** — Starts an RTSP server, registers media sources, handles client connections
2. **ClientPlaybackDemo** — Client-side DESCRIBE/SETUP/PLAY/PAUSE/TEARDOWN sequence

## Dependencies

This module depends on:
- `media-common` — shared SDP parser (RFC 4566)
- `slf4j-api` — logging

---

**Part of the [Lego Flow](../../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../../README.md) | [Root Architecture](../../doc/ARCHITECTURE.md)
