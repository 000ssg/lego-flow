# Client TCP Connection Pattern

## The Problem

Connecting to a broker (e.g., RabbitMQ on localhost:5672) via a non-blocking `SocketChannel` and `SelectableChannelManager` selector event loop. The connection completes in microseconds on localhost, racing with the selector's `select()` cycle (100ms timeout). Multiple failure modes observed:

1. **Latch set after register** — `onConnect()` fires before latch is ready → latch never counts down → timeout
2. **Connect before register** — `SocketChannel.connect()` starts the TCP handshake. If done before `registerChannel()`, the connection completes before selector is listening for `OP_CONNECT` → key is cancelled or never fires → connection never established
3. **registerChannel registers OP_READ|OP_CONNECT** — data arrives while OP_READ is enabled → `processReadable()` reads into buffer → `client.connect()` also calls `receive()` → two readers fighting the same socket → deadlock

## The Mandatory Sequence

This is the ONLY sequence that works correctly:

```java
// 1. Create non-blocking socket
var socketChannel = SocketChannel.open();
socketChannel.configureBlocking(false);
var dataChannel = new TcpDataChannel(socketChannel);

// 2. Create transport
var transport = new PipelineTransport(dataChannel);

// 3. Create handler with completion latch
var handler = new AmqpClientChannelHandler(this, ctx);
var latch = new CountDownLatch(1);
handler.setConnectLatch(latch);

// 4. Register with manager (OP_CONNECT only — no OP_READ yet!)
ctx.registerChannel(this, dataChannel, handler);

// 5. Start async connect (AFTER registration)
socketChannel.connect(new InetSocketAddress(host, port));

// 6. Wait for TCP connect + protocol handshake
if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
    throw new IOException("Connection timeout: " + host + ":" + port);
}
```

And in the handler:

```java
@Override
public void onConnect(DataChannel channel) {
    // Finish TCP connect
    var socketChannel = ((TcpDataChannel) channel).getSocketChannel();
    socketChannel.finishConnect();

    // Enable data flow (OP_READ|OP_WRITE)
    var mgr = ctx.getChannelManager();
    mgr.updateChannelOps(service, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

    // Run protocol handshake (blocks until complete)
    var client = new AmqpClient(config);
    client.connect(transport);

    // Signal completion
    service.setClient(client);
    connectLatch.countDown();
}
```

## Key Principles

- **TCP connection management and protocol handshake are strictly separated** — the selector manages TCP, the protocol runs inside `onConnect()`
- **OP_READ is never enabled until the handler is ready** — the channel is registered with `OP_CONNECT` only, then switched to `OP_READ|OP_WRITE` inside `onConnect()`
- **Latch is always set before register()** — localhost connections can complete between register and connect, so the handler must be fully prepared before any selector interaction
- **Never register for OP_READ before the handler is ready** — this causes two readers (selector loop + inline protocol) to fight the same socket
- **`updateChannelOps` calls `selector.wakeup()`** — to force the selector to see the new interest set immediately
- **Protocol handshake (`client.connect(transport)`) blocks on `receive()`** — this is correct: it blocks on a semaphore that the selector's `processReadable()` releases when data arrives

## Data Flow Chain

```
selector.select() → OP_CONNECT → processConnectable() → fireConnect()
  → handler.onConnect() → finishConnect() → updateChannelOps(READ|WRITE)
  → client.connect(transport) → transport.receive() [blocks on semaphore]

selector.select() → OP_READ → processReadable() → channel.read(readBuffer)
  → fireRead() → handler.onRead() → transport.onRead() [adds to buffer + releases semaphore]
  → transport.receive() [wakes up, drains buffer] → protocol handshake proceeds
```

## Reference

Implemented in `AmqpClientService.doConnect()` and `AmqpClientChannelHandler.onConnect()`.
