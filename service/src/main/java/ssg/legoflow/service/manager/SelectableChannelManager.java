package ssg.legoflow.service.manager;

import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.ServerDataChannel;
import ssg.legoflow.service.channel.TcpDataChannel;
import ssg.legoflow.service.channel.UdpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        var name = service.getDescriptor().name();
        channelsByService.put(name, channel);
        var pipeline = pipelinesByService.computeIfAbsent(name, _ -> new ChannelPipeline());

        // Register with the selector
        try {
            if (channel instanceof TcpDataChannel tcp) {
                int ops = SelectionKey.OP_READ | SelectionKey.OP_CONNECT;
                var key = tcp.getSocketChannel().register(selector, ops,
                        new ChannelRegistration(channel, pipeline));
                tcp.setSelectionKey(key);
                LOG.debug("Registered TCP channel for service: {} with ops={}", name, ops);
            } else if (channel instanceof UdpDataChannel udp) {
                var key = udp.getDatagramChannel().register(selector, SelectionKey.OP_READ,
                        new ChannelRegistration(channel, pipeline));
                udp.setSelectionKey(key);
            }
        } catch (IOException e) {
            channelsByService.remove(name);
            pipelinesByService.remove(name);
            throw new UncheckedIOException("Failed to register channel for service: " + name, e);
        }
        LOG.debug("Registered channel for service: {}", name);
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
     * Dispatches a ready {@link SelectionKey} to a {@link ProcessingThread}.
     *
     * <p><b>Stream contract:</b> each readable event results in exactly one
     * {@link ProcessingThread#processReadable()} call, which reads a single
     * chunk of bytes and forwards it to the pipeline. The pipeline's codecs are
     * responsible for accumulating partial data internally and emitting complete
     * protocol messages when enough bytes have arrived. This manager does not
     * buffer or coalesce reads across selector wake-ups — doing so would couple
     * transport-level I/O to protocol framing, which is the codec's concern.
     */
    private void dispatchKey(SelectionKey key) {
        var attachment = key.attachment();
        if (attachment instanceof ServerChannelRegistration srvReg) {
            // Server socket — accept new connections
            if (key.isAcceptable()) {
                processingPool.submit(() -> handleAccept(srvReg));
            }
            return;
        }
        if (!(attachment instanceof ChannelRegistration reg)) return;

        var channel = reg.channel();
        var pipeline = reg.pipeline();
        var processingThread = new ProcessingThread(channel, pipeline, bufferSize);

        if (key.isReadable()) {
            processingPool.submit(processingThread::processReadable);
        }
        if (key.isWritable()) {
            processingPool.submit(processingThread::processWritable);
        }
        if (key.isConnectable()) {
            connectionPool.submit(processingThread::processConnectable);
        }
    }

    private void handleAccept(ServerChannelRegistration srvReg) {
        try {
            var clientChannel = srvReg.serverChannel().accept();
            if (clientChannel != null) {
                var pipeline = srvReg.pipeline();
                pipeline.fireConnect(clientChannel);
                // Register the accepted client channel with the selector for read events
                var key = clientChannel.getSocketChannel().register(selector, SelectionKey.OP_READ,
                        new ChannelRegistration(clientChannel, pipeline));
                clientChannel.setSelectionKey(key);
                LOG.debug("Accepted client connection on server channel");
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

    record ChannelRegistration(DataChannel channel, ChannelPipeline pipeline) {}
    record ServerChannelRegistration(ServerDataChannel serverChannel, ChannelPipeline pipeline) {}
}
