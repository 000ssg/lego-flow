---
name: wire-protocol-debugging
description: "Fix protocol interop by capturing and comparing wire bytes."
version: 1.0.0
author: Hermes Agent
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [protocol, binary, wire-format, interop, debugging, tcp-proxy]
    related_skills: [systematic-debugging]
---

# Wire Protocol Debugging

## Overview

When a custom protocol implementation fails to interoperate with a real server, guessing at the wire format wastes time. The fastest path to a working implementation is to **capture the exact bytes** a reference client sends and compare them byte-by-byte against your implementation.

## When to Use

- Implementing a new binary protocol codec (AMQP, MQTT, HTTP/2, custom RPC)
- Interop tests fail with `EOFException`, `Connection reset`, `INTERNAL_ERROR`, or protocol-level rejection
- A spec-compliant implementation is rejected by the server (specs often differ from reality)
- You suspect the server has non-standard behavior not documented in the spec

## Step 1: Capture Reference Client Wire Traffic

Set up a TCP proxy between the reference client and the real server that captures all bytes in both directions.

### Using an Existing Pass-Through Proxy

If the project has a `PassThroughConnection` or similar TCP relay with `DataInterceptor` support, use it. It provides per-direction capture, concurrent pipelining, and lifecycle management out of the box.

```java
CaptureLog log = new CaptureLog();
PassThroughConnection proxy = new PassThroughConnection();
proxy.addRoute(proxyPort, new InetSocketAddress("server", serverPort));
proxy.addInterceptor(new CapturingInterceptor(log));
proxy.start();
referenceClient.connect("localhost", proxyPort);
referenceClient.doOperation();
byte[] c2s = mergeDirection(log, LOCAL_TO_REMOTE);
byte[] s2c = mergeDirection(log, REMOTE_TO_LOCAL);
```

### Standalone Proxy (No Framework Available)

Write a minimal bidirectional proxy with concurrent read loops:

```java
Thread c2s = new Thread(() -> pipe(clientIn, remoteOut, clientToServer));
Thread s2c = new Thread(() -> pipe(remoteIn, clientOut, serverToClient));
c2s.start(); s2c.start();
```

**Pitfall:** Sequential `pipe()` calls deadlock — the second direction never starts. Always run both directions concurrently.

## Step 2: Capture Your Client Wire Traffic

Repeat step 1 with your custom client. Even if your client crashes mid-operation, the proxy still captures everything sent before the failure.

## Step 3: Parse and Compare Frames

Write a Python script to parse the raw bytes into protocol frames and compare:

```python
import struct
def parse_frames(data, label):
    off = len(greeting)  # skip protocol greeting
    while off + 7 <= len(data):
        ft, chan, size = data[off], struct.unpack('>H', data[off+1:3])[0], struct.unpack('>I', data[off+3:7])[0]
        payload = data[off+7:off+7+size]
        print(f"{label}: type={ft} chan={chan} size={size} args={payload.hex()}")
        off += 7 + size + 1  # +1 for frame-end octet
```

### What to Compare (per frame)

| Field | Common mismatch |
|-------|----------------|
| Total payload size | Missing `reserved-1` short, extra padding bytes |
| String encoding | `longstr` (4-byte len) vs `shortstr` (1-byte len) |
| Bit fields | MSB-first (`0x80 >> i`) vs LSB-first (`0x01 << i`) |
| Integer sizes | `short` (2) vs `int` (4) vs `long` (8) |
| Reserved fields | Omitted entirely vs present (often required by server) |

## Pitfalls

- **Spec ≠ implementation.** RabbitMQ, Qpid, and other brokers each have deviations from the formal spec. Always capture real traffic from a working reference client.
- **Bit packing direction.** LSB-first vs MSB-first produces different bytes for the same booleans. Verify with the reference client capture.
- **Proxy buffering.** TCP can split or coalesce reads. Always merge all captured chunks for each direction before parsing frames.
- **Heartbeat frames.** Brokers interleave heartbeats with method frames. Filter them out before comparing.

## Reference

See `references/amqp-091-rabbitmq-deviations.md` for AMQP 0-9-1 field-level spec vs RabbitMQ deviations.
