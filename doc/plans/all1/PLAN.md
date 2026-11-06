# All Protocols Compliance + Interop Plan

> **Branch:** `cleanup-protocols`
> **Reference pattern:** the DP/DF/service transport design proven in **MQTT, STOMP, AMQP, WAMP** (messaging modules, already compliant)
> **Approach:** straight, mechanical mirroring of the reference pattern — no deep redesigns.
> **Progress tracking:** [`WIP.md`](WIP.md)

---

## Goal

1. **Compliance** — every protocol module uses the DP/DF/service pipeline with a byte-level **Transport SPI**; **no direct socket usage** in `src/main`; protocol core never imports NIO.
2. **Unit testing** — each module reaches **≥80% line coverage** (target 90-95%).
3. **Functional compliance** — interop tests are part of per-protocol test suites (not separate deferred work); they verify real broker/servers; tests MUST ensure compliance, no hidden/partial decisions.
4. **Limitations reduction** — per-protocol, reduce limitations to only those hard/unreasonable to implement yet, with proper justification documented.
5. **Requirements** — update per-module requirements during migration; update common `doc/REQUIREMENTS.md` only at the end after all related modules verified; keep summary-level entries.
6. **Guidelines** — fold findings into `doc/PROTOCOL-GUIDELINES.md`.
7. **Working method** — execute in smaller steps, keep per-module facts in WIP.md, accumulate common findings across modules/phases, work continuously without stopping between phases.

## Reference Pattern (from messaging protocols)

A module is **compliant** when it has **all six**:

| # | Criterion | What it looks like | Where to check |
|---|-----------|--------------------|----------------|
| 1 | Byte-level **Transport SPI** in `src/main` | `XxxTransport { send(ByteBuffer); receive(ByteBuffer); receiveWithTimeout(buf,long,unit); close(); isOpen(); }` | `.../transport/XxxTransport.java` |
| 2 | **InMemory** transport `createPair()` | two connected byte transports, `BlockingQueue` per direction, no network | `.../transport/InMemoryXxxTransport.java` |
| 3 | **Pipeline** transport | DataChannel + ring buffer (read) + outbound queue (write); **never writes the socket from the calling thread** | `.../transport/PipelineXxxTransport.java` |
| 4 | **Service layer drives I/O** | `XxxClientService`/`XxxServerService` extend `AbstractService`; `ChannelHandler` bridges events → transport; TCP lifecycle in `SelectableChannelManager` | `.../service/` |
| 5 | **Codec stream reassembly** | frame codec accumulates bytes; handles a frame split across N reads **and** multiple frames in one read | `.../codec/` or `.../stream/` |
| 6 | **Test location + coverage** | unit + in-process in `<module>/src/test`; Docker interop only in `interop-tests`; ≥80% line coverage | both + JaCoCo |

**Golden rules** (must hold in every migration):
- **No `import java.net.Socket / ServerSocket / nio.channels.{Socket,ServerSocket}Channel / Selector / SelectionKey`** in a module's `src/main` — except the transport/adapter classes that the **service** module's `DataChannel` wraps. The *protocol core* never imports NIO.
- **Never write the socket from the calling thread** — enqueue to the outbound queue and let the selector thread's `OP_WRITE` drain it.
- **Never trust a single read** — reassemble (the STOMP `findFrameEnd` lesson).

---

## Compliance Map (reconnaissance, verified against source)

### ✅ BASELINE — Already compliant (verify, don't rebuild)

| Module | Tests | Notes |
|--------|-------|-------|
| messaging/mqtt | 355 | Full reference pattern ✅ |
| messaging/stomp | 233 | Full reference pattern ✅ |
| messaging/amqp | 640 | Full reference pattern ✅ |
| messaging/wamp | 295 | WebSocket-bound, compliant ✅ |

### 🔄 MIGRATE — Non-compliant, need full refactoring

#### Group A: TCP-based client/server protocols (straightforward migration)

| Module | Raw sockets in src/main | InMemory | Pipeline | Service | Codec reassembly | Tests | Verdict |
|--------|------------------------|----------|----------|---------|------------------|-------|---------|
| **database/mysql** | `MysqlServer` ServerSocket, `MysqlConnection` Socket | **No** | **No** | **No** | Present (packet framing) | 27 | ❌ migrate |
| **database/postgresql** | `PgServer` ServerSocket, `ClientSession` Socket | **No** | **No** | **No** | Present (4-byte len prefix) | 20 | ❌ migrate |
| **database/redis** | `RedisServer` ServerSocket, `ClientConnection` Socket | Partial | **No** | Stub | Present (RESP parser) | 21 | ❌ migrate |
| **email/imap** | `ImapServer` ServerSocket, `ImapSession` Socket | **No** | **No** | Stub | Present (line-oriented) | 27 | ❌ migrate |
| **email/smtp** | `SmtpServer` ServerSocket, `SmtpSession` Socket | **No** | **No** | Stub | Present (line-oriented) | 28 | ❌ migrate |
| **network/ldap** | `LdapServer` ServerSocket, Socket | **No** | **No** | Stub | Present (BER TLV) | 19 | ❌ migrate |
| **network/ssh** | `SshServer` ServerSocket, `SshTransport` wraps Socket | **No** | **No** | Stub | Present (binary packet) | 58 | ❌ migrate |
| **network/ftp** | `FtpsHandler` SSLSocket | InMemory ✓ | **No** | 2 files ✓ | Present (CRLF) | 27 | ❌ migrate |
| **media/rtsp** | `RtspServer` ServerSocket, Socket | **No** | **No** | **No** | Present (accumulator) | 19 | ❌ migrate |

#### Group B: UDP-based or mixed protocols (adapted migration)

| Module | Raw NIO | InMemory | Pipeline | Service | Notes | Tests | Verdict |
|--------|---------|----------|----------|---------|-------|-------|---------|
| **network/dns** | `TcpTransport` Socket, `DotTransport` SSLSocket, `UdpTransport` DatagramChannel | **No** | **No** | 2 files | UDP+TCP+DoT; UDP part is different paradigm | 39 | ❌ migrate (TCP/DoT parts) |
| **network/syslog** | `TlsSender` SSLSocket, `TlsCollector` SSLServerSocket | **No** | **No** | 4 files | UDP+TCP+TLS; TLS parts need migration | 13 | ❌ migrate (TCP/TLS parts) |
| **media/sip** | `TcpSipTransport` SocketChannel/ServerSocketChannel | **No** | **No** | 4 files | Has SipTransport SPI but TCP impl uses raw NIO | 17 | ❌ migrate (TCP transport) |
| **media/rtp** | `RtpTransport` DatagramChannel | **No** | **No** | 4 files | Pure UDP - different paradigm, adapt | 9 | ⚠️ partial (UDP-specific) |
| **iot/coap** | `CoapServer` DatagramChannel | **No** | **No** | 4 files | Pure UDP + multicast | 21 | ⚠️ partial (UDP-specific) |
| **iot/upnp** | `SsdpService` DatagramChannel (multicast) | **No** | **No** | 0 | Pure UDP multicast | 37 | ⚠️ partial (UDP-specific) |

#### Group C: HTTP/Web protocols (special considerations)

| Module | Raw sockets | InMemory | Pipeline | Service | Notes | Tests | Verdict |
|--------|-------------|----------|----------|---------|-------|-------|---------|
| **web/http** | Indirect via java.net.http | InMemory ✓ | **No** | **No** | Uses JDK HttpClient; WebSocket uses session pattern | 64 | ❌ migrate (add Pipeline + service) |
| **web/http2** | None direct | **No** | **No** | **No** | HTTP/2 over HTTP/1.1 upgrade or h2c | 17 | ❌ migrate |
| **web/http3** | QUIC via QuicConnection (UDP) | **No** | **No** | **No** | QUIC/UDP paradigm; self-contained | 24 | ⚠️ partial (UDP-specific) |
| **web/http-proxy** | java.net.http.HttpClient | InMemory ✓ | **No** | **No** | Proxy using JDK HttpClient | 17 | ❌ migrate |
| **web/web-services** | None | **No** | **No** | **No** | Service layer only | 10 | ❌ migrate (add transport) |

#### Group D: RPC protocols

| Module | Raw sockets | InMemory | Pipeline | Service | Notes | Tests | Verdict |
|--------|-------------|----------|----------|---------|-------|-------|---------|
| **rpc/grpc** | None (InetSocketAddress only) | **No** | **No** | **No** | Has loopback transport for testing | 31 | ❌ migrate |
| **rpc/graphql** | java.net.http.WebSocket | **No** | **No** | **No** | WebSocket + HTTP transports | 20 | ❌ migrate |

### ✅ NO ACTION — Not network protocols

| Module | Reason |
|--------|--------|
| network/terminals/* | Terminal emulators (vt100, xterm, etc.) - no network I/O |
| network/modbus | No raw sockets found - check if already compliant |
| network/snmp | No raw sockets found - check if already compliant |
| network/cluster/* | Clustering - has Transport SPI in core |
| auth/* | Authentication mechanisms, not network protocols |
| email/common | Shared utilities |
| media/common | Shared SDP parser |
| network/common | Shared BER/ASN.1 codec |

---

## Migration Groups (ordered by complexity)

### Phase 1 — Foundation + Audit (this commit)
- Create plan docs
- Verify baseline modules
- Audit all non-compliant modules in detail
- Commit

### Phase 2 — Database Protocols (mysql, postgresql, redis)
- All three are TCP-based with similar patterns
- Migrate in parallel (3 concurrent subagents)
- Each: Transport SPI → InMemory → Pipeline → Service layer
- Target: ≥80% coverage, prefer 90-95%

### Phase 3 — Email Protocols (imap, smtp)
- Similar line-oriented protocols
- Migrate in parallel (2 concurrent subagents)
- Target: ≥80% coverage

### Phase 4 — Network Protocols Part 1 (ldap, ssh, ftp)
- TCP-based with varying complexity
- SSH is largest (58 tests, complex crypto)
- Migrate sequentially: ldap → ftp → ssh (increasing complexity)
- Target: ≥80% coverage

### Phase 5 — Network Protocols Part 2 (dns, syslog)
- Mixed UDP/TCP protocols
- DNS: migrate TCP/DoT parts, leave UDP as-is
- Syslog: migrate TCP/TLS parts, leave UDP as-is
- Target: ≥80% coverage

### Phase 6 — Media Protocols (rtsp, sip, rtp)
- RTSP: TCP-based, straightforward
- SIP: has Transport SPI but TCP impl needs rewrite
- RTP: UDP-specific, adapt pattern
- Target: ≥80% coverage

### Phase 7 — Web Protocols (http, http2, http-proxy, web-services)
- HTTP/1.1: add Pipeline transport + service layer
- HTTP/2: full migration
- HTTP-proxy: adapt to service pattern
- Web-services: add transport abstractions
- HTTP/3: UDP-specific, adapt pattern
- Target: ≥80% coverage

### Phase 8 — RPC Protocols (grpc, graphql)
- gRPC: add Transport SPI + service layer
- GraphQL: add Transport SPI + service layer
- Target: ≥80% coverage

### Phase 9 — IoT Protocols (coap, upnp)
- UDP-specific protocols
- Adapt InMemory pattern for UDP
- Target: ≥80% coverage

### Phase 10 — Final Docs
- Update PROTOCOL-GUIDELINES.md
- Update common doc/REQUIREMENTS.md (summary-level, after all modules verified)
- Final coverage verification
- Commit

---

## Per-Module Fix Checklist

Each module migration includes:

### Source Code Changes
- [ ] Create `XxxTransport` SPI interface in `src/main/.../transport/`
- [ ] Create `InMemoryXxxTransport` with `createPair()`
- [ ] Create `PipelineXxxTransport` (DataChannel + ring + outbound queue)
- [ ] Refactor server off raw `ServerSocket` → `handleConnection(XxxTransport)` seam
- [ ] Refactor client off raw `Socket` → transport-injected
- [ ] Create `XxxClientService` / `XxxServerService` extending `AbstractService`
- [ ] Create `XxxChannelHandler` bridging pipeline events → transport
- [ ] Ensure codec stream reassembly (accumulator pattern)
- [ ] Remove all raw socket/NIO imports from protocol core

### Test Changes
- [ ] Add transport SPI tests
- [ ] Add InMemory pair connectivity tests
- [ ] Migrate existing loopback tests to InMemory seam
- [ ] Add service integration test (TCP client ↔ server via service layer)
- [ ] Add interop test (real broker/server via Docker) — **part of per-protocol test suite**
- [ ] Verify ≥80% line coverage (target 90-95%)

### Demo Changes
- [ ] Update demos to use InMemory transport for unit tests
- [ ] Update demos to use service layer for integration tests
- [ ] Verify demos compile and run

### Documentation Changes
- [ ] Update module `doc/REQUIREMENTS.md` (append-only, per-module)
- [ ] Update `AGENTS.md` with migration notes
- [ ] Update `README.md` if API changes
- [ ] Update `doc/ARCHITECTURE.md` with new architecture diagram
- [ ] Update `doc/COMPLIANCE.md` with compliance matrix
- [ ] Document any remaining limitations with justification

### Common Requirements (end of group)
- [ ] Update common `doc/REQUIREMENTS.md` (summary-level, after all related modules verified)

---

## Lessons from Messaging Protocol Migration

### Problems Encountered & Solutions

1. **Timeout vs EOF confusion** — `receiveWithTimeout()` must distinguish between "no data yet" and "connection closed". Solution: throw `EOFException` on close, return null on timeout.

2. **Service integration test timing** — `CONNECTION_OPENED` event fires during handshake; listener must be set before `client.connect()`. Solution: set listener first, then connect.

3. **Platform thread for server** — CI carrier thread starvation. Solution: run server on `Thread.ofPlatform().start()` for in-process tests.

4. **ByteBuffer clearing** — handlers reuse buffers. Solution: always call `clear()` after reading.

5. **Codec reassembly** — frames split across N reads OR multiple frames in one read. Solution: accumulator pattern with `combineWithAccumulator()` + `decodeAll()`.

6. **Never write from calling thread** — enqueue to outbound queue, let selector thread drain on `OP_WRITE`.

7. **InMemory pair for testing** — `createPair()` returns two connected transports; drives all unit + in-process integration tests.

### Verification Gates (run before each phase commit)

- `mvn compile -DskipTests -pl '!benchmarks'` — compiles
- `./gradlew test` (or targeted `mvn test -pl <module>`) — unit tests green
- JaCoCo `-P jacoco-coverage` — migrated module ≥80% line coverage
- (Where applicable) interop tests run green against Docker brokers

---

## Interop Integration

Interop tests are **part of per-protocol test suites** — they ensure functional compliance against real brokers/servers.

| Group | Tag | Contents | Reference Broker | Status |
|-------|-----|----------|------------------|--------|
| 1 | `interop-messaging-core` | mqtt, stomp, amqp | mosquitto, rabbitmq(STOMP), artemis | ✅ active |
| 2 | `interop-kafka` | kafka | cp-kafka 7.6.1 KRaft | ✅ service added |
| 3 | `interop-wamp` | wamp | crossbar | ✅ service added |
| 4 | `interop-database` | mysql, postgresql, redis | mysql, postgres, redis | **during Phase 2** |
| 5 | `interop-email` | imap, smtp | docker containers | **during Phase 3** |
| 6 | `interop-network-tcp` | ldap, ssh, ftp | docker containers | **during Phase 4** |
| 7 | `interop-network-mixed` | dns, syslog | docker containers | **during Phase 5** |
| 8 | `interop-media` | rtsp, sip | docker containers | **during Phase 6** |
| 9 | `interop-web` | http, http2 | nginx, reference servers | **during Phase 7** |
| 10 | `interop-rpc` | grpc, graphql | reference servers | **during Phase 8** |
| 11 | `interop-iot` | coap, upnp | docker containers | **during Phase 9** |

Each interop test is tagged with its group and runs as part of the protocol's test suite.

---

## Working Notes

- **Straight approach:** mirror the STOMP/MQTT/AMQP files (`PipelineTransport`, `InMemoryTransport`, `XxxClientService`/`XxxServerService`, `XxxChannelHandler`) rather than inventing new structure
- **Ordering:** smallest migrations first to shake out the pattern, then larger ones
- **UDP protocols:** adapt the pattern — InMemory for testing, but Pipeline transport is TCP-specific
- **No hidden/partial decisions:** functional compliance is top priority; tests MUST ensure compliance
- **Limitations:** only keep limitations that are hard/unreasonable to implement; document justification per-protocol
- **Requirements:** update per-module `doc/REQUIREMENTS.md` during migration; update common `doc/REQUIREMENTS.md` only at end after all related modules verified
- **Working method:** smaller steps, keep per-module facts in WIP.md, accumulate common findings, work continuously
- **Never** run `git push`; commit locally only
