# Service Module — Compliance Report

## Design Contracts Covered
- Service lifecycle contract (connect/disconnect, state transitions)
- Scope propagation (Site/Application/Session/Request)
- Access control model (user identity, role-based permissions)
- Channel pipeline contract (handler chain, event dispatch)
- Stream-oriented codec contract (ByteBuffer accumulator pattern)
- Virtual thread usage (async wrapper, NIO event loop, processing pool)

## Compliance Matrix

### Service Lifecycle — Connection & State Contract

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| SL-1 | `connect(ctx)` transitions IDLE → CONNECTING → READY | ✅ Implemented | `AbstractService.connect()`; `ServiceTest`, `ServiceLifecycleDemoTest` |
| SL-2 | `disconnect(ctx)` transitions to STOPPED | ✅ Implemented | `AbstractService.disconnect()`; `ServiceTest`, `ServiceLifecycleDemoTest` |
| SL-3 | `doConnect(ctx)` / `doDisconnect(ctx)` template method hooks | ✅ Implemented | `AbstractService`; `ServiceTest`, `EchoServiceDemoTest` |
| SL-4 | `isConnected()` reflects AtomicBoolean state | ✅ Implemented | `AbstractService`; `ServiceTest` |
| SL-5 | ServiceDescriptor (name, description, priority, dependencies) | ✅ Implemented | `ServiceDescriptor` record; `ServiceTest` |
| SL-6 | ServiceState 9-state enum with fine-grained transitions | ✅ Implemented | `ServiceState` (IDLE, CONNECTING_TRANSPORT, AUTHENTICATING, READY, PAUSED, DRAINING, DISCONNECTING, FAILED, STOPPED); `ServiceTest` |
| SL-7 | ServiceState → ProcessorState mapping | ✅ Implemented | `ServiceState.toProcessorState()`; `ServiceTest` |
| SL-8 | `canTransitionTo()` enforced via static transition map | ✅ Implemented | `ServiceState.VALID_TRANSITIONS`; `ServiceTest` |

### ServicesManager — Orchestration Contract

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| MG-1 | Dependency-aware start ordering | ✅ Implemented | `ServicesManager`; `ServicesManagerTest`, `MultiServiceManagerDemoTest` |
| MG-2 | Reverse-order stop (dependencies last) | ✅ Implemented | `ServicesManager`; `ServicesManagerTest`, `MultiServiceManagerDemoTest` |
| MG-3 | Priority-based parallel startup | ✅ Implemented | `ServicesManager`; `ServicesManagerTest` |
| MG-4 | ConcurrentHashMap<String, Service<?,?>> for service registry | ✅ Implemented | `AbstractServicesManager`; `ServicesManagerTest` |
| MG-5 | AsyncServicesManager wraps ServicesManager with CompletableFuture returns | ✅ Implemented | `AsyncServicesManager`; `ServicesManagerTest` |

### Scope Propagation — 4-Level Hierarchy

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| SC-1 | SiteScope (global, shared across all) | ✅ Implemented | `SiteScope`; `ScopeTest`, `ScopePropagationDemoTest` |
| SC-2 | ApplicationScope (per application) | ✅ Implemented | `ApplicationScope`; `ScopeTest`, `ScopePropagationDemoTest` |
| SC-3 | SessionScope (per user session) | ✅ Implemented | `SessionScope`; `ScopeTest`, `ScopePropagationDemoTest` |
| SC-4 | RequestScope (per request, short-lived) | ✅ Implemented | `RequestScope`; `ScopeTest`, `ScopePropagationDemoTest` |
| SC-5 | ConcurrentHashMap for thread-safe attribute storage per scope | ✅ Implemented | All scope implementations; `ScopeTest` |
| SC-6 | UUID-based scope IDs | ✅ Implemented | `Scope` interface; `ScopeTest` |
| SC-7 | Independent scope lifecycle (child destroy does not affect parent) | ✅ Implemented | Scope hierarchy; `ScopeTest` |
| SC-8 | ContextPropagationFilter copies Request/Session scope across service boundaries | ✅ Implemented | `ContextPropagationFilter`; `ScopePropagationDemoTest` |

### Access Control — User & Role Model

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| AC-1 | ServiceUser with identity types: ANONYMOUS, SHARED, EXACT | ✅ Implemented | `ServiceUser`; `AccessControlTest`, `AccessControlDemoTest` |
| AC-2 | Role-based permission checking via AccessControl | ✅ Implemented | `AccessControl`; `AccessControlTest`, `AccessControlDemoTest` |
| AC-3 | ServiceContext carries user identity | ✅ Implemented | `ServiceContext`; `ServiceContextTest` |
| AC-4 | Extensible AccessControl for custom permission logic | ✅ Implemented | `AccessControl` interface; `AccessControlTest` |

### Channel Pipeline — Handler Chain Contract

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| CP-1 | DataChannel with read/write/isOpen/getSelectionKey | ✅ Implemented | `DataChannel` interface; `SelectableChannelManagerTest` |
| CP-2 | ChannelHandler event callbacks: onRead, onWrite, onConnect, onDisconnect, onError | ✅ Implemented | `ChannelHandler` interface; `SelectableChannelManagerTest`, `ChannelManagerDemoTest` |
| CP-3 | ChannelPipeline backed by CopyOnWriteArrayList | ✅ Implemented | `ChannelPipeline`; `SelectableChannelManagerTest` |
| CP-4 | addFirst/addLast/remove for dynamic handler management | ✅ Implemented | `ChannelPipeline`; `SelectableChannelManagerTest` |
| CP-5 | fire* methods propagate events through all handlers in insertion order | ✅ Implemented | `ChannelPipeline`; `SelectableChannelManagerTest`, `ChannelManagerDemoTest` |
| CP-6 | Handler exception stops propagation, fireError called on all handlers | ✅ Implemented | `ChannelPipeline`; `SelectableChannelManagerTest` |
| CP-7 | fireRead passes data.asReadOnlyBuffer() to protect shared buffer position | ✅ Implemented | `ChannelPipeline.fireRead()`; `SelectableChannelManagerTest` |

### Stream-Oriented Codec — ByteBuffer Accumulator Pattern

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| BC-1 | Transport (ProcessingThread) does NOT accumulate — passes single read() worth | ✅ Implemented | `ProcessingThread.processReadable()`; documented in Javadoc |
| BC-2 | Codecs maintain internal ByteBuffer accumulators | ✅ Implemented | Pattern used by Http2FrameCodec, RtspCodec, SipCodec, LdapCodec |
| BC-3 | combineWithAccumulator() merges leftover + new data before decoding | ✅ Implemented | Codec pattern; documented in ARCHITECTURE.md |
| BC-4 | decodeAll() loop extracts complete protocol units, saves remainder | ✅ Implemented | Codec pattern; documented in ARCHITECTURE.md |
| BC-5 | Partial data in single read is normal (not an error) | ✅ Implemented | Contract documented in ProcessingThread and SelectableChannelManager Javadoc |
| BC-6 | Per-connection codec instance (accumulation state isolated) | ✅ Implemented | Each connection gets own codec; documented in ARCHITECTURE.md |

### NIO Event Loop — SelectableChannelManager Contract

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| NI-1 | Single virtual-thread selector loop | ✅ Implemented | `SelectableChannelManager`; `SelectableChannelManagerTest` |
| NI-2 | Readable/writable/connectable key dispatch to processing pool | ✅ Implemented | `SelectableChannelManager.dispatchKey()`; `SelectableChannelManagerTest` |
| NI-3 | ProcessingThread per readiness event (own virtual thread) | ✅ Implemented | `ProcessingThread`; `SelectableChannelManagerTest` |
| NI-4 | ByteBuffer owned per ProcessingThread instance (not shared) | ✅ Implemented | `ProcessingThread`; documented in ARCHITECTURE.md |
| NI-5 | channelsByService / pipelinesByService in ConcurrentHashMap | ✅ Implemented | `SelectableChannelManager`; `SelectableChannelManagerTest` |
| NI-6 | AtomicBoolean guards event loop start/stop | ✅ Implemented | `SelectableChannelManager.running`; `SelectableChannelManagerTest` |

### UDP Transport — Datagram Contract

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| UD-1 | UdpDataChannel wraps DatagramChannel with send/receive | ✅ Implemented | `UdpDataChannel`; `UdpDataChannelTest`, `UdpEchoDemoTest` |
| UD-2 | DatagramHandler callbacks: onReceive, onSend, onError | ✅ Implemented | `DatagramHandler`; `UdpDataChannelTest` |
| UD-3 | MulticastDataChannel with joinGroup/leaveGroup | ✅ Implemented | `MulticastDataChannel`; `MulticastDataChannelTest`, `UdpMulticastDemoTest` |
| UD-4 | MulticastConfig (group address, network interface, TTL, loopback) | ✅ Implemented | `MulticastConfig` record; `MulticastDataChannelTest` |
| UD-5 | DatagramPacketInfo metadata (source/destination address) | ✅ Implemented | `DatagramPacketInfo` record; `UdpDataChannelTest` |
| UD-6 | UdpChannelManager with per-channel virtual thread receive loop | ✅ Implemented | `UdpChannelManager`; `UdpChannelManagerTest` |

### Virtual Thread Usage

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| VT-1 | AsyncService delegates to sync on virtual threads via newVirtualThreadPerTaskExecutor() | ✅ Implemented | `AsyncService`; `AsyncServiceTest`, `AsyncEchoDemoTest` |
| VT-2 | AsyncServicesManager delegates to ServicesManager on virtual threads | ✅ Implemented | `AsyncServicesManager`; `ServicesManagerTest` |
| VT-3 | SelectableChannelManager selector loop on virtual thread | ✅ Implemented | `SelectableChannelManager`; `SelectableChannelManagerTest` |
| VT-4 | Two independent virtual-thread pools: connectionPool + processingPool | ✅ Implemented | `SelectableChannelManager`; `SelectableChannelManagerTest` |
| VT-5 | UdpChannelManager per-channel virtual thread | ✅ Implemented | `UdpChannelManager`; `UdpChannelManagerTest` |
| VT-6 | ConnectionThread with volatile reference and cooperative cancellation | ✅ Implemented | `ConnectionThread`; documented in ARCHITECTURE.md |

### Dual API — Sync/Async + Procedural/Functional

| Contract | Requirement | Status | Verification |
|----------|------------|--------|-------------|
| DA-1 | Sync procedural: service.consume(ctx, data), service.connect(ctx) | ✅ Implemented | `Service`; `ServiceTest`, `EchoServiceDemoTest`, `CounterServiceDemoTest` |
| DA-2 | Async procedural: asyncService.consume(ctx, data) → CompletableFuture | ✅ Implemented | `AsyncService`; `AsyncServiceTest`, `AsyncEchoDemoTest` |
| DA-3 | Functional sync: ServicePipeline.map().filter().process() | ✅ Implemented | `ServicePipeline`; `ServicePipelineTest`, `PipelineDemoTest` |
| DA-4 | Functional async: AsyncServicePipeline.process() → CompletableFuture | ✅ Implemented | `AsyncServicePipeline`; `AsyncPipelineDemoTest` |
| DA-5 | ServiceBuilder for lambda-based service construction | ✅ Implemented | `ServiceBuilder`; `ServiceBuilderTest`, `LambdaServiceDemoTest` |
| DA-6 | ServiceComposer for ordered service chains | ✅ Implemented | `ServiceComposer`; `ServiceComposerTest`, `ComposerDemoTest` |
| DA-7 | sync() back-reference from async to sync variant | ✅ Implemented | `AsyncService.sync()`, `AsyncServicesManager.sync()`; `AsyncServiceTest` |

## Design Rationale: Sync-Primary API

The NIO layer (`SelectableChannelManager`) is inherently async — the selector event loop dispatches I/O events to virtual thread pools, and `ConnectionThread` returns `CompletableFuture<Void>`. Despite this, the public API (`DataProcessor`, `Service`) is sync-primary by design. JDK 25 virtual threads make blocking calls near-zero-cost from the platform scheduler's perspective, eliminating the traditional motivation for async-primary APIs. Sync code is simpler to write, debug, test, and stack-trace. The async wrapper (`AsyncService`, `AsyncServicePipeline`) exists for callers who need `CompletableFuture` composability — timeouts, combining results, reactive pipelines — without requiring the entire framework to adopt async signatures.

## Known Limitations
- Async API is a wrapper over sync — a deliberate virtual thread strategy, not a limitation (see rationale above)
- No built-in circuit breaker or retry logic in ServicesManager
- No distributed scope propagation — scopes are JVM-local only
- PassThroughConnection is a testing/demo utility, not a production transport
- No flow control between services in a composed chain

## Test Coverage Summary
- Total compliance tests: 194 (per CLAUDE.md)
- Key unit test classes: `ServiceTest`, `AsyncServiceTest`, `ServiceContextTest`, `ScopeTest`, `AccessControlTest`, `ServicesManagerTest`, `SelectableChannelManagerTest`, `ServicePipelineTest`, `ServiceBuilderTest`, `ServiceComposerTest`, `UdpDataChannelTest`, `MulticastDataChannelTest`, `UdpChannelManagerTest`, `PassThroughConnectionTest`, `BufferUtilsTest`
- Key demo test classes: `EchoServiceDemoTest`, `CounterServiceDemoTest`, `AsyncEchoDemoTest`, `AccessControlDemoTest`, `PipelineDemoTest`, `AsyncPipelineDemoTest`, `LambdaServiceDemoTest`, `ComposerDemoTest`, `ServiceLifecycleDemoTest`, `MultiServiceManagerDemoTest`, `ScopePropagationDemoTest`, `ChannelManagerDemoTest`, `UdpEchoDemoTest`, `UdpMulticastDemoTest`
- All service lifecycle transitions verified (connect, disconnect, state changes)
- All four API styles verified (sync procedural, async procedural, functional sync, functional async)
- Scope hierarchy and propagation verified across service boundaries
- Channel pipeline handler chain ordering and error propagation verified
- UDP transport (unicast and multicast) verified
