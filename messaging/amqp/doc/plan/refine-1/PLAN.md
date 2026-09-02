# AMQP Protocol Refinement — Phase Plan

**Goal:** Fix all architectural violations, eliminate deadlocks and data loss, ensure service layer compliance, verify interop, update docs, and commit.

**Context:** The AMQP 1.0 implementation has transport-agnostic core (AmqpClient/AmqpContainer) that works over AmqpTransport SPI. Service layer wraps it with service-manager integration (SelectableChannelManager + ChannelPipeline + PipelineTransport). Core logic was built first; service integration was layered on and has architectural violations and race conditions.

**Rules:**
1. Protocol core NEVER touches SocketChannel/Selector — only AmqpTransport
2. Service layer owns TCP lifecycle via SelectableChannelManager
3. No deadlocks: client handshake must not block the selector thread
4. No data loss: PipelineTransport must handle backpressure, not drop frames
5. Tests must use CountDownLatch, not Thread.sleep, for async coordination

---

## Phase 1: Audit & Baseline ✅ DONE

### 1.1 Catalog all source files and identify issues
- [x] AmqpClientService.doConnect() — spawns virtual thread for handshake while blocking on latch. Potential deadlock if selector event loop stalls.
- [x] AmqpContainerService.acceptConnection() — creates PipelineTransport AND calls container.handleConnection(). But AmqpContainerChannelHandler.onConnect() creates ANOTHER PipelineTransport. **Double transport bug.**
- [x] AmqpContainerService.doDisconnect() — does NOT call ctx.getChannelManager().unregisterServerChannel(). **Selector key leak.**
- [x] AmqpClientService.doDisconnect() — calls ctx.getChannelManager().unregisterChannel() without null check on ctx.
- [x] PipelineTransport.send() — drops data when outboundBuffer non-null. **Data loss under backpressure.**
- [x] PipelineTransport.onWrite() — only writes one buffer, no queue. Frames can be lost.
- [x] InProcessIntegrationTest uses InMemoryTransport (bypasses service layer) — doesn't test the actual pipeline.

### 1.2 Run existing tests and document failures
- [x] 268 tests, 1 failed: testDisconnectBeforeConnectDoesNotThrow

---

## Phase 2: Architecture & Flow Fixes ✅ IN PROGRESS

### 2.1 Fix PipelineTransport send() — backpressure queue
- [x] Replace single outboundBuffer with LinkedBlockingQueue<ByteBuffer>
- [x] send() tries immediate write, enqueues remainder
- [x] onWrite() drains queue, re-queues partial writes
- [x] registerOps() checks queue emptiness, not single buffer

### 2.2 Fix AmqpContainerChannelHandler — single transport per connection
- [x] Remove service.acceptConnection() call from onConnect() — it was creating a wasted transport
- [x] Transport created once in handler, passed to container

### 2.3 Fix AmqpContainerService.disconnect() — unregister server channel
- [x] Add ctx.getChannelManager().unregisterServerChannel(this) with null safety

### 2.4 Fix AmqpClientService.doDisconnect() — null safety
- [x] Check ctx != null before accessing channel manager

### 2.5 Fix AmqpClientServiceTest
- [x] Provide real ServiceContext with channel manager for disconnect test

---

## Phase 3: Test & Interop Verification

### 3.1 Service layer integration test
- Add test: AmqpClientService ↔ AmqpContainerService via TCP
- Verify handshake, session, send/receive through full pipeline

### 3.2 In-process integration test update
- Update InProcessIntegrationTest to also test via service layer (not just InMemoryTransport)

### 3.3 Verify interop tests against RabbitMQ Docker
- Run AmqpInteropTest against RabbitMQ
- Verify no deadlocks under load

---

## Phase 4: Documentation & Commit

### 4.1 Update ARCHITECTURE.md
### 4.2 Update COMPLIANCE.md
### 4.3 Update README.md
### 4.4 Final build verification (Maven + Gradle)
### 4.5 Commit with full documentation
