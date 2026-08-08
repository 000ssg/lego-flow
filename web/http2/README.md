
# Lego Flow HTTP/2 — Full HTTP/2 Implementation

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Version](https://img.shields.io/badge/Version-0.1.0-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()
[![Tests](https://img.shields.io/badge/Tests-180_passing-brightgreen.svg)]()

Full HTTP/2 implementation compliant with RFC 7540 and RFC 9113. Provides binary framing, HPACK header compression, stream multiplexing with flow control, server push, and H2c upgrade. Integrates with the existing `http` module — existing `HttpRouter` handlers work without modification.

## Features

- **Binary framing** — all 10 frame types (DATA, HEADERS, PRIORITY, RST_STREAM, SETTINGS, PUSH_PROMISE, PING, GOAWAY, WINDOW_UPDATE, CONTINUATION)
- **HPACK header compression** — static table (61 entries), dynamic table with configurable size, Huffman coding (RFC 7541)
- **Stream multiplexing** — concurrent streams over a single TCP connection, full stream state machine per RFC 7540 §5
- **Flow control** — connection-level and stream-level windows, automatic WINDOW_UPDATE emission
- **Server push** — PUSH_PROMISE frames with configurable push policy
- **H2c upgrade** — HTTP/1.1 → HTTP/2 cleartext upgrade handshake
- **HttpRouter bridge** — `Http2RequestAdapter` maps HTTP/2 streams to `HttpRequest`/`HttpResponse`; no handler changes needed
- **Http2Feature** — plugs into the `http` module's feature system (`HttpFeatureCategory.HTTP2`)
- **Virtual threads** — one virtual thread per stream for non-blocking frame processing
- **Standard profiles** — `Http2Profiles.SERVER_DEFAULT`, `SERVER_PUSH_ENABLED`, `CLIENT_DEFAULT`

## Quick Start

### Minimal HTTP/2 Server

```java
var server = Http2Server.builder()
    .port(8443)
    .profile(Http2Profiles.SERVER_DEFAULT)
    .route("/hello", (ctx, req) -> HttpResponse.ok("Hello, HTTP/2!"))
    .build();

server.start(new DefaultContext());
```

### HTTP/2 Client

```java
var client = Http2Client.builder()
    .profile(Http2Profiles.CLIENT_DEFAULT)
    .build();

var response = client.send(new DefaultContext(),
    HttpRequest.get("https://localhost:8443/hello"));
```

### Enabling HTTP/2 on an Existing HTTP/1.1 Server

```java
var featureSet = HttpFeatureSet.builder()
    .profile(HttpFeatureSet.SERVER_STANDARD)
    .add(HttpFeatureCategory.HTTP2)   // adds Http2Feature
    .build();

var server = HttpServer.builder()
    .port(8443)
    .featureSet(featureSet)
    .sslFilter(new SslDataFilter(keyStore, trustStore))
    .build();
```

### Server Push

```java
var server = Http2Server.builder()
    .port(8443)
    .profile(Http2Profiles.SERVER_PUSH_ENABLED)
    .route("/index.html", (ctx, req) -> {
        // Push stylesheet before sending the main response
        ctx.push("/styles.css", HttpRequest.get("/styles.css"));
        return HttpResponse.ok(indexHtml).contentType("text/html");
    })
    .build();
```

### H2c Upgrade (cleartext HTTP/2)

```java
var server = Http2Server.builder()
    .port(8080)
    .profile(Http2Profiles.SERVER_DEFAULT)
    .upgradeHandler(new Http2UpgradeHandler())  // handles Upgrade: h2c
    .build();
```

## Standard Profiles

| Profile              | Settings                                                        |
|----------------------|-----------------------------------------------------------------|
| SERVER_DEFAULT       | Max concurrent streams=100, initial window=65535, push disabled |
| SERVER_PUSH_ENABLED  | Max concurrent streams=100, initial window=65535, push enabled  |
| CLIENT_DEFAULT       | Max concurrent streams=100, initial window=65535                |

## Package Layout

| Package       | Classes                                                            |
|---------------|--------------------------------------------------------------------|
| `frame/`      | Http2FrameType, Http2Frame, Http2FrameCodec, Http2Flags, Http2ErrorCode |
| `hpack/`      | HpackEncoder, HpackDecoder, HpackStaticTable, HpackDynamicTable, HpackHuffman |
| `stream/`     | Http2Stream, Http2StreamState, Http2StreamManager, Http2FlowControl |
| `connection/` | Http2Settings, Http2Connection, Http2ConnectionPreface            |
| `server/`     | Http2Server, Http2ServerHandler, Http2RequestAdapter              |
| `client/`     | Http2Client                                                        |
| `feature/`    | Http2Feature, Http2UpgradeHandler                                 |
| `config/`     | Http2Config, Http2Profiles                                        |
| `demo/`       | SimpleHttp2Server, MultiplexingDemo, ServerPushDemo, H2cUpgradeDemo, FlowControlDemo |

## Demo Programs

| Demo                | Description                                              |
|---------------------|----------------------------------------------------------|
| SimpleHttp2Server   | Minimal HTTP/2 server — hello world over TLS             |
| MultiplexingDemo    | Multiple concurrent streams over a single connection     |
| ServerPushDemo      | Server push with PUSH_PROMISE frames                     |
| H2cUpgradeDemo      | HTTP/1.1 → HTTP/2 cleartext upgrade                      |
| FlowControlDemo     | Connection and stream flow control with large payloads   |

Each demo has a corresponding test class in `src/test/java/.../demo/`.

## Build

```bash
mvn compile -pl http2 -am
mvn test -pl http2
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
