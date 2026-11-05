# Selector Thread Discipline (Verified 2026-09-09)

## Core Rules Discovered

### 1. OP_CONNECT is Synchronous in Selector Thread
`finishConnect()` is called directly in `dispatchKey()` on the selector thread. Not dispatched to any pool. After `finishConnect()`, change interest ops to `OP_READ|OP_WRITE`, then fire `pipeline.fireConnect()`. All synchronous. Prevents spurious OP_CONNECT re-selection and key lifecycle races.

### 2. TCP Read is Synchronous in Selector Thread
`dispatchKey()` reads bytes from socket into `ProcessingThread.readBuffer`, flips the buffer, then submits `processReadable()` to pool. NEVER read from socket in async thread — multiple async reads on the same non-blocking socket cause data races and false EOF (-1).

### 3. Handler Does Only Protocol Work
`onConnect()` guards with `AtomicBoolean`, counts down latch. NEVER calls `finishConnect()`, never touches channel or selector. TCP lifecycle is manager's exclusive domain.

### 4. Single Pipeline Per Channel
`ServiceContext.registerChannel()` MUST delegate to `manager.registerChannel(service, channel, handler)`. If both create pipelines, events go to the wrong one. Two pipelines = handler never receives events.

### 5. Transport at Handler Construction
Handler receives `PipelineTransport` at construction so `onRead()` can deliver data immediately. If transport is null when data arrives, it's silently lost.

### 6. Handler.onDisconnect Closes Transport
Stack trace confirmed: `dispatchKey → processDisconnect → fireDisconnect → handler.onDisconnect → transport.close()`. TCP close is a transport event. Protocol may still have buffer data to process separately.

### 7. Transport.send() Must Never Write Directly to Channel
`PipelineByteTransport.send()` enqueues to `outboundQueue` — the selector thread writes via `onWrite()`. Direct `channel.write()` from the calling thread races with the selector's read/write and causes message loss. This rule applies to ALL protocols (NATS, STOMP, MQTT, Kafka, AMQP). Any protocol needing fast initial writes should handle it at the codec or protocol layer, not in the transport SPI.
