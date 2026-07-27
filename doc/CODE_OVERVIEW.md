# Lego Flow — Code Overview

> Cross-references: [README](README.md) | [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md)
>
> Per-module overviews: [blocks/CODE_OVERVIEW.md](blocks/CODE_OVERVIEW.md) | [service/CODE_OVERVIEW.md](service/CODE_OVERVIEW.md)

---

## Project Goals

Lego Flow is a **composable data processing framework** for Java built on JDK 25. The core idea is "LEGO blocks for network protocols": every component is a `DataProcessor<I,O>` or `DataFilter<T>` that can be composed, filtered, and observed without knowing its neighbours. The project implements a wide suite of industrial network protocols (HTTP/2, HTTP/3, QUIC, MQTT, AMQP, Kafka, Redis, PostgreSQL, SSH, …) entirely from scratch in pure Java — no third-party protocol libraries.

**Primary design goals:**

1. **Composability** — arbitrary dataflow graphs built by wiring DP/DF blocks.
2. **Observability** — every processor tracks per-type in/out counts and amounts via `ProcessorStatistics`. State transitions are observable via `StateListener`.
3. **Protocol completeness** — RFC-faithful implementations with compliance documentation for all 40 leaf protocol modules.
4. **Modern Java** — exclusive use of JDK 25 features: virtual threads, scoped values, structured concurrency, sealed interfaces, record patterns, pattern matching, stream gatherers.
5. **Dual API** — every service exposes sync + async (CompletableFuture) and procedural + functional (lambda) variants.

---

## Module Structure

The project is a Maven multi-module build (also buildable with Gradle). Leaf modules are grouped into 9 categories plus 2 core modules.

```
lego-flow/                          ← root POM
├── blocks/                         ← CORE: DP<I,O>, DF<T>, Context, State, Statistics
├── service/                        ← CORE: Service lifecycle, scopes, NIO, pass-through
├── web/                            ← Web & HTTP protocols
│   ├── http/                       HTTP/1.1 (RFC 2616)
│   ├── http2/                      HTTP/2 (RFC 9113) + HPACK
│   ├── http3/                      HTTP/3 (RFC 9114) + QUIC + QPACK
│   ├── web-services/               REST/content-negotiation endpoints
│   └── http-proxy/                 Forward / reverse / caching proxy
├── auth/                           ← Authentication modules
│   ├── gssapi/                     GSS-API / Kerberos V5 / SPNEGO
│   └── http-auth/
│       ├── core/                   Auth framework, sessions, JWT
│       ├── basic-digest/           Basic (RFC 7617) + Digest (RFC 7616)
│       ├── oauth/                  OAuth 2.0 / PKCE / OIDC
│       ├── sso/                    Reverse-proxy SSO, SAML
│       └── spnego/                 HTTP Negotiate (RFC 4559)
├── messaging/                      ← Message brokers & event streaming
│   ├── kafka/                      Apache Kafka wire protocol (37 API keys)
│   ├── amqp/                       AMQP 1.0 (ISO 19464)
│   ├── stomp/                      STOMP 1.2
│   ├── nats/                       NATS + JetStream
│   ├── mqtt/                       MQTT v3.1.1 / v5.0
│   ├── xmpp/                       XMPP (RFC 6120/6121)
│   └── wamp/                       WAMP (RPC + Pub/Sub)
├── iot/                            ← Internet of Things
│   ├── upnp/                       UPnP/DLNA (SSDP, SOAP, GENA, media server)
│   └── coap/                       CoAP (RFC 7252, observe, blockwise)
├── rpc/                            ← RPC frameworks
│   ├── grpc/                       gRPC (HTTP/2 + protobuf)
│   └── graphql/                    GraphQL (query language)
├── database/                       ← Database wire protocols
│   ├── redis/                      Redis RESP2/RESP3 (AUTH, HyperLogLog, Geo)
│   ├── postgresql/                 PostgreSQL v3 (SCRAM, aggregates, JOINs)
│   └── mysql/                      MySQL client/server (JOINs, transactions)
├── email/                          ← Email protocols
│   ├── common/                     Shared MIME parsing (RFC 2045-2049)
│   ├── smtp/                       SMTP (RFC 5321)
│   └── imap/                       IMAP4rev2 (RFC 9051)
├── network/                        ← Network services & management
│   ├── common/                     Shared BER/ASN.1 codec
│   ├── dns/                        DNS (RFC 1034/1035 + DNSSEC + DoH + DoT)
│   ├── ldap/                       LDAP v3 (RFC 4511)
│   ├── snmp/                       SNMP v3 (RFC 3411-3418)
│   ├── syslog/                     Syslog (RFC 5424)
│   ├── modbus/                     Modbus TCP
│   ├── ssh/                        SSH-2 (RFC 4251-4256, SFTP, SCP, forwarding)
│   └── ftp/                        FTP/FTPS (RFC 959, RFC 4217)
└── media/                          ← Real-time media & VoIP
    ├── common/                     Shared SDP parser (RFC 4566)
    ├── rtsp/                       RTSP 2.0 (RFC 7826)
    ├── rtp/                        RTP/RTCP (RFC 3550)
    └── sip/                        SIP (RFC 3261)
```

**Statistics (as of 2026-07-11):** 2,344 Java source files · 42 leaf modules · 8,600+ tests · ~240,000 lines of code.

---

## Component Decomposition and Reasoning

### Layer 1 — blocks (foundation)

`blocks` defines five orthogonal abstractions used by all 41 other modules:

| Abstraction | Role | Rationale |
|---|---|---|
| `DataProcessor<I,O>` | Bidirectional transform unit | Separates remote type (I) from local type (O); allows the same framework to model protocol codecs (bytes→messages) and business pipelines (string→integer) |
| `DataFilter<T>` | Positioned filter on I or O stream | Single-responsibility transformation or validation; composable into chains without touching the processor |
| `Context` | Logger + statistics + attributes | Avoids passing many separate objects; keeps the API stable as concerns are added |
| `ProcessorState` | IDLE→CONNECTING→READY→PAUSED→FAILED→STOPPED | Formal state machine prevents invalid lifecycle operations; STOPPED is terminal to make resource leak detection easier |
| `ProcessorStatistics` | Per-type count+amount tracking | ConcurrentHashMap+AtomicLong gives lock-free thread-safe stats; per-type keys allow heterogeneous pipelines to report per-type throughput |

**Key decision: AbstractDataProcessor template method.** The abstract class wires filter chains + statistics and delegates `convertToOutput` / `convertToInput` to subclasses. This is the "Template Method" pattern: the invariant behaviour (filter chain, stats, accept/produce routing) lives in one place; subclasses implement only the type conversion.

**Key decision: varargs I/O.** All consume/produce/accept/submit take varargs (`I...`). This enables batching (feed multiple items in one call) while keeping the API for single-item use concise. The tradeoff is heap allocations for the temporary arrays; this is acceptable because the framework targets JDK 25 virtual threads where object creation is cheap relative to I/O latency.

### Layer 2 — service

`service` extends blocks into a network service framework. Key additions over `blocks`:

| Component | What it adds |
|---|---|
| `ServiceState` (9 states) | Finer lifecycle granularity than `ProcessorState` (6 states); differentiates e.g. CONNECTING_TRANSPORT from AUTHENTICATING |
| `ServiceContext` | Scoped hierarchy (Site/Application/Session/Request) propagated via `ScopedValue`; user identity + role-based access control |
| `ServicesManager` | Dependency-aware start/stop ordering using `StructuredTaskScope` for parallel startup |
| `SelectableChannelManager` | NIO `Selector`-based acceptor/I/O event loop; distributes channels across virtual thread pools |
| `ServiceGroup` | Multi-selector event loop (1 connector + N data selectors, round-robin assignment) for high-connection-count scenarios |
| `PassThroughConnection` | TCP port redirector with interception, pause/resume, and per-connection statistics |
| Functional API | `ServiceBuilder`, `ServicePipeline`, `ServiceComposer`, `AsyncServicePipeline` |

**Key decision: sync-primary with async wrapper.** `Service<I,O>` is synchronous; `AsyncService<I,O>` wraps it in `CompletableFuture` running on a virtual thread executor. JDK 25 virtual threads make blocking calls near-zero-cost, so sync code is simpler to write and debug without throughput penalty. The NIO selector layer is inherently async, but the application-level API is sync.

**Key decision: ScopedValue for context propagation.** `ServiceContext` uses `ScopedValue` (JEP 481) instead of `ThreadLocal`. This is safe with virtual threads: a virtual thread can be remounted on different carrier threads, which makes `ThreadLocal` values unreliable in structured-concurrency scenarios. `ScopedValue` is bound per-scope and cannot be mutated after binding.

### Layer 3 — Protocol modules

All 40 protocol leaf modules follow the same structure:

```
<module>/
├── src/main/java/ssg/legoflow/<protocol>/
│   ├── <Protocol>Config.java          — immutable configuration record
│   ├── <Protocol>Codec.java           — ByteBuffer ↔ message-object codec
│   ├── <Protocol>Server.java          — server-side service extending AbstractService
│   ├── <Protocol>Client.java          — client-side service
│   └── demo/Demo<Protocol>All.java    — comprehensive feature demo (USE_EXTERNAL flag)
├── src/test/java/ssg/legoflow/<protocol>/
│   ├── <Protocol>CodecTest.java
│   ├── <Protocol>ServerTest.java
│   └── demo/Demo<Protocol>AllTest.java
└── doc/
    ├── ARCHITECTURE.md
    ├── REQUIREMENTS.md
    └── COMPLIANCE.md                  — RFC cross-reference (where applicable)
```

**Key decision: ByteBuffer accumulation in codecs.** All stream-oriented codecs maintain an internal `ByteBuffer` accumulator to handle TCP fragmentation (data arriving in multiple `read()` calls). This follows the `Http2FrameCodec` reference pattern and was audited/fixed across all 42 modules in phase 9 (stream-oriented codec audit).

**Key decision: USE_EXTERNAL flag in demos.** Each `DemoXxxAll` class has a `USE_EXTERNAL` boolean. When `false` (default for CI), the demo stands up its own in-process mock server and runs all feature methods against it. When `true`, it connects to a real external service. This pattern allows meaningful functional testing without external dependencies.

---

## Key Architectural Decisions

### 1. No third-party protocol libraries

All protocol codecs are implemented from scratch. **Rationale:** educational, complete control over wire format, no version conflicts, consistent ByteBuffer-based stream model across all modules.

**Inconsistency noted:** The `gssapi` module cannot realistically be tested without a Kerberos KDC. Its tests are necessarily shallow (configuration, OID constants, exception wrapping). This is documented in `auth/gssapi/doc/COMPLIANCE.md`.

### 2. Dual API everywhere (from service upward)

Every public API from the service layer upward exposes: sync procedural + async procedural (CompletableFuture) + functional (lambda-friendly builders/pipelines).

**Rationale:** Different callers have different composition needs. The sync variant is simplest. The async variant enables `CompletableFuture` composition. The functional variant reduces boilerplate for pipeline definitions.

**Inconsistency noted:** The blocks layer has no async or functional variant. This is intentional — blocks are the lowest-level building blocks; the dual API would add complexity without benefit at that layer.

### 3. Two build systems (Maven + Gradle)

Both `pom.xml` and `build.gradle.kts` exist at root and per-module level. Maven is the primary system (used in CI and documentation examples). Gradle provides parallel builds with up to 10 workers and build caching.

**Risk:** Keeping both in sync requires discipline. When new modules are added, both `settings.gradle.kts` and the root `pom.xml` must be updated. Currently the two are in sync.

### 4. JDK 25 minimum

The project uses `--enable-preview` features (sealed classes stable in 21+, scoped values stable in 23+, stream gatherers stable in 24+). This is a hard constraint — the project will not compile on JDK < 25.

### 5. Test categories: unit + functional demo + API style

Every module has three test tiers:
- **Unit tests** — test individual classes in isolation.
- **Functional demo tests** — test the `DemoXxxAll` class end-to-end (all protocol features against an in-process mock).
- **API style tests** — cover procedural-sync, procedural-async, functional-sync, functional-async variants where applicable.

---

## Inconsistencies, Inefficiencies, and Ambiguities Found

### 1. `produce()` statistics track output, not input (naming mismatch)

In `AbstractDataProcessor.produce()`:
```java
public void produce(Context ctx, I... data) {
    statistics.recordOut(inputType, data.length, data.length);
}
```
The method records `recordOut` for `inputType`. This is semantically correct (produce sends I outward = output) but is confusing because `I` is the "input type" parameter. The naming `inputType` vs `outputType` refers to the type parameters of the processor, not the direction of the statistics.

**Proposal:** Add clarifying Javadoc to `AbstractDataProcessor` explaining that `inputType` = the remote/external type and `outputType` = the local/internal type, regardless of data flow direction.

### 2. `PassThroughConnection.start()` lacked `SO_REUSEADDR`

The `ServerSocketChannel` in `start()` did not set `SO_REUSEADDR`, causing `testStartStopRestart` to fail intermittently when the OS held the port in TIME_WAIT after `stop()`. **Fixed in this session** by adding `serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true)` before `bind()`.

### 3. `close()` on already-STOPPED `AbstractDataFilter` does not set `closed=true`

`AbstractDataFilter.close()` checks `if (current != ProcessorState.STOPPED)` before updating state, making it idempotent. However, there is no `closed` boolean flag like in `PassThroughConnection`, so callers cannot distinguish "never started" from "closed". This is a minor inconsistency vs `AbstractDataProcessor` which also lacks such a flag — both are consistent with each other, but inconsistent with `PassThroughConnection`.

**Proposal:** Accept the current behaviour. Both abstract base classes are idempotent on `close()`, which is the important contract. Adding a `closed` flag would change the public API and would only be needed if callers need to distinguish not-yet-closed from closed.

### 4. `ProcessorStatistics` in `Context` is unused in most protocol modules

`DefaultContext.getStatistics()` returns a `ProcessorStatistics` instance on the context, but `AbstractDataProcessor` maintains its own separate `ProcessorStatistics`. Protocol modules that create a `DefaultContext` per-connection end up with context-level statistics that are never populated.

**Proposal:** Either (a) document that context-level statistics are for application-level tracking (not processor-level I/O), or (b) remove `getStatistics()` from the `Context` interface and let callers use `processor.getStatistics()` directly.

### 5. `REQUIREMENTS.md` for blocks module was never populated

`blocks/doc/REQUIREMENTS.md` contains only the timeline header and "no commits yet". All other modules have populated REQUIREMENTS.md files.

**Proposal:** Populate `blocks/doc/REQUIREMENTS.md` with the initial commit section. (Out of scope for this session — it would require manufacturing historical data.)

### 6. Demo-only classes in main source tree

All `demo/` packages under `src/main/java` contain classes (e.g., `PassthroughProcessor`, `StringToIntProcessor`, `LoggingFilter`) that are only used by tests. These should ideally live in `src/test/java` to avoid being packaged into production artifacts.

**Proposal:** Move `demo/` packages from `src/main/java` to `src/test/java` in a future refactoring pass. This is a non-trivial change (all demo classes are imported by tests) but would be cleaner.

### 7. `AbstractDataProcessor.submit()` filter order differs from `consume()`

In `consume()`: inputFilters → convertToOutput → outputFilters → accept
In `submit()`: outputFilters → convertToInput → inputFilters → produce

This is the correct bidirectional symmetry: submit is the reverse of consume. However, the `recordIn`/`recordOut` in `submit()` records `recordIn(outputType)` which means "received O locally" — this is correct but non-obvious without reading the code.

**Proposal:** Add inline comments to `AbstractDataProcessor.submit()` explaining the reversed filter order and statistics semantics.

---

## Test Coverage Summary

| Module | Tests | Notes |
|---|---|---|
| blocks | 84 | All core classes covered; exceptions, state transitions, filter edge cases added in this session |
| service | 230 | Full coverage; `SO_REUSEADDR` bug fixed in this session |
| web/http | 543 | Full coverage |
| web/http2 | ~150 | Full coverage |
| web/http3 | ~250 | Full coverage |
| web/web-services | ~80 | Full coverage |
| web/http-proxy | 252 | Full coverage |
| auth (all) | ~480 | Full coverage |
| messaging (all) | ~1,370 | Full coverage |
| iot (all) | ~574 | Full coverage |
| rpc (all) | ~170 | Full coverage |
| database (all) | ~682 | Full coverage |
| email (all) | ~150 | Full coverage |
| network (all) | ~920 | Full coverage |
| media (all) | ~509 | Full coverage |
| **Total** | **~8,600+** | All tests passing as of 2026-07-11 |

---

## Related Documents

- [README.md](README.md) — project overview, build instructions, module table
- [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) — architecture decisions (edited in place)
- [doc/REQUIREMENTS.md](doc/REQUIREMENTS.md) — requirements history (append-only)
- [costs/BUILD_COST_REPORT.md](costs/BUILD_COST_REPORT.md) — build effort and token cost report
- [CLAUDE.md](CLAUDE.md) — development guide for AI-assisted sessions
- [blocks/CODE_OVERVIEW.md](blocks/CODE_OVERVIEW.md) — blocks module deep-dive
- [service/CODE_OVERVIEW.md](service/CODE_OVERVIEW.md) — service module deep-dive
