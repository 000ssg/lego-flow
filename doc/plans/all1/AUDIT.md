# All Protocols Compliance Audit

> **Date:** 2026-11-05
> **Branch:** `cleanup-protocols`
> **Method:** grep for Socket/ServerSocket/SocketChannel/Selector in src/main + review of Transport SPI presence

---

## Reference Pattern (from messaging protocols)

A module is **compliant** iff it has **all six**:

| # | Criterion | What it looks like |
|---|-----------|--------------------|
| 1 | Byte-level **Transport SPI** | `XxxTransport { send(ByteBuffer); receive(ByteBuffer); receiveWithTimeout(...); close(); isOpen(); }` |
| 2 | **InMemory** transport | `createPair()` returns two connected byte transports |
| 3 | **Pipeline** transport | DataChannel + ring buffer + outbound queue; never writes socket from calling thread |
| 4 | **Service layer** | `XxxClientService`/`XxxServerService` extend `AbstractService`; TCP lifecycle in manager |
| 5 | **Codec stream reassembly** | accumulator pattern; handles frame split across N reads |
| 6 | **Test location + coverage** | unit in `<module>/src/test`; Docker interop only in `interop-tests`; ≥80% coverage |

---

## Compliance Map

### ✅ BASELINE (Already Compliant)

| Module | Tests | Notes |
|--------|-------|-------|
| messaging/mqtt | 355 | Full reference pattern |
| messaging/stomp | 233 | Full reference pattern |
| messaging/amqp | 640 | Full reference pattern |
| messaging/wamp | 295 | WebSocket-bound, compliant |

### ❌ NEEDS MIGRATION

#### Database Protocols

| Module | Raw Sockets | InMemory | Pipeline | Service | Codec | Tests | Priority |
|--------|-------------|----------|----------|---------|-------|-------|----------|
| database/mysql | ServerSocket, Socket | No | No | No | Yes (packet) | 27 | P1 |
| database/postgresql | ServerSocket, Socket | No | No | No | Yes (4-byte len) | 20 | P1 |
| database/redis | ServerSocket, Socket | Partial | No | Stub | Yes (RESP) | 21 | P1 |

#### Email Protocols

| Module | Raw Sockets | InMemory | Pipeline | Service | Codec | Tests | Priority |
|--------|-------------|----------|----------|---------|-------|-------|----------|
| email/imap | ServerSocket, Socket | No | No | Stub | Yes (line) | 27 | P2 |
| email/smtp | ServerSocket, Socket | No | No | Stub | Yes (line) | 28 | P2 |

#### Network Protocols (TCP)

| Module | Raw Sockets | InMemory | Pipeline | Service | Codec | Tests | Priority |
|--------|-------------|----------|----------|---------|-------|-------|----------|
| network/ldap | ServerSocket, Socket | No | No | Stub | Yes (BER) | 19 | P3 |
| network/ftp | SSLSocket | Yes | No | 2 files | Yes (CRLF) | 27 | P3 |
| network/ssh | ServerSocket, Socket | No | No | Stub | Yes (binary) | 58 | P3 |

#### Network Protocols (Mixed UDP/TCP)

| Module | Raw NIO | InMemory | Pipeline | Service | Notes | Tests | Priority |
|--------|---------|----------|----------|---------|-------|-------|----------|
| network/dns | Socket, DatagramChannel | No | No | 2 files | UDP+TCP+DoT | 39 | P4 |
| network/syslog | SSLSocket, SSLServerSocket | No | No | 4 files | UDP+TCP+TLS | 13 | P4 |

#### Media Protocols

| Module | Raw Sockets | InMemory | Pipeline | Service | Codec | Tests | Priority |
|--------|-------------|----------|----------|---------|-------|-------|----------|
| media/rtsp | ServerSocket, Socket | No | No | No | Yes (accumulator) | 19 | P5 |
| media/sip | SocketChannel | No | No | 4 files | Yes | 17 | P5 |
| media/rtp | DatagramChannel | No | No | 4 files | UDP-specific | 9 | P5 |

#### Web Protocols

| Module | Raw Sockets | InMemory | Pipeline | Service | Tests | Priority |
|--------|-------------|----------|----------|---------|-------|----------|
| web/http | Indirect (JDK HttpClient) | Yes | No | No | 64 | P6 |
| web/http2 | None | No | No | No | 17 | P6 |
| web/http-proxy | JDK HttpClient | Yes | No | No | 17 | P6 |
| web/web-services | None | No | No | No | 10 | P6 |
| web/http3 | QUIC (UDP) | No | No | No | 24 | P6 |

#### RPC Protocols

| Module | Raw Sockets | InMemory | Pipeline | Service | Tests | Priority |
|--------|-------------|----------|----------|---------|-------|----------|
| rpc/grpc | None | No | No | No | 31 | P7 |
| rpc/graphql | WebSocket | No | No | No | 20 | P7 |

#### IoT Protocols

| Module | Raw NIO | InMemory | Pipeline | Service | Notes | Tests | Priority |
|--------|---------|----------|----------|---------|-------|-------|----------|
| iot/coap | DatagramChannel | No | No | 4 files | UDP+multicast | 21 | P8 |
| iot/upnp | DatagramChannel | No | No | 0 | UDP multicast | 37 | P8 |

### ✅ NO ACTION

| Module | Reason |
|--------|--------|
| network/terminals/* | Terminal emulators, no network I/O |
| network/modbus | No raw sockets found |
| network/snmp | No raw sockets found |
| network/cluster/* | Has Transport SPI |
| auth/* | Auth mechanisms, not network protocols |
| email/common | Shared utilities |
| media/common | Shared SDP parser |
| network/common | Shared BER/ASN.1 codec |

---

## Commands Used

```bash
# Check for raw socket usage
grep -rn "Socket\|ServerSocket\|SocketChannel\|ServerSocketChannel\|Selector" <module>/src/main --include="*.java"

# Check for Transport SPI
find <module>/src/main -name "*Transport*.java"

# Check for InMemory transport
find <module>/src/main -name "*InMemory*.java"

# Check for Pipeline transport
find <module>/src/main -name "*Pipeline*.java"

# Check for service layer
find <module>/src/main -path "*/service/*" -name "*.java"

# Test count
find <module>/src/test -name "*Test.java" | wc -l
```

---

## Migration Complexity Estimate

| Group | Modules | Est. Effort | Complexity |
|-------|---------|-------------|------------|
| Database | mysql, postgresql, redis | 3 days | Medium |
| Email | imap, smtp | 1.5 days | Low |
| Network TCP | ldap, ftp, ssh | 3 days | Medium-High (ssh is largest) |
| Network Mixed | dns, syslog | 1.5 days | Medium |
| Media | rtsp, sip, rtp | 2 days | Medium |
| Web | http, http2, http-proxy, web-services, http3 | 3 days | Medium |
| RPC | grpc, graphql | 1.5 days | Low-Medium |
| IoT | coap, upnp | 1 day | Low (UDP-specific) |
| **Total** | **~20 modules** | **~17 days** | — |
