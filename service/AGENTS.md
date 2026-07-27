# Service Module — Development Guide

## Module Overview

The **service** module builds on the blocks framework to provide service-oriented lifecycle management, scoped contexts, user/role-based access control, and dual API support (sync/async, procedural/functional).

## Key Interfaces

### Service<I,O>
Extends `DataProcessor<I,O>` with connection lifecycle, descriptor, dependencies, priority. Default `async()` method returns `AsyncService<I,O>` wrapper.

### AsyncService<I,O>
Lightweight async wrapper returning `CompletableFuture<T>`. Delegates to sync implementation on virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`. This sync-primary design is deliberate: the NIO layer (`SelectableChannelManager`) is inherently async, but JDK 25 virtual threads make blocking calls near-zero-cost, so sync APIs give simpler code without throughput penalty. See `doc/ARCHITECTURE.md` § "Wrapper Pattern (Async)" for the full rationale.

### ServiceContext
Extends `Context` with scoped sub-elements (Site/Application/Session/Request), user identity, role-based access control.

### ServicesManager
Manages service lifecycle with dependency-aware start/stop ordering and priority-based parallel startup.

### ServiceGroup
Multi-selector I/O event loop. Distributes channels across N+1 selectors (1 connector + N data selectors) with round-robin assignment. Each selector runs in its own virtual thread. Statistics tracked per-selector via `ServiceGroupStatistics`. Built via `ServiceGroup.builder("name").dataSelectorCount(2).build()`.

## Dual API Convention

- **Procedural sync**: `service.consume(ctx, data)`, `service.connect(ctx)`
- **Procedural async**: `asyncService.consume(ctx, data)` returns `CompletableFuture<Void>`
- **Functional sync**: `ServicePipeline.map().filter().process()`, `ServiceBuilder.of().onConvertToOutput().build()`
- **Functional async**: `AsyncServicePipeline.process()` returns `CompletableFuture<List<T>>`

## ByteBuffer Stream Contract

The service module defines a clear contract between the transport layer and protocol codecs:

- `ProcessingThread` passes a single `read()` worth of data to the pipeline. It does **not** accumulate bytes across reads.
- Codecs in the pipeline (`Http2FrameCodec`, `RtspCodec`, `SipCodec`, `LdapCodec`, etc.) maintain internal `ByteBuffer` accumulators. They combine each incoming chunk with buffered partial data, extract complete protocol units, and save the remainder.
- Partial data in a single read is normal, not an error.

This contract is documented in the Javadoc of `ProcessingThread` (class-level) and `SelectableChannelManager.dispatchKey()`.

## Dependencies
- blocks (core DP/DF framework)
- slf4j-api (logging)

## Commit Rules
- Update doc/REQUIREMENTS.md with commit section
- Update doc/ARCHITECTURE.md if architecture changed
- Update README.md for API changes
