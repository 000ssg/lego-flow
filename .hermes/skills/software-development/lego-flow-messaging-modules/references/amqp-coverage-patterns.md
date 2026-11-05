# AMQP Coverage Testing Patterns

## Quick-Win Classes (≤10 missed lines)

When targeting 80%+ coverage, start with small classes that have 1-10 missed lines. Found in AMQP module:

| Class | Missed | Pattern |
|-------|--------|---------|
| `AmqpTransport` (interface) | 1 | `default receive()` — covered by any `InMemoryTransport` test |
| `DeliveryState` (sealed) | 1 | `encode()`/`decode()` — needs round-trip via `DeliveryStateCodec` |
| `FrameAccumulator` (inner) | 1 | `put()` empty-read path — send empty `ByteBuffer` to codec |
| `FrameCodec` | 2 | `encode()`/`decode()` — frame codec test covers |
| `AmqpMessage` | 2 | Constructor with payload — create message in client test |
| `ContainerConfig` | 2 | Builder patterns — instantiate config |
| `SenderLink` | 3 | `send()` path — covered by integration test sending a message |
| `Delivery` | 3 | State transitions — covered by full send-receive flow |
| `ContainerConfig.Builder` | 3 | Builder chain — instantiate container config |
| `AmqpContainer.IncomingMessage` | 3 | Message receipt path — covered by integration test |
| `AmqpClientChannelHandler` | 4 | Connection handler — covered by client connect test |
| `ClientConfig.Builder` | 4 | Builder chain — instantiate client config |
| `ClientConfig` | 3 | Config accessors — create config and read fields |
| `InMemoryTransport` | 4 | Edge cases — empty send, closed receive, timeout |
| `PlainMechanism` | 1 | `respond()` — covered by SASL test |

## AmqpCommonTest Pattern

Create a single test class (`AmqpCommonTest`) that covers multiple small classes at once:

```java
// AmqpException — two constructors
var ex = new AmqpException("amqp:decode-error", "bad frame");
assertThat(ex.condition()).isEqualTo("amqp:decode-error");

var cause = new NullPointerException("root");
var ex2 = new AmqpException("amqp:internal-error", "fail", cause);
assertThat(ex2.getCause()).isSameAs(cause);

// AmqpEventListener — noOp and latchOnFirst
AmqpEventListener.noOp().onEvent(EventType.CONNECTION_STARTED, "c1", null);

var latch = new CountDownLatch(1);
var listener = AmqpEventListener.latchOnFirst(latch, EventType.MESSAGE_RECEIVED);
listener.onEvent(EventType.MESSAGE_RECEIVED, "c1", "link-1");
assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();

// DeliveryState records
new DeliveryState.Accepted();
new DeliveryState.Released();
new DeliveryState.Received(1, 42);
new DeliveryState.Rejected("amqp:undeclared-codec");  // single-arg constructor
new DeliveryState.Rejected("amqp:decode-error", "invalid");
new DeliveryState.Modified(true, false, Map.of("x", 1));
new DeliveryState.TransactionalState(new byte[]{1}, new DeliveryState.Accepted());

// AmqpClientService.AmqpResult
AmqpClientService.AmqpResult.ok("addr", ByteBuffer.wrap("data".getBytes()));
AmqpClientService.AmqpResult.error("link closed");

// ExternalMechanism
var mech = new ExternalMechanism();
assertThat(mech.name()).isEqualTo("EXTERNAL");
assertThat(mech.initialResponse()).isEmpty();
assertThat(mech.respond(new byte[]{1, 2})).isEmpty();
```

## AmqpFrameCodecImpl Edge Cases

```java
// Invalid frame size (< 4) should not produce a frame
ByteBuffer bad = ByteBuffer.allocate(4);
bad.putInt(2);  // size < 4
bad.flip();
codec.onRead(null, bad);
assertThat(extracted).isEmpty();

// Empty read should not produce a frame
codec.onRead(null, ByteBuffer.wrap(new byte[0]));
assertThat(extracted).isEmpty();
```

## TypeCodec Gaps (139 lines missed)

The `TypeCodec` class (791 lines) has 139 missed lines even with 64 tests. The uncovered paths are in:
- Complex type encoding (maps, lists with nested types)
- Error handling paths (range validation failures)
- Described type encoding/decoding
- Array handling

To cover these, add tests for:
1. `AmqpType.Map` with nested values
2. `AmqpType.Symbol` encoding
3. `AmqpType.UUID` encoding
4. `AmqpType.Decimal` / `AmqpType.Decimal128`
5. `AmqpType.Timestamp`
6. `AmqpType.Vbin8` with different sizes
7. Described types (`AmqpType.Described`)
8. Error paths: encoding values outside valid range

## AmqpContainer Gaps (161 lines missed)

Container-side logic is hard to test without Docker. The integration test (`AmqpServiceIntegrationTest`) covers the main paths. Remaining gaps are in:
- Error handling (unexpected messages, protocol violations)
- Edge cases in message routing
- `IncomingMessage` inner class constructors

## Strategy

To bridge the 78.8% → 80% gap (37 lines), focus on:
1. **AmqpCommonTest** (already done) — covers 18 quick-win lines
2. **AmqpFrameCodecImplTest** edge cases — 2 more lines
3. **TypeCodec** complex types — 10+ lines per type
4. **Integration test extensions** — exercise container error paths
