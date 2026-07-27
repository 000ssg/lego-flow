
# Lego Flow HTTP/3 — Full HTTP/3 over QUIC Implementation

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()
[![Tests](https://img.shields.io/badge/Tests-313_passing-brightgreen.svg)]()

Full HTTP/3 implementation compliant with RFC 9114, built on QUIC transport (RFC 9000) with QPACK header compression (RFC 9204). Provides stream multiplexing without head-of-line blocking, 0-RTT connection establishment, connection migration, and server push. Integrates with the existing `http` module — existing `HttpRouter` handlers work without modification.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                Http3Server / Http3Client                 │
│            (entry points, connection accept)              │
├─────────────────────────────────────────────────────────┤
│                    Http3Connection                        │
│     (stream dispatch, settings, GOAWAY, Alt-Svc)         │
├───────────────────────┬─────────────────────────────────┤
│  Http3StreamManager   │       Http3Feature               │
│  (stream lifecycle,   │  (HttpFeatureCategory.HTTP3,     │
│   request/response)   │   Alt-Svc discovery)             │
├───────────────────────┴─────────────────────────────────┤
│                 Http3RequestAdapter                       │
│   (pseudo-header → HttpRequest → HttpRouter → response   │
│    → HEADERS + DATA frames)                              │
├─────────────────────────────────────────────────────────┤
│              QpackEncoder / QpackDecoder                  │
│   (static table, dynamic table, encoder/decoder streams) │
├─────────────────────────────────────────────────────────┤
│                   HTTP/3 Framing                         │
│        (DATA, HEADERS, CANCEL_PUSH, SETTINGS,            │
│         PUSH_PROMISE, GOAWAY, MAX_PUSH_ID)               │
├─────────────────────────────────────────────────────────┤
│                  QUIC Transport Layer                     │
│     (connection, streams, packets, loss recovery,        │
│      congestion control, 0-RTT, migration)               │
└─────────────────────────────────────────────────────────┘
```

## Features

- **QUIC transport** (RFC 9000) — UDP-based, connection IDs, TLS 1.3 integration, packet protection, loss recovery, congestion control
- **QPACK header compression** (RFC 9204) — static table (99 entries), dynamic table with encoder/decoder streams, no head-of-line blocking
- **Stream multiplexing** — concurrent streams over a single QUIC connection, no head-of-line blocking at transport level
- **0-RTT connection establishment** — resumed connections send application data with the first flight
- **Connection migration** — seamless handover across network changes via connection IDs
- **Server push** — HTTP/3 PUSH_PROMISE with configurable push policy
- **Alt-Svc discovery** — advertise HTTP/3 availability to HTTP/1.1 and HTTP/2 clients
- **HttpRouter bridge** — `Http3RequestAdapter` maps HTTP/3 streams to `HttpRequest`/`HttpResponse`; no handler changes needed
- **Http3Feature** — plugs into the `http` module's feature system (`HttpFeatureCategory.HTTP3`)
- **Virtual threads** — one virtual thread per stream for non-blocking request processing
- **TLS 1.3 handshake** — SSLEngine-based with HandshakePhase tracking (INITIAL/HANDSHAKE/ESTABLISHED/CLOSED), ALPN/cipher/protocol negotiation
- **QPACK dynamic table instructions** — encoder instructions (Insert With Name/Literal, Duplicate, Set Capacity), decoder instructions (Section Ack, Stream Cancel, Insert Count Increment)
- **Real HTTP/3 client requests** — QPACK-encoded HEADERS frames, DATA frames for body, proper response parsing

## Quick Start

### Minimal HTTP/3 Server

```java
var server = Http3Server.builder()
    .port(8443)
    .profile(Http3Profiles.SERVER_DEFAULT)
    .route("/hello", (ctx, req) -> HttpResponse.ok("Hello, HTTP/3!"))
    .build();

server.start(new DefaultContext());
```

### HTTP/3 Client

```java
var client = Http3Client.builder()
    .profile(Http3Profiles.CLIENT_DEFAULT)
    .build();

var response = client.send(new DefaultContext(),
    HttpRequest.get("https://localhost:8443/hello"));
```

### 0-RTT Resumption

```java
var client = Http3Client.builder()
    .profile(Http3Profiles.CLIENT_DEFAULT)
    .zeroRtt(true)
    .sessionCache(new QuicSessionCache())
    .build();

// First connection: full handshake, session ticket saved
var resp1 = client.send(ctx, HttpRequest.get("https://example.com/api"));

// Subsequent connection: 0-RTT, data sent with first packet
var resp2 = client.send(ctx, HttpRequest.get("https://example.com/api"));
```

### Server Push

```java
var server = Http3Server.builder()
    .port(8443)
    .profile(Http3Profiles.SERVER_PUSH_ENABLED)
    .route("/index.html", (ctx, req) -> {
        ctx.push("/styles.css", HttpRequest.get("/styles.css"));
        return HttpResponse.ok(indexHtml).contentType("text/html");
    })
    .build();
```

### Alt-Svc Advertisement

```java
// On an existing HTTP/1.1 or HTTP/2 server, advertise HTTP/3 availability
var featureSet = HttpFeatureSet.builder()
    .profile(HttpFeatureSet.SERVER_STANDARD)
    .add(HttpFeatureCategory.HTTP3)   // adds Http3Feature with Alt-Svc header
    .build();
```

## Standard Profiles

| Profile              | Settings                                                              |
|----------------------|-----------------------------------------------------------------------|
| SERVER_DEFAULT       | Max concurrent streams=100, 0-RTT disabled, push disabled, migration enabled |
| SERVER_PUSH_ENABLED  | Max concurrent streams=100, 0-RTT disabled, push enabled, migration enabled  |
| CLIENT_DEFAULT       | Max concurrent streams=100, 0-RTT enabled, session caching enabled    |

## Package Layout

| Package       | Classes                                                            |
|---------------|--------------------------------------------------------------------|
| `quic/`       | QuicConnection, QuicStream, QuicPacket, QuicPacketCodec, QuicTransport |
| `quic.crypto/`| QuicTls, QuicPacketProtection, QuicRetryToken                     |
| `quic.recovery/`| QuicLossRecovery, QuicCongestionControl, QuicRttEstimator        |
| `quic.migration/`| QuicConnectionMigration, QuicPathValidation                     |
| `qpack/`      | QpackEncoder, QpackDecoder, QpackStaticTable, QpackDynamicTable   |
| `frame/`      | Http3FrameType, Http3Frame, Http3FrameCodec                       |
| `stream/`     | Http3Stream, Http3StreamManager, Http3StreamType                   |
| `connection/` | Http3Settings, Http3Connection                                     |
| `server/`     | Http3Server, Http3ServerHandler, Http3RequestAdapter               |
| `client/`     | Http3Client, QuicSessionCache                                      |
| `feature/`    | Http3Feature, AltSvcHandler                                        |
| `config/`     | Http3Config, Http3Profiles                                         |
| `demo/`       | SimpleHttp3Server, MultiplexingDemo, ZeroRttDemo, MigrationDemo, ServerPushDemo |

## Demo Programs

| Demo                | Description                                              |
|---------------------|----------------------------------------------------------|
| SimpleHttp3Server   | Minimal HTTP/3 server — hello world over QUIC            |
| MultiplexingDemo    | Multiple concurrent streams without head-of-line blocking|
| ZeroRttDemo         | 0-RTT connection resumption with early data              |
| MigrationDemo       | Connection migration across network changes              |
| ServerPushDemo      | Server push with HTTP/3 PUSH_PROMISE                     |
| DemoHttp3All        | Comprehensive demo: all features including TLS handshake, QPACK dynamic table |

Each demo has a corresponding test class in `src/test/java/.../demo/`.

## Build

```bash
mvn compile -pl http3 -am
mvn test -pl http3
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
