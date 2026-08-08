
# Lego Flow CoAP Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-157-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-blue.svg)]()

CoAP protocol module for the Lego Flow framework, providing constrained RESTful communication for IoT devices.

## Overview

This module implements CoAP (RFC 7252) and its key extensions, enabling Java applications to build servers and clients for constrained networks. The architecture layers REST semantics on top of a reliable UDP message transport:

```
CoAP Client / Server (application layer)
  → Resource Handlers (GET, PUT, POST, DELETE on URI paths)
    → Observe Engine (RFC 7641: server-push notifications)
      → Block Transfer (RFC 7959: Block1/Block2 for large payloads)
        → Message Layer (CON/NON/ACK/RST, retransmission, dedup)
          → UDP Transport (service module datagram channels)
```

## Features

- **RFC 7252 CoAP** — Constrained Application Protocol with compact binary format
- **Message types** — Confirmable (CON), Non-confirmable (NON), Acknowledgement (ACK), Reset (RST)
- **REST methods** — GET, PUT, POST, DELETE mapped to CoAP request/response codes
- **Observe (RFC 7641)** — server-push notifications on resource state changes
- **Blockwise transfer (RFC 7959)** — Block1 (request) and Block2 (response) for large payloads
- **Resource discovery** — /.well-known/core endpoint with CoRE Link Format (RFC 6690)
- **Content-Format** — negotiation for text/plain, application/json, application/cbor, application/xml
- **Reliability** — retransmission with exponential backoff, message deduplication by Message ID
- **Congestion control** — NSTART limiting, default ACK_TIMEOUT, ACK_RANDOM_FACTOR
- **Dual API** — sync + async (CompletableFuture), procedural + functional styles

## Quick Start

### Create a CoAP server with resources

```java
var server = CoapServer.builder()
    .port(5683)
    .resource("/temperature", exchange -> {
        exchange.respond(ResponseCode.CONTENT, "22.5",
            ContentFormat.TEXT_PLAIN);
    })
    .resource("/led", exchange -> {
        var payload = exchange.requestPayloadAsString();
        setLed(payload);
        exchange.respond(ResponseCode.CHANGED);
    })
    .build();
server.start();
```

### Send a GET request

```java
var client = CoapClient.builder()
    .host("localhost").port(5683)
    .build();
var response = client.get("/temperature");
System.out.println("Temperature: " + response.payloadAsString());
```

### Observe a resource for changes

```java
client.observe("/temperature", notification ->
    System.out.println("Updated: " + notification.payloadAsString()));
```

### Blockwise transfer for large payloads

```java
var firmware = loadFirmwareBytes();
client.put("/firmware", firmware, ContentFormat.APPLICATION_OCTET_STREAM);
```

## Package Structure

```
ssg.legoflow.coap/
├── server/            — CoAP server: resource registry, request dispatch, response building
├── client/            — CoAP client: request construction, response handling, observe management
├── message/           — Message model: CON/NON/ACK/RST types, options, codes, Message ID
├── resource/          — Resource abstraction: URI paths, handlers, content format negotiation
├── observe/           — Observe engine (RFC 7641): observer registry, notification delivery
├── block/             — Blockwise transfer (RFC 7959): Block1/Block2 assembly, size negotiation
├── discovery/         — Resource discovery: /.well-known/core, CoRE Link Format (RFC 6690)
├── reliability/       — Retransmission, deduplication, congestion control
└── demo/              — Demo applications and examples
```

## Demo Applications

1. **SimpleServerDemo** — CoAP server with temperature and LED resources
2. **ClientRequestDemo** — Client GET/PUT/POST/DELETE requests with various content formats
3. **ObserveDemo** — Observe a resource and receive push notifications on changes
4. **BlockTransferDemo** — Large payload transfer using blockwise options
5. **DiscoveryDemo** — Resource discovery via /.well-known/core and link parsing

## Dependencies

This module depends on:
- `lego-flow-blocks` — DP/DF data processing primitives
- `lego-flow-service` — UDP transport (UdpDataChannel), lifecycle management, virtual threads

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
