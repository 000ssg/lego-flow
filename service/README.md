# Lego Flow Service Module

![Java 25+](https://img.shields.io/badge/Java-25%2B-blue)
![Maven 3.9+](https://img.shields.io/badge/Maven-3.9%2B-orange)
![Tests 230](https://img.shields.io/badge/tests-230-brightgreen)
![Version 0.1.0-SNAPSHOT](https://img.shields.io/badge/version-0.1.0-SNAPSHOT-yellow)

Service-oriented framework building on the blocks module. Provides service lifecycle management, scoped contexts, user/role-based access control, dual APIs (sync/async + procedural/functional), NIO channel management with a virtual-thread event loop, UDP transport with multicast support, and context propagation across service boundaries.

## Features

- **Service<I,O>** — extends DataProcessor with connect/disconnect lifecycle, descriptor, dependencies, priority
- **ServiceState** — 9-state enum (CONNECTING_TRANSPORT, AUTHENTICATING, DRAINING, DISCONNECTING, …) with enforced valid transitions, mapped to ProcessorState
- **AsyncService<I,O>** — lightweight CompletableFuture wrapper using virtual threads
- **ServiceContext** — scoped context hierarchy (Site → Application → Session → Request)
- **Access Control** — role-based operation restrictions (ADMIN, USER, GUEST)
- **ServicesManager** — dependency-aware lifecycle management with priority ordering
- **AsyncServicesManager** — CompletableFuture wrapper for ServicesManager on virtual threads
- **NIO Channel API** — DataChannel interface, ChannelHandler callbacks, ChannelPipeline ordered handler chain
- **SelectableChannelManager** — NIO Selector + dual virtual-thread pools (connection + I/O dispatch), extends AbstractServicesManager
- **ContextPropagationFilter** — DataFilter that propagates ServiceContext Request/Session scope attributes across service boundaries
- **Functional API** — ServicePipeline, ServiceBuilder, ServiceComposer, AsyncServicePipeline
- **UDP Transport** — UdpDataChannel for datagram I/O, DatagramHandler for receive/send callbacks
- **Multicast** — MulticastDataChannel with group join/leave, MulticastConfig for TTL/interface/loopback
- **UdpChannelManager** — virtual-thread receive loops for UDP channels, analogous to SelectableChannelManager for TCP
- **ServiceGroup** — multi-selector I/O event loop with N+1 selectors (connector + data), round-robin channel distribution, builder API
- **ServiceGroupStatistics** — per-selector I/O statistics with thread-local routing, snapshot records, and transfer rate formatting

## Quick Start

### Procedural Sync
```java
var echo = new EchoService();
var ctx = new DefaultServiceContext(ServiceUser.anonymous());
echo.connect(ctx);
echo.consume(ctx, "hello");
echo.disconnect(ctx);
```

### Procedural Async
```java
var asyncEcho = echo.async();
asyncEcho.connect(ctx).thenRun(() ->
    asyncEcho.consume(ctx, "hello").join()
);
```

### Functional — ServiceBuilder
```java
var service = ServiceBuilder.of(String.class, Integer.class)
    .descriptor("parser", "Parses strings to integers")
    .onConvertToOutput((ctx, input) -> { ... })
    .onConvertToInput((ctx, output) -> { ... })
    .onConnect((ctx, svc) -> log.info("Connected"))
    .build();
```

### Functional — ServicePipeline
```java
var pipeline = new ServicePipeline<String>()
    .filter(s -> s != null && !s.isBlank())
    .map(String::trim)
    .map(String::toUpperCase);
List<String> results = pipeline.process(inputList);
```

### Async Pipeline
```java
var asyncPipeline = new AsyncServicePipeline<>(pipeline);
CompletableFuture<List<String>> future = asyncPipeline.process(inputList);
```

### NIO Channel Manager
```java
// Create manager with a NIO Selector and virtual-thread pools
var ctx = new DefaultServiceContext(ServiceUser.anonymous());
try (var manager = new SelectableChannelManager(ctx)) {

    // Register a service and its channel
    var svc = new EchoService();
    manager.register(svc);
    manager.registerChannel(svc, myDataChannel);

    // Attach a handler to the service's pipeline
    manager.getChannelPipeline(svc).addLast(new MyChannelHandler());

    // Start the NIO event loop (virtual thread)
    manager.startEventLoop();
    manager.startAll();

    // ... I/O events dispatched automatically ...

    manager.stopEventLoop();
}
```

### Async ServicesManager
```java
var asyncManager = new AsyncServicesManager(manager);
asyncManager.startAll()
    .thenCompose(_ -> asyncManager.getStates())
    .thenAccept(states -> log.info("States: {}", states))
    .join();
```

### Context Propagation Filter
```java
// Propagates Request/Session scope attributes from sourceCtx into the filter context
var filter = new ContextPropagationFilter<>(String.class, sourceCtx);
service.addInputFilter(filter);
```

### UDP Echo Service
```java
var channel = new UdpDataChannel(new InetSocketAddress(9999));
var manager = new UdpChannelManager();
manager.register(channel, (ch, data, packetInfo) -> {
    // Echo datagram back to sender
    ch.send(data, packetInfo.sourceAddress());
});
manager.start();
```

### Multi-Selector ServiceGroup
```java
try (var group = ServiceGroup.builder("my-group")
        .dataSelectorCount(2)
        .bufferSize(8192)
        .selectTimeoutMs(100)
        .build()) {
    group.start();
    
    // Register UDP channel with data selector (round-robin)
    var dc = DatagramChannel.open();
    var channel = new UdpDataChannel(dc);
    channel.bind(new InetSocketAddress("127.0.0.1", 0));
    var pipeline = new ChannelPipeline();
    pipeline.addLast(myHandler);
    group.registerData(dc, SelectionKey.OP_READ, channel, pipeline);
    
    // Check statistics
    var snap = group.getStatistics().snapshot();
    System.out.println("UDP reads: " + snap.udpPackets()[0]);
    
    group.stop();
}
```

### Multicast
```java
var config = new MulticastConfig(
    InetAddress.getByName("239.1.1.1"),
    NetworkInterface.getByName("en0"),
    /* ttl */ 4);
var channel = new MulticastDataChannel(config);
channel.joinGroup(config.groupAddress());
```

## API Reference

| Interface / Class | Purpose |
|---|---|
| `Service<I,O>` | Sync service with lifecycle (extends DataProcessor) |
| `ServiceState` | 9-state enum with ProcessorState mapping and transition validation |
| `AsyncService<I,O>` | Async wrapper with CompletableFuture returns |
| `ServiceContext` | Scoped context with user/role support |
| `ServicesManager` | Manages multiple services with dependency ordering |
| `AsyncServicesManager` | CompletableFuture wrapper for ServicesManager |
| `SelectableChannelManager` | NIO Selector + virtual-thread event loop, extends AbstractServicesManager |
| `DataChannel` | NIO channel interface (read/write ByteBuffer, isOpen, close, getSelectionKey) |
| `ChannelHandler` | I/O event callback interface (onRead, onWrite, onConnect, onDisconnect, onError) |
| `ChannelPipeline` | Ordered ChannelHandler chain with fire* event propagation (includes `fireDatagram()` for direct DatagramHandler dispatch) |
| `ConnectionThread` | Virtual-thread wrapper for service connection establishment |
| `ProcessingThread` | Virtual-thread wrapper for per-key I/O event dispatch |
| `ContextPropagationFilter<T>` | DataFilter that copies ServiceContext scope attributes to the target context |
| `ServicePipeline<T>` | Functional map/filter/collect pipeline |
| `AsyncServicePipeline<T>` | Async variant of ServicePipeline |
| `ServiceBuilder<I,O>` | Lambda-based service construction |
| `ServiceComposer` | Compose multiple services into chains |
| `UdpDataChannel` | DataChannel for UDP datagrams (send/receive) |
| `DatagramHandler` | Callback interface for datagram I/O events |
| `MulticastDataChannel` | UDP channel with multicast group management |
| `MulticastConfig` | Multicast configuration (group, interface, TTL) |
| `DatagramPacketInfo` | Metadata record for datagram source/destination |
| `UdpChannelManager` | Manages UDP channels with virtual-thread receive loops |
| `ServiceGroup` | Multi-selector I/O event loop with N+1 selectors and round-robin distribution |
| `ServiceGroupStatistics` | Per-selector I/O statistics with snapshot records and transfer rate formatting |

## Build

```bash
mvn compile -pl service -am
mvn test -pl service -am
```

## Documentation

- [Code Overview](doc/CODE_OVERVIEW.md) | [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md)
- [Root README](../README.md) | [Root Code Overview](../doc/CODE_OVERVIEW.md) | [Root Architecture](../doc/ARCHITECTURE.md)
