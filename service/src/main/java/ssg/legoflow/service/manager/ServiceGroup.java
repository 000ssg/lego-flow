package ssg.legoflow.service.manager;

import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.UdpDataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-selector I/O event loop for managing multiple NIO selectors.
 *
 * <p>Distributes channels across multiple selectors for parallel I/O processing.
 * Selector[0] is the connector selector handling OP_ACCEPT and OP_CONNECT events,
 * while selectors[1..N] are data selectors handling OP_READ and OP_WRITE events.
 * Channels are distributed across data selectors using round-robin assignment.
 *
 * <p>Each selector runs in its own virtual thread. Key dispatch creates new
 * {@link ProcessingThread} instances on a virtual-thread-per-task executor.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (var group = ServiceGroup.builder("my-group")
 *         .dataSelectorCount(2)
 *         .bufferSize(8192)
 *         .selectTimeoutMs(100)
 *         .build()) {
 *     group.start();
 *     // register channels, process I/O...
 *     group.stop();
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class ServiceGroup implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceGroup.class);
    private static final int DEFAULT_DATA_SELECTOR_COUNT = 2;
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final long DEFAULT_SELECT_TIMEOUT_MS = 100;

    private final String name;
    private final int dataSelectorCount;
    private final Selector connectorSelector;
    private final Selector[] dataSelectors;
    private final AtomicInteger nextSelectorIndex = new AtomicInteger(0);
    private final ServiceGroupStatistics statistics;
    private final int bufferSize;
    private final long selectTimeoutMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> selectorThreads = new ArrayList<>();
    private final ExecutorService processingPool;
    private final CopyOnWriteArrayList<DataChannel> registeredChannels = new CopyOnWriteArrayList<>();

    private ServiceGroup(String name, int dataSelectorCount, int bufferSize, long selectTimeoutMs) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.dataSelectorCount = dataSelectorCount;
        this.bufferSize = bufferSize;
        this.selectTimeoutMs = selectTimeoutMs;
        this.statistics = new ServiceGroupStatistics(dataSelectorCount + 1);
        this.processingPool = Executors.newVirtualThreadPerTaskExecutor();

        try {
            this.connectorSelector = Selector.open();
            this.dataSelectors = new Selector[dataSelectorCount];
            for (int i = 0; i < dataSelectorCount; i++) {
                dataSelectors[i] = Selector.open();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open selectors for group: " + name, e);
        }
    }

    /**
     * Creates a new builder for a {@code ServiceGroup}.
     *
     * @param name the group name
     * @return a new builder
     * @since 1.0.0
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Starts the event loop by creating virtual threads for each selector.
     *
     * <p>Creates N+1 virtual threads: one for the connector selector and one
     * for each data selector.
     *
     * @since 1.0.0
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // Connector selector thread (index 0)
            var connThread = Thread.ofVirtual()
                    .name(name + "-connector-0")
                    .start(() -> selectorLoop(connectorSelector, 0));
            selectorThreads.add(connThread);

            // Data selector threads (index 1..N)
            for (int i = 0; i < dataSelectorCount; i++) {
                final int selectorIndex = i + 1;
                var thread = Thread.ofVirtual()
                        .name(name + "-data-" + selectorIndex)
                        .start(() -> selectorLoop(dataSelectors[selectorIndex - 1], selectorIndex));
                selectorThreads.add(thread);
            }
            LOG.info("ServiceGroup '{}' started with {} data selectors", name, dataSelectorCount);
        }
    }

    /**
     * Stops the event loop by setting the running flag to false and waking all selectors.
     *
     * @since 1.0.0
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            connectorSelector.wakeup();
            for (var sel : dataSelectors) {
                sel.wakeup();
            }
            for (var thread : selectorThreads) {
                try {
                    thread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            selectorThreads.clear();
            LOG.info("ServiceGroup '{}' stopped", name);
        }
    }

    /**
     * Returns whether the event loop is running.
     *
     * @return {@code true} if the event loop is active
     * @since 1.0.0
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Registers a channel with the connector selector for OP_ACCEPT or OP_CONNECT.
     *
     * @param selectableChannel the NIO channel to register
     * @param ops               the interest operations (OP_ACCEPT or OP_CONNECT)
     * @param channel           the data channel
     * @param pipeline          the channel pipeline
     * @return the selection key
     * @throws IOException if registration fails
     * @since 1.0.0
     */
    public SelectionKey registerConnector(SelectableChannel selectableChannel, int ops,
                                          DataChannel channel, ChannelPipeline pipeline) throws IOException {
        var key = selectableChannel.register(connectorSelector, ops, new ChannelRegistration(channel, pipeline));
        registeredChannels.add(channel);
        connectorSelector.wakeup();
        LOG.debug("Registered connector channel on group '{}': ops={}", name, ops);
        return key;
    }

    /**
     * Registers a channel with the next data selector for OP_READ and/or OP_WRITE.
     *
     * <p>Uses round-robin distribution across data selectors.
     *
     * @param selectableChannel the NIO channel to register
     * @param ops               the interest operations (OP_READ, OP_WRITE, or both)
     * @param channel           the data channel
     * @param pipeline          the channel pipeline
     * @return the selection key
     * @throws IOException if registration fails
     * @since 1.0.0
     */
    public SelectionKey registerData(SelectableChannel selectableChannel, int ops,
                                     DataChannel channel, ChannelPipeline pipeline) throws IOException {
        int idx = nextSelectorIndex.getAndUpdate(i -> (i + 1) % dataSelectorCount);
        var selector = dataSelectors[idx];
        var key = selectableChannel.register(selector, ops, new ChannelRegistration(channel, pipeline));
        registeredChannels.add(channel);
        selector.wakeup();
        LOG.debug("Registered data channel on group '{}': selector={}, ops={}", name, idx, ops);
        return key;
    }

    /**
     * Returns the index of the data selector that will be used for the next registration.
     *
     * @return the next data selector index (0-based within data selectors)
     * @since 1.0.0
     */
    public int getNextDataSelectorIndex() {
        return nextSelectorIndex.get() % dataSelectorCount;
    }

    /**
     * Returns the connector selector (index 0).
     *
     * @return the connector selector
     * @since 1.0.0
     */
    public Selector getConnectorSelector() {
        return connectorSelector;
    }

    /**
     * Returns the data selector at the given index.
     *
     * @param index the data selector index (0-based)
     * @return the data selector
     * @throws IndexOutOfBoundsException if index is out of range
     * @since 1.0.0
     */
    public Selector getDataSelector(int index) {
        return dataSelectors[index];
    }

    /**
     * Returns the statistics tracker for this group.
     *
     * @return the statistics
     * @since 1.0.0
     */
    public ServiceGroupStatistics getStatistics() {
        return statistics;
    }

    /**
     * Returns the group name.
     *
     * @return the name
     * @since 1.0.0
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the number of data selectors.
     *
     * @return the data selector count
     * @since 1.0.0
     */
    public int getDataSelectorCount() {
        return dataSelectorCount;
    }

    /**
     * Returns the buffer size used for processing.
     *
     * @return the buffer size in bytes
     * @since 1.0.0
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Returns the list of currently registered channels.
     *
     * @return an unmodifiable view of registered channels
     * @since 1.0.0
     */
    public List<DataChannel> getRegisteredChannels() {
        return List.copyOf(registeredChannels);
    }

    private void selectorLoop(Selector selector, int selectorIndex) {
        statistics.setSelectorIndex(selectorIndex);
        while (running.get()) {
            try {
                long cycleStart = System.nanoTime();
                int selected = selector.select(selectTimeoutMs);
                if (selected == 0) continue;

                var it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    var key = it.next();
                    it.remove();
                    if (!key.isValid()) continue;
                    dispatchKey(key, selectorIndex);
                }
                statistics.addSelectorDuration(selectorIndex, System.nanoTime() - cycleStart);
            } catch (ClosedSelectorException e) {
                LOG.debug("Selector closed in group '{}', index {}", name, selectorIndex);
                break;
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error in selector loop for group '{}', index {}", name, selectorIndex, e);
                }
            }
        }
    }

    private void dispatchKey(SelectionKey key, int selectorIndex) {
        var attachment = key.attachment();
        if (!(attachment instanceof ChannelRegistration reg)) return;

        var channel = reg.channel();
        var pipeline = reg.pipeline();

        if (key.isReadable()) {
            long started = System.nanoTime();
            if (channel instanceof UdpDataChannel udpChannel) {
                // UDP channels need special handling: use receiveDatagram() to preserve
                // sender address, then dispatch via fireDatagram(). Using ProcessingThread
                // would call channel.read() (consuming the datagram) then DatagramHandler.onRead()
                // would call receiveDatagram() again on an already-empty channel.
                processingPool.submit(() -> {
                    try {
                        var packet = udpChannel.receiveDatagram();
                        if (packet != null) {
                            pipeline.fireDatagram(udpChannel, packet);
                        }
                    } catch (Exception e) {
                        LOG.error("Error processing UDP read in group '{}'", name, e);
                        pipeline.fireError(channel, e);
                    }
                    statistics.addKeyProcessed(ServiceGroupStatistics.READ, System.nanoTime() - started);
                });
            } else {
                var processingThread = new ProcessingThread(channel, pipeline, bufferSize);
                processingPool.submit(() -> {
                    processingThread.processReadable();
                    statistics.addKeyProcessed(ServiceGroupStatistics.READ, System.nanoTime() - started);
                });
            }
        }
        if (key.isWritable()) {
            long started = System.nanoTime();
            var processingThread = new ProcessingThread(channel, pipeline, bufferSize);
            processingPool.submit(() -> {
                processingThread.processWritable();
                statistics.addKeyProcessed(ServiceGroupStatistics.WRITE, System.nanoTime() - started);
            });
        }
        if (key.isAcceptable()) {
            long started = System.nanoTime();
            processingPool.submit(() -> {
                // Accept handled by connector
                statistics.addKeyProcessed(ServiceGroupStatistics.ACCEPT, System.nanoTime() - started);
            });
        }
        if (key.isConnectable()) {
            long started = System.nanoTime();
            var processingThread = new ProcessingThread(channel, pipeline, bufferSize);
            processingPool.submit(() -> {
                processingThread.processConnectable();
                statistics.addKeyProcessed(ServiceGroupStatistics.CONNECT, System.nanoTime() - started);
            });
        }
    }

    /**
     * Closes this service group, stopping the event loop and releasing all resources.
     *
     * @since 1.0.0
     */
    @Override
    public void close() {
        stop();
        registeredChannels.forEach(ch -> {
            try {
                if (ch.isOpen()) ch.close();
            } catch (IOException e) {
                LOG.warn("Error closing channel in group '{}'", name, e);
            }
        });
        registeredChannels.clear();
        processingPool.close();
        try {
            connectorSelector.close();
        } catch (IOException e) {
            LOG.warn("Error closing connector selector in group '{}'", name, e);
        }
        for (var sel : dataSelectors) {
            try {
                sel.close();
            } catch (IOException e) {
                LOG.warn("Error closing data selector in group '{}'", name, e);
            }
        }
    }

    /**
     * Channel registration record associating a {@link DataChannel} with its {@link ChannelPipeline}.
     *
     * @param channel  the data channel
     * @param pipeline the channel pipeline
     * @since 1.0.0
     */
    public record ChannelRegistration(DataChannel channel, ChannelPipeline pipeline) {}

    /**
     * Builder for {@link ServiceGroup}.
     *
     * @since 1.0.0
     */
    public static class Builder {

        private final String name;
        private int dataSelectorCount = DEFAULT_DATA_SELECTOR_COUNT;
        private int bufferSize = DEFAULT_BUFFER_SIZE;
        private long selectTimeoutMs = DEFAULT_SELECT_TIMEOUT_MS;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Sets the number of data selectors.
         *
         * @param count the number of data selectors (must be positive)
         * @return this builder
         * @since 1.0.0
         */
        public Builder dataSelectorCount(int count) {
            if (count <= 0) throw new IllegalArgumentException("dataSelectorCount must be positive: " + count);
            this.dataSelectorCount = count;
            return this;
        }

        /**
         * Sets the buffer size for read operations.
         *
         * @param size the buffer size in bytes (must be positive)
         * @return this builder
         * @since 1.0.0
         */
        public Builder bufferSize(int size) {
            if (size <= 0) throw new IllegalArgumentException("bufferSize must be positive: " + size);
            this.bufferSize = size;
            return this;
        }

        /**
         * Sets the select timeout in milliseconds.
         *
         * @param timeoutMs the timeout in milliseconds (must be positive)
         * @return this builder
         * @since 1.0.0
         */
        public Builder selectTimeoutMs(long timeoutMs) {
            if (timeoutMs <= 0) throw new IllegalArgumentException("selectTimeoutMs must be positive: " + timeoutMs);
            this.selectTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Builds and returns a new {@link ServiceGroup}.
         *
         * @return the new service group
         * @since 1.0.0
         */
        public ServiceGroup build() {
            return new ServiceGroup(name, dataSelectorCount, bufferSize, selectTimeoutMs);
        }
    }
}
