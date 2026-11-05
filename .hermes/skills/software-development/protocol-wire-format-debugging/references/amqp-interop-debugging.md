# AMQP 1.0 Interop Debugging

## AMQP 1.0 vs 0-9-1 Distinction

This project implements AMQP 1.0 (ISO 19464), NOT AMQP 0-9-1. They are different protocols despite sharing the name. Reference clients:
- **Qpid Proton-J** (0.34) — AMQP 1.0, engine-level API (`Proton.connection()`, `Proton.transport()`, `TransportImpl`)
- **RabbitMQ Java client** (`amqp-client`) — AMQP 0-9-1, does NOT work for AMQP 1.0 testing

## Qpid Proton-J Engine API (0.34)

The proton-j manual pump API uses implementation types not exposed by the `Proton` factory:

```java
// Manual pump — bypass Worker/Reactor to route through your own socket
var transport = new TransportImpl();
var connection = ProtonJConnection.create();
connection.bind(transport);
// ... open session, links ...
// Pump in a loop:
//   transport.init()
//   transport.update(buffer)
//   read from socket → buffer
//   transport.update(buffer)
//   transport.flush(outputBuffer) → write to socket
```

Casting `Proton.connection()` to `ProtonJConnection` and `Proton.transport()` to `ProtonJTransport` is required to access `createReceiver()`/`createSender()` methods.

## Broker Behavior Patterns Observed (RabbitMQ AMQP 1.0)

1. **SASL-first**: RabbitMQ expects SASL handshake before OPEN. Connecting without SASL causes connection reset.
2. **END on protocol violation**: If the client's ATTACH or FLOW frames are malformed or frames arrive out of order, RabbitMQ responds with END (session close) + DETACH + CLOSE. This is how you know your frames are wrong.
3. **No TRANSFER after broker END**: Once the broker sends END, it stops sending application data (TRANSFER). The client receives END instead of the expected message, and the receive blocks until timeout.

## Diagnosing Broker Closes

When the broker sends END/DETACH unexpectedly:
1. Capture broker→client bytes with hex logging at `PipelineTransport.onRead()`
2. Decode each frame: check performative type, channel, and frame size
3. Compare with what the reference client sends for the same operation
4. Look for: missing fields, wrong AMQP types, incorrect handle/channel, wrong credit values

## PassThroughConnection / WireTransportInterceptor

The lego-flow `service/passthrough/` module provides:
- `PassThroughConnection` — bidirectional TCP relay with interception points
- `DataInterceptor` interface — `intercept(Direction, byte[])` for each direction
- `WireCaptureInterceptor` — logs every byte as hex with direction labels

Use these to capture traffic from ANY client (Qpid, RabbitMQ Java client, or lego-flow) to RabbitMQ without modifying the client code.

## Building Working Interop Tests

1. Create a test class in `interop-tests/src/test/java/`
2. Start RabbitMQ Docker with AMQP 1.0 plugin enabled
3. Use lego-flow `AmqpClientService` to connect
4. Keep traces at `System.out` level during debugging — `LOG.debug()` is suppressed by default Maven surefire
5. After debugging, clean up traces before committing
