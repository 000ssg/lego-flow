# Service Module — Code Overview

> Cross-references: [README](README.md) | [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md)
> Parent: [Project CODE_OVERVIEW](../doc/CODE_OVERVIEW.md)

---

## Module Goals

The `service` module **extends blocks into a production network service framework**. It adds:
- A richer service state machine (9 states vs blocks' 6)
- Scoped context propagation with user identity and RBAC
- Dependency-aware service lifecycle management
- NIO selector-based I/O event loops (TCP + UDP)
- Multi-selector `ServiceGroup` for high-concurrency scenarios
- TCP port-forwarding with interception (`PassThroughConnection`)
- Dual API (sync + async, procedural + functional)

---

## Source Structure

```
service/src/main/java/ssg/legoflow/service/
├── Service.java                       — extends DataProcessor<I,O> with connect/disconnect, descriptor
├── AsyncService.java                  — CompletableFuture-returning wrapper interface
├── AbstractService.java               — abstract base: state machine (9 states), listeners, stats
├── DefaultAsyncService.java           — wraps sync Service on a virtual thread executor
├── ServiceState.java                  — 9-state enum with transition table
├── ServiceContext.java                — interface: scoped hierarchy + user/role API
├── DefaultServiceContext.java         — ScopedValue-based implementation
├── ServiceDescriptor.java             — record: name, description, version, priority, dependencies
├── channel/
│   ├── DataChannel.java               — NIO TCP channel wrapper
│   ├── ChannelHandler.java            — interface for pipeline stages
│   ├── ChannelPipeline.java           — ordered chain of ChannelHandler instances
│   ├── DatagramHandler.java           — interface for UDP handlers
│   ├── DatagramPacketInfo.java        — UDP packet metadata record
│   ├── UdpDataChannel.java            — NIO UDP channel wrapper
│   ├── MulticastDataChannel.java      — multicast-aware UDP channel
│   └── MulticastConfig.java          — multicast group configuration record
├── manager/
│   ├── ServicesManager.java           — interface: start/stop/get, dependency ordering
│   ├── AbstractServicesManager.java   — dependency-graph resolution
│   ├── AsyncServicesManager.java      — parallel startup via StructuredTaskScope
│   ├── SelectableChannelManager.java  — NIO Selector TCP acceptor + event dispatcher
│   ├── UdpChannelManager.java         — NIO Selector UDP receiver
│   ├── ServiceGroup.java              — multi-selector I/O event loop (builder API)
│   ├── ServiceGroupStatistics.java    — per-selector event counters
│   ├── ConnectionThread.java          — acceptor thread abstraction
│   └── ProcessingThread.java          — I/O processing thread with ByteBuffer read loop
├── functional/
│   ├── ServiceBuilder.java            — lambda-based service construction
│   ├── ServicePipeline.java           — fluent pipeline: map/filter/forEach
│   ├── ServiceComposer.java           — fan-out composer
│   └── AsyncServicePipeline.java      — async version of pipeline
├── filter/
│   └── ContextPropagationFilter.java  — copies context attributes through filter chain
├── passthrough/
│   ├── PassThroughConnection.java     — TCP port redirector, multi-route, interception
│   ├── PassThroughConfig.java         — route configuration record
│   ├── EstablishedConnection.java     — active forwarded connection with relay threads
│   ├── ConnectionStatistics.java      — bytes transferred, connection count
│   ├── DataInterceptor.java           — interface to inspect/modify relayed data
│   ├── Direction.java                 — enum CLIENT_TO_REMOTE / REMOTE_TO_CLIENT
│   ├── BufferUtils.java               — ByteBuffer utility methods
│   └── PassThroughEvent.java          — sealed event hierarchy (Started, Stopped, ...)
└── demo/ (procedural/, functional/, combined/, udp/)
    ├── DemoServiceAll.java            — comprehensive feature demo with USE_EXTERNAL flag
    └── ...                            — individual demo classes per feature
```

---

## Key Components

### ServiceState

9-state machine that maps to `ProcessorState` while adding network-specific phases:

| ServiceState | ProcessorState mapping | Meaning |
|---|---|---|
| IDLE | IDLE | Not started |
| CONNECTING_TRANSPORT | CONNECTING | TCP/TLS handshake in progress |
| AUTHENTICATING | CONNECTING | Protocol-level auth (e.g., SASL, SCRAM) |
| READY | READY | Fully operational |
| PAUSED | PAUSED | Temporarily suspended |
| DRAINING | READY | Finishing in-flight requests before disconnect |
| DISCONNECTING | READY | Shutdown sequence initiated |
| FAILED | FAILED | Irrecoverable error |
| STOPPED | STOPPED | Terminal |

**Design decision:** DRAINING and DISCONNECTING both map to READY in ProcessorState. This is intentional: from a data-processing perspective, the service can still accept data during drain. The distinction is only relevant at the network layer.

### SelectableChannelManager + ProcessingThread

The NIO event loop:

```
SelectableChannelManager
  ├─ ConnectionThread (virtual thread per route)  — accept() loop
  │    └─ registers accepted SocketChannel with a Selector
  └─ ProcessingThread (virtual thread per Selector) — select() loop
       └─ for each readable key: read(ByteBuffer) → ChannelPipeline.fireChannelRead()
```

**Critical contract:** `ProcessingThread` passes a **single `read()` worth of data** to the pipeline. It does NOT accumulate bytes across calls. Protocol codecs in the pipeline (e.g., `Http2FrameCodec`, `RtspCodec`) must maintain their own internal `ByteBuffer` accumulator to handle TCP fragmentation.

### ServiceGroup

A multi-selector variant of `SelectableChannelManager`:

```
ServiceGroup.builder("name").dataSelectorCount(N).build()
  ├─ 1 connector selector thread  (accept)
  └─ N data selector threads      (read/write, round-robin channel assignment)
```

Statistics are tracked per-selector via `ServiceGroupStatistics`. `fireDatagram()` allows direct `DatagramHandler` dispatch for UDP use cases (e.g., `SsdpService`).

### PassThroughConnection

TCP port redirector:

```
client → [local port] → PassThroughConnection → [remote host:port] → backend
```

Features: multiple routes, `DataInterceptor` for inspection/modification, per-connection pause/resume, `SO_REUSEADDR` for clean restart (fixed in this session), virtual-thread relay (one thread per direction per connection).

**Bug fixed in this session:** Added `serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true)` before `bind()` to allow immediate restart on the same port after `stop()`. Without this, the OS TIME_WAIT state could hold the port causing `testStartStopRestart` to fail.

### Dual API pattern

```
// Sync procedural
service.consume(ctx, data);

// Async procedural (virtual thread)
CompletableFuture<Void> f = service.async().consume(ctx, data);

// Functional sync pipeline
ServicePipeline.of(service).map(transform).filter(predicate).process(ctx, data);

// Functional async pipeline
AsyncServicePipeline.of(service).process(ctx, data); // → CompletableFuture<List<O>>
```

`ServiceBuilder` allows defining a complete service with lambdas:

```java
var svc = ServiceBuilder.<String, Integer>create("parser")
    .onConvertToOutput((ctx, items) -> Arrays.stream(items).map(Integer::parseInt)...)
    .build();
```

---

## Test Coverage

**230 tests** across 29 test files. Key test areas:

| Test area | Tests |
|---|---|
| Service lifecycle (state machine, listeners) | ServiceTest |
| ServicesManager (dependency ordering, parallel start) | ServicesManagerTest |
| ServiceContext (scopes, users, roles) | ServiceContextTest, ScopeTest |
| Functional API (builder, pipeline, composer) | ServiceBuilderTest, ServicePipelineTest, ServiceComposerTest |
| Async API | AsyncServicePipelineTest (via demo tests) |
| SelectableChannelManager | SelectableChannelManagerTest |
| ServiceGroup | ServiceGroupTest, ServiceGroupStatisticsTest |
| UDP channels | UdpDataChannelTest, MulticastDataChannelTest, UdpChannelManagerTest |
| PassThrough | PassThroughConnectionTest (14 integration tests) |
| BufferUtils | BufferUtilsTest |
| Demo/functional tests | DemoServiceAllTest, EchoServiceDemoTest, CounterServiceDemoTest, etc. |

### Gaps addressed in this session

- **Bug fix:** `PassThroughConnection.start()` now sets `SO_REUSEADDR` before bind, fixing `testStartStopRestart` failure.

---

## Inconsistencies and Proposals

1. **`ServiceState.DRAINING` and `DISCONNECTING` both map to `ProcessorState.READY`** — this is correct for the data layer but may confuse callers who check `getState()` expecting a single mapping. Proposal: document the mapping explicitly in `ServiceState` Javadoc (already done in `doc/ARCHITECTURE.md`).

2. **`ServiceContext` has both `site/application/session/request` scopes and `user/roles`** — the scope hierarchy is for request routing; user/roles are for access control. These are orthogonal but live in the same interface, which is a broad API surface. Proposal: split into `ScopedContext` and `SecuredContext` sub-interfaces in a future refactoring.

3. **`AbstractServicesManager` resolves dependencies at start-time only** — dynamic service addition after startup is not supported. If a new service is added after `startAll()`, it starts independently without dependency checking. Proposal: document this limitation in `ServicesManager` Javadoc.

See also [project-level CODE_OVERVIEW.md](../doc/CODE_OVERVIEW.md) for cross-cutting inconsistencies.
