package ssg.legoflow.service.manager;

import ssg.legoflow.service.Service;
import ssg.legoflow.service.channel.ChannelHandler;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.ServerDataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.service.channel.UdpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
public class SelectableChannelManager extends AbstractServicesManager {

    private static final Logger LOG = LoggerFactory.getLogger(SelectableChannelManager.class);
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final long DEFAULT_SELECT_TIMEOUT_MS = 100;

    private final Selector selector;
    private final ExecutorService connectionPool;
    private final ExecutorService processingPool;
    private final int bufferSize;
    private final long selectTimeoutMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, DataChannel> channelsByService = new ConcurrentHashMap<>();
    private final Map<String, ChannelPipeline> pipelinesByService = new ConcurrentHashMap<>();
    private final Map<String, ServerDataChannel> serverChannelsByService = new ConcurrentHashMap<>();
    private volatile Thread selectorThread;

    public SelectableChannelManager(ServiceContext context) {
        this(context, DEFAULT_BUFFER_SIZE, DEFAULT_SELECT_TIMEOUT_MS);
    }

    public SelectableChannelManager(ServiceContext context, int bufferSize, long selectTimeoutMs) {
        super(context);
        this.bufferSize = bufferSize;
        this.selectTimeoutMs = selectTimeoutMs;
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open selector", e);
        }
        this.connectionPool = Executors.newVirtualThreadPerTaskExecutor();
        this.processingPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void registerChannel(Service<?, ?> service, DataChannel channel) {
        registerChannel(service, channel, null);
    }

    public void registerChannel(Service<?, ?> service, DataChannel channel, ChannelHandler handler) {
        var name = service.getDescriptor().name();
        channelsByService.put(name, channel);
        var pipeline = pipelinesByService.computeIfAbsent(name, _ -> new ChannelPipeline());
        if (handler != null) {
            pipeline.addLast(handler);
        }
        // Register with the selector — persistent registration for backpressure
        try {
            var reg = new ChannelRegistration(channel, pipeline, bufferSize);
            if (channel instanceof TcpDataChannel tcp) {
                var socketChannel = tcp.getSocketChannel();
                var key = socketChannel.register(selector, SelectionKey.OP_CONNECT, reg);
                tcp.setSelectionKey(key);
                LOG.debug("Registered TCP channel for service: {} with OP_CONNECT", name);
            } else if (channel instanceof UdpDataChannel udp) {
                var key = udp.getDatagramChannel().register(selector, SelectionKey.OP_READ, reg);
                udp.setSelectionKey(key);
            }
        } catch (IOException e) {
            channelsByService.remove(name);
            pipelinesByService.remove(name);
            throw new UncheckedIOException("Failed to register channel for service: " + name, e);
        }
        LOG.debug("Registered channel for service: {} with handler={}", name, handler != null);
    }

    /**
     * Update interest ops for an already-registered channel (e.g., after TCP connect,
     * switch from OP_CONNECT to OP_READ | OP_WRITE).
     */
    public void updateChannelOps(Service<?, ?> service, int ops) {
        var name = service.getDescriptor().name();
        var channel = channelsByService.get(name);
        if (channel instanceof TcpDataChannel tcp) {
            var key = tcp.getSelectionKey();
            if (key != null) {
                key.interestOps(ops);
                selector.wakeup(); // Force selector to see the new interest set
                LOG.debug("Updated interest ops for service: {} to {} and woke up selector", name, ops);
            }
        }
    }

    public void registerServerChannel(Service<?, ?> service, ServerDataChannel channel) {
        var name = service.getDescriptor().name();
        serverChannelsByService.put(name, channel);
        var pipeline = pipelinesByService.computeIfAbsent(name, _ -> new ChannelPipeline());

        // Register with the selector for OP_ACCEPT
        try {
            channel.registerWith(selector);
            var key = channel.getSelectionKey();
            if (key != null) {
                key.attach(new ServerChannelRegistration(channel, pipeline));
            }
            channel.setSelectionKey(key);
            LOG.debug("Registered server channel for service: {} with OP_ACCEPT", name);
        } catch (IOException e) {
            serverChannelsByService.remove(name);
            pipelinesByService.remove(name);
            throw new UncheckedIOException("Failed to register server channel for service: " + name, e);
        }
    }

    public void unregisterServerChannel(Service<?, ?> service) {
        var name = service.getDescriptor().name();
        var channel = serverChannelsByService.remove(name);
        pipelinesByService.remove(name);
        if (channel != null) {
            try {
                var key = channel.getSelectionKey();
                if (key != null) key.cancel();
                if (channel.isOpen()) channel.close();
            } catch (IOException e) {
                LOG.warn("Error closing server channel for service: {}", name, e);
            }
        }
        LOG.debug("Unregistered server channel for service: {}", name);
    }

    public void unregisterChannel(Service<?, ?> service) {
        var name = service.getDescriptor().name();
        var channel = channelsByService.remove(name);
        pipelinesByService.remove(name);
        if (channel != null) {
            try {
                // Deregister from selector first
                if (channel instanceof TcpDataChannel tcp) {
                    var key = tcp.getSelectionKey();
                    if (key != null) key.cancel();
                } else if (channel instanceof UdpDataChannel udp) {
                    var key = udp.getSelectionKey();
                    if (key != null) key.cancel();
                }
                if (channel.isOpen()) channel.close();
            } catch (IOException e) {
                LOG.warn("Error closing channel for service: {}", name, e);
            }
        }
        LOG.debug("Unregistered channel for service: {}", name);
    }

    public ChannelPipeline getChannelPipeline(Service<?, ?> service) {
        return pipelinesByService.get(service.getDescriptor().name());
    }

    public DataChannel getChannel(Service<?, ?> service) {
        return channelsByService.get(service.getDescriptor().name());
    }

    public ServerDataChannel getServerChannel(Service<?, ?> service) {
        return serverChannelsByService.get(service.getDescriptor().name());
    }

    public void startEventLoop() {
        if (running.compareAndSet(false, true)) {
            selectorThread = Thread.ofVirtual()
                    .name("selector-loop")
                    .start(this::eventLoop);
            LOG.info("Selector event loop started");
        }
    }

    public void stopEventLoop() {
        running.set(false);
        selector.wakeup();
        var t = selectorThread;
        if (t != null) {
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("Selector event loop stopped");
    }

    public boolean isEventLoopRunning() {
        return running.get();
    }

    private void eventLoop() {
        while (running.get()) {
            try {
                int selected = selector.select(selectTimeoutMs);
                if (selected == 0) continue;

                var selectedKeys = selector.selectedKeys();
                var iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    var key = iterator.next();
                    iterator.remove();
                    if (!key.isValid()) continue;

                    dispatchKey(key);
                }
            } catch (ClosedSelectorException e) {
                LOG.debug("Selector closed, exiting event loop");
                break;
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error in selector event loop", e);
                }
            }
        }
    }

    /**
     * Dispatches a ready {@link SelectionKey} in the selector thread.
     *
     * <h2>TCP read contract (fully synchronous, order-preserving, lossless)</h2>
     * <ol>
     *   <li>Any remainder in the persistent TCP buffer ({@code reg.tcpBuf}) — data the protocol
     *       did not consume in the previous cycle — is flushed first via {@code fireRead}.</li>
     *   <li>A new TCP read happens <b>only if</b> the previous flush was fully consumed. This is
     *       the backpressure gate: while the protocol is holding data, no new TCP data is pulled,
     *       so the socket-level backlog applies pressure to the sender.</li>
     *   <li>Unconsumed bytes after a flush remain in the buffer and are retried on the next
     *       read/write cycle. Nothing is cleared or discarded in this class — the buffer position
     *       is the consumption contract (the handler advances {@code position} as it consumes).</li>
     * </ol>
     *
     * <h2>Responsibility separation</h2>
     * <p>TCP lifecycle (finishConnect, reads, EOF detection, key cancellation) belongs here.
     * TCP EOF is a <b>transport error</b>: {@code fireDisconnect} is fired so the handler can
     * process whatever data the protocol has buffered, then tear down. This class never
     * interprets data contents and never blocks on protocol state.</p>
     */
    private void dispatchKey(SelectionKey key) {
        var attachment = key.attachment();
        if (attachment instanceof ServerChannelRegistration srvReg) {
            if (key.isAcceptable()) {
                processingPool.submit(() -> handleAccept(srvReg));
            }
            return;
        }
        if (!(attachment instanceof ChannelRegistration reg)) return;

        var channel = reg.channel;
        var pipeline = reg.pipeline;

        // OP_CONNECT: TCP lifecycle — finish the connection, switch to READ|WRITE, notify pipeline.
        if (key.isConnectable()) {
            try {
                ((TcpDataChannel) channel).getSocketChannel().finishConnect();
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                pipeline.fireConnect(channel);
            } catch (IOException e) {
                LOG.error("finishConnect failed for {}", channel, e);
                key.cancel();
                pipeline.fireDisconnect(channel);
            }
        }

        if (key.isReadable()) {
            if (channel instanceof TcpDataChannel) {
                var buf = reg.tcpBuf;
                try {
                    // 1. Flush remainder from the previous cycle first (relative-position buffer).
                    //    A flag (not position) marks pending data: a handler that consumed zero
                    //    bytes leaves position at 0, indistinguishable from a fresh buffer.
                    if (reg.tcpBufHasData) {
                        pipeline.fireRead(channel, buf);
                        reg.tcpBufHasData = buf.hasRemaining(); // handler may consume 0..all bytes
                        if (reg.tcpBufHasData) {
                            return; // protocol still holding data — no new TCP read (backpressure)
                        }
                    }
                    // 2. Read new TCP data only when the previous flush was fully consumed
                    buf.clear();
                    int n = channel.read(buf);
                    if (n < 0) {
                        // TCP closed — transport error. We only reach the read with tcpBufHasData
                        // false, i.e. every byte previously read has been delivered to the pipeline.
                        // Data buffered deeper in the protocol layer is its responsibility to
                        // process after the disconnect event.
                        LOG.debug("TCP EOF on {}", channel);
                        key.cancel();
                        pipeline.fireDisconnect(channel);
                        return;
                    }
                    if (n > 0) {
                        buf.flip();
                        pipeline.fireRead(channel, buf);
                        // Any remainder stays in tcpBuf — flushed on the next cycle (backpressure).
                        reg.tcpBufHasData = buf.hasRemaining();
                    }
                } catch (IOException e) {
                    LOG.debug("Read error on {}: {}", channel, e.getMessage());
                    key.cancel();
                    try { channel.close(); } catch (IOException ignored) {}
                    pipeline.fireDisconnect(channel);
                }
            } else {
                // Non-TCP (e.g. UDP): fresh buffer per read — datagram boundary semantics,
                // no remainder carried across cycles.
                var buf = ByteBuffer.allocate(bufferSize);
                try {
                    int n = channel.read(buf);
                    if (n < 0) {
                        key.cancel();
                        pipeline.fireDisconnect(channel);
                        return;
                    }
                    if (n > 0) {
                        buf.flip();
                        pipeline.fireRead(channel, buf);
                    }
                } catch (IOException e) {
                    key.cancel();
                    try { channel.close(); } catch (IOException ignored) {}
                    pipeline.fireDisconnect(channel);
                }
            }
        }

        // OP_WRITE: first retry flushing any TCP remainder the protocol may have consumed since
        // the last attempt (the protocol drains its ring buffer while producing writes),
        // then fire the write async — the handler drains its outbound queue in order.
        if (key.isWritable()) {
            if (channel instanceof TcpDataChannel && reg.tcpBufHasData) {
                pipeline.fireRead(channel, reg.tcpBuf); // retry flush; no new TCP read — not readable
                reg.tcpBufHasData = reg.tcpBuf.hasRemaining();
            }
            processingPool.submit(() -> pipeline.fireWrite(channel));
        }
    }
    private void handleAccept(ServerChannelRegistration srvReg) {
        try {
            var clientChannel = srvReg.serverChannel().accept();
            if (clientChannel != null) {
                var pipeline = srvReg.pipeline();
                var reg = new ChannelRegistration(clientChannel, pipeline, bufferSize);
                var key = clientChannel.getSocketChannel().register(selector, SelectionKey.OP_READ, reg);
                clientChannel.setSelectionKey(key);
                pipeline.fireConnect(clientChannel);
                LOG.debug("Accepted client connection");
            }
        } catch (IOException e) {
            LOG.warn("Error accepting connection", e);
        }
    }

    @Override
    public void close() {
        stopEventLoop();
        channelsByService.values().forEach(channel -> {
            try {
                if (channel.isOpen()) channel.close();
            } catch (IOException e) {
                LOG.warn("Error closing channel", e);
            }
        });
        channelsByService.clear();
        serverChannelsByService.values().forEach(channel -> {
            try {
                if (channel.isOpen()) channel.close();
            } catch (IOException e) {
                LOG.warn("Error closing server channel", e);
            }
        });
        serverChannelsByService.clear();
        pipelinesByService.clear();
        connectionPool.close();
        processingPool.close();
        try {
            selector.close();
        } catch (IOException e) {
            LOG.warn("Error closing selector", e);
        }
        super.close();
    }

    public Selector getSelector() {
        return selector;
    }

    /**
     * Per-channel registration state. All fields are touched only by the selector thread
     * (tcpBuf) or at registration time; no synchronization needed.
     */
    static class ChannelRegistration {
        final DataChannel channel;
        final ChannelPipeline pipeline;
        final ByteBuffer tcpBuf;   // persistent TCP read buffer — selector thread only
        boolean tcpBufHasData;     // true when tcpBuf holds unconsumed TCP data

        ChannelRegistration(DataChannel channel, ChannelPipeline pipeline, int bufferSize) {
            this.channel = channel;
            this.pipeline = pipeline;
            this.tcpBuf = ByteBuffer.allocate(bufferSize);
        }
    }
    record ServerChannelRegistration(ServerDataChannel serverChannel, ChannelPipeline pipeline) {}
}
