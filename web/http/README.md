
# Lego Flow HTTP — Full HTTP/1.1 Implementation

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Version](https://img.shields.io/badge/Version-0.1.0-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()
[![Tests](https://img.shields.io/badge/Tests-542_passing-brightgreen.svg)]()

Full RFC 2616 HTTP/1.1 implementation with a pluggable feature system, SSL/TLS support, WebSocket upgrade, static content serving, and standard profiles for server and client configurations.

## Features by Category

- **CORE** — HTTP request/response parsing, status codes, methods
- **TRANSFER** — Chunked transfer encoding, content-length handling, stream-oriented partial data accumulation
- **CONTENT** — Content negotiation with q-factor weighting, media types, charset
- **CACHING** — ETag generation, If-Modified-Since, 304 Not Modified responses
- **CONNECTION** — Keep-alive, connection lifecycle management
- **ENTITY** — Entity body handling, byte range requests
- **METADATA** — Date, Server, User-Agent header management
- **SECURITY** — SSL/TLS as DataFilter, HSTS (RFC 6797)
- **WEBSOCKET** — WebSocket upgrade handshake (RFC 6455)
- **STATIC** — Static file serving, directory listings

## Quick Start

### Minimal Server

```java
var server = HttpServer.builder()
    .port(8080)
    .featureSet(HttpFeatureSet.SERVER_MINIMAL)
    .route("/hello", (ctx, req) -> HttpResponse.ok("Hello, World!"))
    .build();

server.start(new DefaultContext());
```

### Minimal Client

```java
var client = HttpClient.builder()
    .featureSet(HttpFeatureSet.CLIENT_MINIMAL)
    .build();

var response = client.send(new DefaultContext(),
    HttpRequest.get("http://localhost:8080/hello"));
```

### Feature Configuration

```java
var featureSet = HttpFeatureSet.builder()
    .profile(HttpFeatureSet.SERVER_STANDARD)
    .add(HttpFeatureCategory.SECURITY)
    .add(HttpFeatureCategory.WEBSOCKET)
    .build();

var server = HttpServer.builder()
    .port(8443)
    .featureSet(featureSet)
    .sslFilter(new SslDataFilter(keyStore, trustStore))
    .build();
```

## Standard Profiles

| Profile            | Categories Included                                              |
|--------------------|------------------------------------------------------------------|
| SERVER_MINIMAL     | CORE, TRANSFER                                                   |
| SERVER_STANDARD    | CORE, TRANSFER, CONTENT, CACHING, CONNECTION, ENTITY             |
| SERVER_FULL        | All categories                                                   |
| CLIENT_MINIMAL     | CORE, TRANSFER                                                   |
| CLIENT_STANDARD    | CORE, TRANSFER, CONTENT, CONNECTION, ENTITY                      |
| CLIENT_FULL        | All categories                                                   |

## Demo Programs

Runnable demos are organized under `demo/` by complexity tier:

| Category | Demos |
|----------|-------|
| **Server** | SecureServer (SSL+HSTS), WebSocketServer, CachingServer, FullFeaturedServer |
| **Client** | AdaptiveClient (runtime profile switching), RangeClient, SecureClient, WebSocketClient |
| **Multi** | MultiServerDemo, ClientServerPairDemo, LoadBalancedDemo |

Each demo has a corresponding test class exercising it end-to-end.

## Protocol Codec Design

All ByteBuffer-based codecs (`ChunkedCodec`, `WebSocketFrameCodec`, `HttpProtocolCodec`) handle partial TCP reads gracefully. Each codec maintains an internal accumulator buffer, parses complete protocol units as they become available, and saves any remainder for the next read. Callers do not need to pre-assemble complete messages before invoking the codec. See [Architecture](doc/ARCHITECTURE.md) for details.

## Build

```bash
mvn compile -pl http -am
mvn test -pl http
```

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
