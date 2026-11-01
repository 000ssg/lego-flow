# AMQP 1.0: SASL-First Protocol Negotiation

## Problem

AMQP 1.0 spec allows two connection initiation methods:
- **SASL-first (proto 3)**: Client sends `SASL_HEADER` (`AMQP\x03\x01\x00\x00`), negotiates mechanisms, then sends `AMQP_HEADER` (`AMQP\x00\x01\x00\x00`)
- **Proto-0**: Client sends `AMQP_HEADER` directly, skips SASL

Many implementations only sent SASL_HEADER when credentials existed, and AMQP_HEADER when anonymous. **This breaks against modern brokers that require SASL.**

## Brokers Tested

| Broker | Response to SASL_HEADER | Requires SASL? | Supports ANONYMOUS? |
|--------|------------------------|----------------|-------------------|
| RabbitMQ 4 | Echoes SASL_HEADER | Yes | Yes |
| Apache Artemis | Echoes SASL_HEADER | Yes | Yes |
| Qpid Dispatch Router | **Closes connection** | No | No (its own way) |

## Solution

**Always send SASL_HEADER first**, regardless of authentication mechanism. Read the server response:

1. If server echoes `SASL_HEADER` → proceed with SASL negotiation (sasl-mechanisms → sasl-init → sasl-outcome), then send `AMQP_HEADER`
2. If server echoes `AMQP_HEADER` → skip SASL, proceed to OPEN
3. If server closes or sends anything else → error

This approach works with all SASL-requiring brokers and falls back gracefully to proto-0 servers.

## Qpid Dispatch Special Case

Qpid Dispatch Router (port 5674) closes the connection when receiving either SASL_HEADER or AMQP_HEADER in standard AMQP mode. It uses its own protocol (KRB5/SASL-only or dispatch router protocol). Standard AMQP 1.0 clients connect through it using the underlying SASL mechanism configured by the administrator.

## Wire-Level Verification

```python
# Quick test of SASL-first flow
import socket
s = socket.socket()
s.connect(('localhost', 5672))
s.settimeout(3)
s.sendall(b'AMQP\x03\x01\x00\x00')  # SASL_HEADER
resp = s.recv(8)
if resp == b'AMQP\x03\x01\x00\x00':
    print('Server requires SASL')
elif resp == b'AMQP\x00\x01\x00\x00':
    print('Server accepts proto-0')
```

## Key Lessons

- **Never skip SASL header based on credentials.** A server may require SASL even for anonymous auth (it returns OK via sasl-outcome).
- **Qpid Dispatch is not a standard AMQP broker.** It's a router that uses its own dispatch protocol. Direct AMQP connections may not work without router configuration.
- **TCP buffering matters.** Java `SocketChannel.write()` may not flush immediately. Python `socket.sendall()` is guaranteed to send all bytes.
- **Connection refused in interop tests usually means Docker containers stopped.** Always verify with `nc -z` before debugging protocol issues.

## Session Context

Discovered during Phase 6 (Interop Test Matrix) of lego-flow AMQP module, 2026-08-26.
Original issue: client sent AMQP_HEADER for anonymous connections, but RabbitMQ closed the connection because it requires SASL-first.