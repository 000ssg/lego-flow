package ssg.legoflow.service.manager;

import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
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
        pipelinesByService.computeIfAbsent(name, _ -> new ChannelPipeline());
        LOG.debug("Registered channel for service: {}", name);
    }

    public void unregisterChannel(Service<?, ?> service) {
        var name = service.getDescriptor().name();
        var channel = channelsByService.remove(name);
        pipelinesByService.remove(name);
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
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
}
