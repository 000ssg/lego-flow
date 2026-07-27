# Service Module — Requirements Evolution

## Timeline

| Commit | Feature | Tests |
|--------|---------|-------|
| Initial | Service framework: lifecycle, scopes, access control, dual APIs | 46 |
| NIO/Channel | NIO channel abstraction, virtual-thread event loop, context propagation filter | 138 |
| Demo Expansion | Remaining demo sources: AsyncEchoService, AsyncPipelineDemo, ChatService, DependentServicesDemo | 138 |
| Stream Contract | ByteBuffer stream contract documentation & flaky test fix | 194 |
| ServiceGroup | Multi-selector I/O event loop, ServiceGroupStatistics, deferred channel registration | ~228 |
| ServiceGroup Test Fixes + UPnP Integration | UDP dispatch fix, ChannelPipeline.fireDatagram(), SsdpService ServiceGroup integration | 230 |

---

## Commit: Initial — Service Framework (2026-06-16)

### Original Request
> "Create service module with Service/AsyncService interfaces, ServiceContext with scoped sub-elements (Site/Application/Session/Request), user types (Anonymous/Shared/Exact), role-based access control, ServicesManager with dependency/priority-based lifecycle. Expose dual APIs: sync + async (CompletableFuture wrapper on virtual threads), procedural + functional (ServicePipeline, ServiceBuilder, ServiceComposer). Include demos for procedural, functional, and combined styles with comprehensive tests."

### Reformulated Requirements
1. Service<I,O> extends DataProcessor with connect/disconnect lifecycle, descriptor (name, description, priority, dependencies), isConnected state
2. AsyncService<I,O> wraps sync Service with CompletableFuture returns using virtual thread executor
3. ServiceContext extends Context with 4-level scope hierarchy (Site, Application, Session, Request), user identity, role-based permission checking
4. ServiceUser with 3 types: ANONYMOUS (guest role), SHARED (user role), EXACT (custom roles)
5. AccessControl with operation-to-role mapping and permission checking
6. ServicesManager managing multiple services with dependency-aware start (starts deps first) and stop (stops dependents first), priority-sorted parallel startup
7. Functional API: ServicePipeline (map/filter/collect), ServiceBuilder (lambda-based construction), AsyncServicePipeline, ServiceComposer
8. Demo implementations: EchoService, CounterService, AuthenticatedService (procedural), PipelineDemo, LambdaServiceDemo (functional)
9. Comprehensive test coverage for all components

### Final Design Decisions
- Async wrapper uses `Executors.newVirtualThreadPerTaskExecutor()` for all async operations
- ServiceBuilder uses BiFunction/BiConsumer lambdas for conversion and lifecycle hooks
- ServiceComposer manages ordered chains with connectAll/disconnectAll
- Scopes use UUID-based IDs by default, ConcurrentHashMap for thread-safe attributes
- AccessControl uses ConcurrentHashMap<String, Set<ServiceRole>> for operation-role mapping
- AbstractServicesManager sorts by priority for startAll, reverses for stopAll

### Implementation Details
- 20 source files (interfaces, implementations, functional API, demos)
- Packages: service, service.scope, service.user, service.manager, service.functional, service.demo.procedural, service.demo.functional

### Test Coverage
- Unit tests: ServiceTest(8), AsyncServiceTest(7), ServiceContextTest(8), ScopeTest(8), AccessControlTest(8), ServicesManagerTest(10)
- Functional API tests: ServicePipelineTest(6), ServiceBuilderTest(6), ServiceComposerTest(5)
- Demo tests: EchoServiceDemoTest(4), AsyncEchoDemoTest(3), CounterServiceDemoTest(4), AccessControlDemoTest(5)
- Functional demo tests: PipelineDemoTest(4), LambdaServiceDemoTest(4), AsyncPipelineDemoTest(4), ComposerDemoTest(3)
- Combined demo tests: ScopePropagationDemoTest(6), MultiServiceManagerDemoTest(5), ServiceLifecycleDemoTest(4)
- **Total: 46 service tests (112 total with blocks)**

---

## Commit: NIO/Channel — NIO Channel Abstraction & Virtual Thread Event Loop (2026-06-16)

### Original Request
> "Add ServiceState enum with extended states (CONNECTING_TRANSPORT, AUTHENTICATING, DRAINING, DISCONNECTING) mapping to ProcessorState. Add channel package: DataChannel NIO interface (read/write ByteBuffer, isOpen, close, getSelectionKey), ChannelHandler I/O event handler (onRead, onWrite, onConnect, onDisconnect, onError), ChannelPipeline ordered handler chain with fire* event propagation. Add manager package additions: SelectableChannelManager (NIO Selector + virtual thread event loop, extends AbstractServicesManager), ConnectionThread (virtual thread wrapper for connection establishment), ProcessingThread (virtual thread wrapper for I/O event dispatching), AsyncServicesManager (CompletableFuture wrapper for ServicesManager on virtual threads). Add filter/ContextPropagationFilter propagating ServiceContext scopes. Add demo/combined/ChannelManagerDemo with InMemoryDataChannel and RecordingHandler."

### Reformulated Requirements
1. `ServiceState` enum — 9 states (IDLE, CONNECTING_TRANSPORT, AUTHENTICATING, READY, PAUSED, DRAINING, DISCONNECTING, FAILED, STOPPED) each mapping to a `ProcessorState`; enforces valid transitions via `canTransitionTo()`
2. `DataChannel` — NIO-oriented interface extending `AutoCloseable`; `read(ByteBuffer)`, `write(ByteBuffer)`, `isOpen()`, `close()`, `getSelectionKey()`
3. `ChannelHandler` — I/O event callback interface: `onRead`, `onWrite`, `onConnect`, `onDisconnect`, `onError(Throwable)`
4. `ChannelPipeline` — ordered handler chain backed by `CopyOnWriteArrayList`; `addFirst/addLast/remove`; `fire*` methods propagate events sequentially, stop on exception and invoke `fireError`; `fireRead` passes a read-only buffer copy to each handler
5. `SelectableChannelManager` — extends `AbstractServicesManager`; owns a NIO `Selector`; two virtual-thread pools (connection + processing); `registerChannel/unregisterChannel` per service; `startEventLoop/stopEventLoop`; `dispatchKey` routes readable/writable/connectable events to `ProcessingThread` on processing pool; `close()` drains channels, pools, and selector; configurable `bufferSize` and `selectTimeoutMs`
6. `ConnectionThread` — wraps `service.connect(ctx)` on a named virtual thread; returns `CompletableFuture<Void>`; `cancel()` interrupts the thread
7. `ProcessingThread` — dispatches individual NIO readiness events (`processReadable`, `processWritable`, `processConnectable`) each on its own virtual thread; fires corresponding `ChannelPipeline` events; owns a reusable `ByteBuffer`
8. `AsyncServicesManager` — `CompletableFuture` wrapper for `ServicesManager`; delegates every operation (`register`, `unregister`, `startAll`, `stopAll`, `pauseAll`, `resumeAll`, `start`, `stop`, `getService`, `getServices`, `getStates`) to sync delegate on virtual-thread executor; `sync()` returns delegate; implements `AutoCloseable`
9. `ContextPropagationFilter<T>` — extends `AbstractDataFilter`; on `doFilter` stores source `ServiceContext` as attribute `"service.propagated.context"` on target context; if target is a `ServiceContext`, copies Request-scope and Session-scope attributes from source; static `getPropagatedContext(Context)` extractor
10. `ChannelManagerDemo` — combined demo with `InMemoryDataChannel` (in-memory ByteBuffer channel) and `RecordingHandler` (records events for assertion); exercises full connect → read → write → disconnect → error pipeline

### Final Design Decisions
- `ServiceState` uses a `Map<ServiceState, Set<ServiceState>>` constant for O(1) transition checks; terminal `STOPPED` has an empty transition set
- `DRAINING` and `DISCONNECTING` both map to `ProcessorState.READY` to remain compatible with existing processor state consumers while conveying richer service semantics
- `ChannelPipeline` uses `CopyOnWriteArrayList` so handlers can be added/removed concurrently without blocking the event loop iteration
- `fireRead` passes `data.asReadOnlyBuffer()` to each handler — protects the shared read buffer position from mutation by individual handlers
- `SelectableChannelManager` uses two separate virtual-thread pools: `connectionPool` for connection establishment (potentially blocking), `processingPool` for I/O dispatch (short-lived)
- `ProcessingThread` spawns a new virtual thread per readiness event rather than reusing a thread, relying on the zero-cost nature of virtual threads
- `AsyncServicesManager` mirrors the same wrapper pattern as `AsyncService`: core logic always lives in the sync delegate, async is strictly additive
- `ContextPropagationFilter` copies only Request- and Session-scope attributes (not Application or Site) — those broader scopes are considered infrastructure-level and should not be propagated per-request

### Implementation Details
- 10 new source files across packages: `service` (ServiceState), `service.channel` (DataChannel, ChannelHandler, ChannelPipeline), `service.manager` (SelectableChannelManager, ConnectionThread, ProcessingThread, AsyncServicesManager), `service.filter` (ContextPropagationFilter), `service.demo.combined` (ChannelManagerDemo)
- `SelectableChannelManager` uses a private `ChannelRegistration` record to attach both `DataChannel` and `ChannelPipeline` to each `SelectionKey` as an attachment object
- Default selector buffer: 8192 bytes; default select timeout: 100 ms

### Test Coverage
- `SelectableChannelManagerTest` (15 tests): event loop start/stop, channel register/unregister, pipeline retrieval, read/write/connect dispatch, error handling, concurrent registration, close behaviour
- `ChannelManagerDemoTest` (11 tests): full connect-read-write-disconnect-error round-trips, RecordingHandler event ordering, InMemoryDataChannel read/write symmetry, ContextPropagationFilter scope copy, AsyncServicesManager future completion
- **Total: 138 service tests (540 total project)**

---

## Commit: Demo Expansion — Remaining Demo Sources (2026-06-16)

### Original Request
> "Single commit adding remaining demo sources and tests across all 4 modules. Service: 4 new demo source files (AsyncEchoService, AsyncPipelineDemo, ChatService, DependentServicesDemo). Test count unchanged at 138."

### Reformulated Requirements
1. `AsyncEchoService` — async variant of EchoService demonstrating CompletableFuture-based service responses in the procedural async style
2. `AsyncPipelineDemo` — demo showcasing `AsyncServicePipeline` chained with async service calls on virtual threads
3. `ChatService` — multi-participant chat service demo using ServicesManager with session-scoped contexts and access control
4. `DependentServicesDemo` — combined demo illustrating dependency-aware startup/shutdown ordering via `AbstractServicesManager` with priority configuration

### Final Design Decisions
- New demo sources reside in existing `service.demo.*` packages — no new packages introduced
- `ChatService` reuses `SessionScope` and `EXACT` user type to demonstrate realistic multi-user scenarios
- `DependentServicesDemo` uses explicit `ServiceDescriptor` dependencies to verify correct startup/stop sequencing

### Implementation Details
- 4 new source files added to `service.demo.*` packages
- No new test files — existing 138 tests provide coverage of the underlying APIs exercised by these demos

### Test Coverage
- No new tests — demo sources are covered by the existing test suite
- **Total: 138 service tests (764 total project)**

---

## Commit: Stream Contract — ByteBuffer Stream Contract Documentation & Flaky Test Fix (2026-07-06)

### Original Request
> "Document the stream contract in ProcessingThread and SelectableChannelManager Javadoc: codecs handle accumulation, not transport. Fix flaky testPauseByAddress in PassThroughConnectionTest."

### Reformulated Requirements
1. `ProcessingThread` Javadoc documents stream contract: `processReadable()` passes a single read's worth of data to the pipeline; it does *not* accumulate bytes across reads — accumulation is the codec's responsibility
2. `SelectableChannelManager.dispatchKey()` Javadoc documents the same contract: the manager does not buffer or coalesce reads across selector wake-ups
3. Fix flaky `testPauseByAddress` in `PassThroughConnectionTest`: replace `Thread.sleep(200)` with warm-up round-trip on both connections to prove the relay is active before testing pause behavior

### Final Design Decisions
- Stream contract is documented at both the dispatch site (`SelectableChannelManager.dispatchKey`) and the execution site (`ProcessingThread` class Javadoc) — two places developers are likely to look
- The Javadoc references specific codec examples (`Http2FrameCodec`, `LdapCodec`) as the canonical pattern
- Flaky test fix uses a deterministic warm-up round-trip instead of a sleep — the relay is proven active by a successful exchange, not by waiting an arbitrary duration

### Implementation Details
- Files modified: 3 (`ProcessingThread.java`, `SelectableChannelManager.java`, `PassThroughConnectionTest.java`)
- Lines: +40/-2
- `ProcessingThread.java`: added class-level Javadoc block documenting stream contract
- `SelectableChannelManager.java`: added Javadoc to `dispatchKey()` method
- `PassThroughConnectionTest.java`: replaced `Thread.sleep(200)` with warm-up round-trip on both connections

### Test Coverage
- No new tests (documentation-only changes to production code; test fix improves existing test reliability)
- **Total: 194 service tests**

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~0 |
| Agent tool calls | ~0 |
| Agent wall time | ~0 min |
| Files created/modified | 3 |
| Lines added/removed | +40 / -2 |
| Tests added | 0 (total: 194) |

---

## Commit: ServiceGroup — Multi-Selector I/O Event Loop (2026-07-07)

### Original Request
> "Implement the ServiceGroup multi-selector I/O event loop for the lego-flow service module. ServiceGroupStatistics for per-selector I/O tracking, ServiceGroup with N+1 selectors (connector + data), round-robin channel distribution, builder pattern. Deferred-registration support in UdpDataChannel and MulticastDataChannel. DemoServiceAll comprehensive demo with Results record. Unit tests for ServiceGroupStatistics and ServiceGroup."

### Reformulated Requirements
1. `ServiceGroupStatistics` — per-selector I/O statistics with ThreadLocal<Integer> for auto-routing, AtomicLong/AtomicLongArray counters for connections, TCP bytes, UDP packets/bytes, key type counts/durations, per-selector read/write bytes/durations/key counts; Snapshot record for immutable capture; reset(); toString() with per-selector breakdown and transfer rate formatting
2. `ServiceGroup` — multi-selector I/O event loop implementing AutoCloseable; N+1 selectors (connector at index 0, data at indices 1..N); round-robin distribution via AtomicInteger; builder pattern with dataSelectorCount, bufferSize, selectTimeoutMs; start()/stop() lifecycle; selectorLoop() per selector thread; dispatchKey() reusing ProcessingThread; ChannelRegistration record for SelectionKey attachment
3. `UdpDataChannel` deferred registration — new constructor taking only DatagramChannel (configures non-blocking, selectionKey=null); registerWith(Selector) method; volatile selectionKey field; null-safe close() and getSelectionKey()
4. `MulticastDataChannel` deferred registration — new constructor calling super(DatagramChannel)
5. `DemoServiceAll` — comprehensive demo with Results record covering 7 scenarios: lifecycle, UDP echo, multicast, statistics, multi-selector distribution, channel manager basics, service lifecycle
6. `DemoServiceAllTest` — functional test for all demo scenarios
7. `ServiceGroupStatisticsTest` — 12+ unit tests for all counter types, snapshot, reset, toString, formatRate
8. `ServiceGroupTest` — 15+ unit tests for builder, lifecycle, registration, round-robin, echo through event loop

### Final Design Decisions
- ServiceGroup uses CopyOnWriteArrayList for registered channels — safe for concurrent registration during event loop
- Statistics ThreadLocal auto-routing means selector threads never need to pass their index to recording methods
- Deferred registration in UdpDataChannel uses volatile for selectionKey to ensure visibility across threads
- Builder pattern validates all parameters at setter time (fail-fast)
- ChannelRegistration is a public record on ServiceGroup (analogous to SelectableChannelManager's package-private record)
- DemoServiceAll.demoServiceGroupMulticast() uses loopback interface for CI-safe multicast testing

### Implementation Details
- 4 new source files: ServiceGroupStatistics.java, ServiceGroup.java, DemoServiceAll.java, DemoServiceAllTest.java
- 2 new test files: ServiceGroupStatisticsTest.java, ServiceGroupTest.java
- 2 modified source files: UdpDataChannel.java (deferred registration), MulticastDataChannel.java (deferred constructor)
- 4 modified doc files: CLAUDE.md, README.md, ARCHITECTURE.md, REQUIREMENTS.md

### Test Coverage
- ServiceGroupStatisticsTest: 12 tests (constructor validation, counters, snapshot, reset, toString, formatRate, thread-local)
- ServiceGroupTest: 15 tests (builder, defaults, validation, start/stop, idempotent, registration, round-robin, selector access, statistics, UDP echo, close, null name)
- DemoServiceAllTest: 7 tests (runAll + individual scenario tests)
- **New tests: ~34**
- **Total: ~228 service tests**

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 1 (agent-ad938d076e060e755) |
| Agent tokens | ~50000 |
| Agent tool calls | ~40 |
| Agent wall time | ~15 min |
| Files created/modified | 10 |
| Lines added/removed | +900 / -5 |
| Tests added | ~34 (total: ~228) |

---

## Commit: ServiceGroup Test Fixes + UPnP Integration (2026-07-07)

### Original Request
> "introduce in service module IOGroup based on NetIOGroup concept... use it e.g. in UpNP... add related demo all cases and update documentation/costs. also re-use idea of additional per-selector statistics."

### Reformulated Requirements
1. Fix ServiceGroup UDP echo test — ProcessingThread double-reads DatagramChannel
2. Fix DemoServiceAll multicast bind to wildcard address (not multicast group)
3. Create SsdpChannelHandler bridging ServiceGroup pipeline to SsdpService
4. Add opt-in ServiceGroup constructors to SsdpService
5. Create SsdpServiceGroupTest with comprehensive coverage
6. Update documentation and costs

### Final Design Decisions
- UDP channels dispatched differently in ServiceGroup.dispatchKey(): uses receiveDatagram() directly + fireDatagram() pipeline method instead of ProcessingThread (which consumes the datagram via channel.read(), then DatagramHandler.onRead() tries receiveDatagram() on an already-empty channel)
- SsdpService ServiceGroup integration is opt-in via new constructors — existing constructors unchanged
- SsdpChannelHandler is a DatagramHandler that decodes UTF-8, parses SsdpMessage, delegates to processMessage()
- Multicast demo gracefully handles NoRouteToHostException (common with VPN)

### Implementation Details
- Added ChannelPipeline.fireDatagram() for direct DatagramHandler dispatch
- ServiceGroup.dispatchKey() now instanceof-checks for UdpDataChannel
- SsdpService gains ServiceGroup field (null for standalone mode)
- SsdpService.start() branches: ServiceGroup mode registers channel + pipeline, standalone mode starts blocking receive thread

### Test Coverage
- Service module: 230 tests (all pass)
- UPnP module: 418 tests (all pass, +6 new SsdpServiceGroupTest)

### Cost Estimate
| Metric | Value |
|--------|-------|
| Background agents | 0 |
| Agent tokens | ~0 |
| Agent tool calls | ~0 |
| Agent wall time | ~0 min |
| Files created/modified | 6 |
| Lines added/removed | ~250 / ~30 |
| Tests added | 6 (total service: 230, total upnp: 418) |
