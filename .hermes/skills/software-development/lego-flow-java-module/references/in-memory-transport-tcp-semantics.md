# In-Memory Transport TCP Semantics

## Overview

`InMemoryByteTransport` supports configurable TCP-like semantics to simulate real network behavior in tests.

## Configuration

### TcpSemantics Enum

| Constant | Chunk Size | Max Queue | Backpressure | Description |
|----------|------------|-----------|--------------|-------------|
| `DEFAULT` | 256 bytes | Unbounded | No | Simulates TCP segmentation |
| `NONE` | N/A | Unbounded | No | Raw synchronous queue (original behavior) |
| `COALESCING` | 16 KB | Unbounded | No | Simulates TCP coalescing |
| `BACKPRESSURE` | 256 bytes | 4 KB | Yes | Simulates full send buffer |

### Usage

```java
// Create pair with default TCP semantics (chunked)
var pair = InMemoryByteTransport.createPair();

// Disable TCP semantics for deterministic testing
var pair = InMemoryByteTransport.createPair();
pair[0].withTcpSemantics(TcpSemantics.NONE);
pair[1].withTcpSemantics(TcpSemantics.NONE);

// Enable backpressure simulation
var pair = InMemoryByteTransport.createPair();
pair[0].withTcpSemantics(TcpSemantics.BACKPRESSURE);
pair[1].withTcpSemantics(TcpSemantics.BACKPRESSURE);
```

## Behavior

### Chunked Delivery (DEFAULT)

Each `send()` call splits data into chunks (max 256 bytes by default). Each chunk is enqueued separately, simulating TCP packet segmentation.

```java
// Sending 512 bytes creates 2 chunks
transport.send(ByteBuffer.wrap(new byte[512]));
// Peer receives 2 separate chunks when calling receive()
```

### Partial Reads

`receive()` delivers one chunk at a time, simulating TCP partial reads. If the caller's buffer is smaller than the chunk, only part of the chunk is consumed and the remainder is re-queued.

### Backpressure (BACKPRESSURE)

When enabled, `send()` blocks if the outbound queue exceeds the limit (4 KB by default). This simulates TCP flow control when the receiver's buffer is full.

## When to Use Each Mode

| Test Type | Recommended Semantics | Reason |
|-----------|----------------------|--------|
| Protocol core unit tests | `NONE` | Deterministic, no timing |
| Protocol integration tests | `DEFAULT` | Simulates real TCP behavior |
| Backpressure tests | `BACKPRESSURE` | Tests flow control |
| Coalescing tests | `COALESCING` | Tests batched delivery |

## Implementation Details

- `send()` advances the caller's buffer position to limit (consumes all bytes)
- `receiveWithTimeout()` blocks until at least one byte is available or timeout
- `close()` poisons both queues with empty buffers to wake blocked readers
- The `headBuffer` pattern ensures correct reassembly when reads are partial
