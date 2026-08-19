package ssg.legoflow.upnp.ssdp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.MulticastDataChannel;
import ssg.legoflow.service.manager.ServiceGroup;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * SSDP discovery service for UPnP device discovery and advertisement.
 *
 * <p>This service manages SSDP multicast communication on 239.255.255.250:1900,
 * providing device discovery via M-SEARCH requests and device advertisement
 * via NOTIFY messages. It maintains a cache of discovered devices with
 * automatic expiry based on CACHE-CONTROL max-age values.
 *
 * <p>This class is thread-safe. All internal state is managed through concurrent
 * data structures, and I/O operations use virtual threads.
 *
 * @since 0.1.0
 */
public class SsdpService implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(SsdpService.class);
    private static final int RECEIVE_BUFFER_SIZE = 4096;

    private final DatagramChannel multicastChannel;
    private final NetworkInterface networkInterface;
    private final List<SsdpListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, CachedDevice> deviceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final int multicastTtl;
    private final ServiceGroup serviceGroup;
    private volatile ScheduledFuture<?> expiryTask;
    private volatile Thread receiveThread;

    /**
     * A cached device entry with expiry tracking.
     *
     * @param message   the last SSDP message from this device
     * @param location  the device description URL
     * @param expiresAt when this cache entry expires
     * @since 0.1.0
     */
    record CachedDevice(SsdpMessage message, String location, Instant expiresAt) {

        /**
         * Returns whether this cache entry has expired.
         *
         * @return {@code true} if the entry has expired
         * @since 0.1.0
         */
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /** Default multicast TTL as recommended by UDA. @since 0.1.0 */
    public static final int DEFAULT_MULTICAST_TTL = 4;

    /**
     * Creates a new {@code SsdpService} using the specified network interface
     * with the default multicast TTL of 4.
     *
     * @param networkInterface the network interface for multicast communication
     * @throws IOException          if the multicast channel cannot be opened or configured
     * @throws NullPointerException if {@code networkInterface} is {@code null}
     * @since 0.1.0
     */
    public SsdpService(NetworkInterface networkInterface) throws IOException {
        this(networkInterface, DEFAULT_MULTICAST_TTL);
    }

    /**
     * Creates a new {@code SsdpService} using the specified network interface
     * and multicast TTL value.
     *
     * @param networkInterface the network interface for multicast communication
     * @param multicastTtl     the multicast Time-To-Live (number of hops); must be between 1 and 255
     * @throws IOException              if the multicast channel cannot be opened or configured
     * @throws NullPointerException     if {@code networkInterface} is {@code null}
     * @throws IllegalArgumentException if {@code multicastTtl} is out of the valid range
     * @since 0.1.0
     */
    public SsdpService(NetworkInterface networkInterface, int multicastTtl) throws IOException {
        this.networkInterface = Objects.requireNonNull(networkInterface, "networkInterface must not be null");
        if (multicastTtl < 1 || multicastTtl > 255) {
            throw new IllegalArgumentException("multicastTtl must be between 1 and 255: " + multicastTtl);
        }
        this.multicastTtl = multicastTtl;

        this.multicastChannel = DatagramChannel.open(StandardProtocolFamily.INET)
                .setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true)
                .setOption(java.net.StandardSocketOptions.SO_REUSEPORT, true)
                .setOption(java.net.StandardSocketOptions.IP_MULTICAST_TTL, multicastTtl)
                .setOption(java.net.StandardSocketOptions.IP_MULTICAST_LOOP, true)
                .setOption(java.net.StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
        this.multicastChannel.bind(new InetSocketAddress(SsdpConstants.MULTICAST_PORT));
        this.multicastChannel.join(SsdpConstants.getMulticastAddress(), networkInterface);

        this.serviceGroup = null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofVirtual().unstarted(r);
            t.setName("ssdp-scheduler");
            return t;
        });
    }

    /**
     * Creates a new {@code SsdpService} with a pre-configured datagram channel.
     *
     * <p>This constructor is primarily intended for testing, allowing injection
     * of a mock or pre-configured channel.
     *
     * @param multicastChannel the datagram channel to use for SSDP communication
     * @param networkInterface the network interface for multicast
     * @since 0.1.0
     */
    SsdpService(DatagramChannel multicastChannel, NetworkInterface networkInterface) {
        this.multicastChannel = Objects.requireNonNull(multicastChannel, "multicastChannel must not be null");
        this.networkInterface = Objects.requireNonNull(networkInterface, "networkInterface must not be null");
        this.multicastTtl = DEFAULT_MULTICAST_TTL;
        this.serviceGroup = null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofVirtual().unstarted(r);
            t.setName("ssdp-scheduler");
            return t;
        });
    }

    /**
     * Creates a new {@code SsdpService} that uses a {@link ServiceGroup} for I/O dispatch
     * instead of its own blocking receive loop.
     *
     * <p>The multicast channel is registered with the ServiceGroup's data selector,
     * and incoming datagrams are dispatched via an {@link SsdpChannelHandler} pipeline
     * handler. The ServiceGroup's lifecycle is managed externally — this service does
     * not start or stop it.
     *
     * <p>When {@link #start()} is called, the channel is registered with the ServiceGroup
     * and no blocking receive thread is created. When {@link #close()} is called, the
     * channel is unregistered but the ServiceGroup itself is not stopped.
     *
     * @param networkInterface the network interface for multicast communication
     * @param serviceGroup     the service group to use for I/O dispatch
     * @throws IOException          if the multicast channel cannot be opened or configured
     * @throws NullPointerException if any argument is {@code null}
     * @since 0.1.0
     */
    public SsdpService(NetworkInterface networkInterface, ServiceGroup serviceGroup) throws IOException {
        this(networkInterface, DEFAULT_MULTICAST_TTL, serviceGroup);
    }

    /**
     * Creates a new {@code SsdpService} with a custom multicast TTL that uses a
     * {@link ServiceGroup} for I/O dispatch.
     *
     * @param networkInterface the network interface for multicast communication
     * @param multicastTtl     the multicast Time-To-Live; must be between 1 and 255
     * @param serviceGroup     the service group to use for I/O dispatch
     * @throws IOException              if the multicast channel cannot be opened or configured
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code multicastTtl} is out of range
     * @since 0.1.0
     */
    public SsdpService(NetworkInterface networkInterface, int multicastTtl, ServiceGroup serviceGroup)
            throws IOException {
        this.networkInterface = Objects.requireNonNull(networkInterface, "networkInterface must not be null");
        this.serviceGroup = Objects.requireNonNull(serviceGroup, "serviceGroup must not be null");
        if (multicastTtl < 1 || multicastTtl > 255) {
            throw new IllegalArgumentException("multicastTtl must be between 1 and 255: " + multicastTtl);
        }
        this.multicastTtl = multicastTtl;

        this.multicastChannel = DatagramChannel.open(StandardProtocolFamily.INET)
                .setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true)
                .setOption(java.net.StandardSocketOptions.SO_REUSEPORT, true)
                .setOption(java.net.StandardSocketOptions.IP_MULTICAST_TTL, multicastTtl)
                .setOption(java.net.StandardSocketOptions.IP_MULTICAST_LOOP, true)
                .setOption(java.net.StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
        this.multicastChannel.bind(new InetSocketAddress(SsdpConstants.MULTICAST_PORT));
        this.multicastChannel.join(SsdpConstants.getMulticastAddress(), networkInterface);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofVirtual().unstarted(r);
            t.setName("ssdp-scheduler");
            return t;
        });
    }

    /**
     * Returns the configured multicast TTL.
     *
     * @return the multicast Time-To-Live value
     * @since 0.1.0
     */
    public int getMulticastTtl() {
        return multicastTtl;
    }

    /**
     * Returns the network interface this service is bound to.
     *
     * @return the network interface
     * @since 0.1.0
     */
    public NetworkInterface getNetworkInterface() {
        return networkInterface;
    }

    /**
     * Starts the SSDP service, beginning to listen for multicast messages
     * and scheduling cache expiry checks.
     *
     * @since 0.1.0
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            if (serviceGroup != null) {
                // Register with ServiceGroup — no blocking receive thread needed
                try {
                    var multicastDataChannel = new MulticastDataChannel(multicastChannel);
                    var pipeline = new ChannelPipeline();
                    pipeline.addLast(new SsdpChannelHandler(this));
                    serviceGroup.registerData(multicastChannel, SelectionKey.OP_READ,
                            multicastDataChannel, pipeline);
                    LOG.info("SSDP service started on interface {} via ServiceGroup '{}'",
                            networkInterface.getName(), serviceGroup.getName());
                } catch (IOException e) {
                    running.set(false);
                    throw new java.io.UncheckedIOException(
                            "Failed to register SSDP channel with ServiceGroup", e);
                }
            } else {
                // Standalone mode — blocking receive loop on virtual thread
                receiveThread = Thread.ofVirtual().name("ssdp-receiver").start(this::receiveLoop);
                LOG.info("SSDP service started on interface {}", networkInterface.getName());
            }
            expiryTask = scheduler.scheduleAtFixedRate(this::purgeExpiredDevices, 30, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Stops the SSDP service, halting message reception and cache maintenance.
     *
     * @since 0.1.0
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (receiveThread != null) {
                receiveThread.interrupt();
            }
            if (expiryTask != null) {
                expiryTask.cancel(false);
            }
            LOG.info("SSDP service stopped");
        }
    }

    /**
     * Adds a listener for SSDP discovery events.
     *
     * @param listener the listener to add; must not be {@code null}
     * @throws NullPointerException if {@code listener} is {@code null}
     * @since 0.1.0
     */
    public void addListener(SsdpListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously added SSDP listener.
     *
     * @param listener the listener to remove
     * @since 0.1.0
     */
    public void removeListener(SsdpListener listener) {
        listeners.remove(listener);
    }

    /**
     * Sends an M-SEARCH request for the specified search target and collects
     * responses asynchronously.
     *
     * @param searchTarget the search target (e.g., "ssdp:all", "upnp:rootdevice")
     * @return a future that completes with the list of search responses collected
     *         within the MX wait period
     * @throws NullPointerException if {@code searchTarget} is {@code null}
     * @since 0.1.0
     */
    public CompletableFuture<List<SsdpMessage>> search(String searchTarget) {
        return search(searchTarget, SsdpConstants.DEFAULT_MX);
    }

    /**
     * Sends an M-SEARCH request with a custom MX wait time.
     *
     * @param searchTarget the search target
     * @param mx           the maximum wait time in seconds
     * @return a future that completes with the collected search responses
     * @throws NullPointerException     if {@code searchTarget} is {@code null}
     * @throws IllegalArgumentException if {@code mx} is not positive
     * @since 0.1.0
     */
    public CompletableFuture<List<SsdpMessage>> search(String searchTarget, int mx) {
        var message = SsdpMessage.search(searchTarget, mx);
        var responses = new CopyOnWriteArrayList<SsdpMessage>();

        SsdpListener searchListener = event -> {
            if (event instanceof SsdpEvent.SearchResponse sr) {
                responses.add(sr.message());
            }
        };
        addListener(searchListener);

        return CompletableFuture.supplyAsync(() -> {
            try {
                sendMessage(message, SsdpConstants.getMulticastSocketAddress());
                Thread.sleep(Duration.ofSeconds(mx));
            } catch (java.net.NoRouteToHostException e) {
                LOG.error("M-SEARCH failed: multicast route not available on interface {}. "
                        + "If a VPN is active, run: sudo route add -net 224.0.0.0/4 -interface {}",
                        networkInterface.getName(), networkInterface.getName());
            } catch (IOException e) {
                LOG.error("Failed to send M-SEARCH request", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                removeListener(searchListener);
            }
            return List.copyOf(responses);
        }, runnable -> Thread.ofVirtual().name("ssdp-search").start(runnable));
    }

    /**
     * Sends an M-SEARCH request for all devices and services ({@code ssdp:all}).
     *
     * @return a future that completes with the collected search responses
     * @since 0.1.0
     */
    public CompletableFuture<List<SsdpMessage>> searchAll() {
        return search(SsdpConstants.ST_ALL);
    }

    /**
     * Sends an M-SEARCH request for root devices only ({@code upnp:rootdevice}).
     *
     * @return a future that completes with the collected search responses
     * @since 0.1.0
     */
    public CompletableFuture<List<SsdpMessage>> searchRootDevices() {
        return search(SsdpConstants.ST_ROOT_DEVICE);
    }

    /**
     * Sends an M-SEARCH request for a specific device or service type.
     *
     * @param deviceType the UPnP device type URN
     *                   (e.g., "urn:schemas-upnp-org:device:MediaServer:1")
     * @return a future that completes with the collected search responses
     * @throws NullPointerException if {@code deviceType} is {@code null}
     * @since 0.1.0
     */
    public CompletableFuture<List<SsdpMessage>> searchByType(String deviceType) {
        return search(deviceType);
    }

    /**
     * Sends a NOTIFY ssdp:alive advertisement for the given device.
     *
     * @param location the device description URL
     * @param nt       the notification type
     * @param usn      the unique service name
     * @param server   the server identification string
     * @param maxAge   the cache max-age in seconds
     * @throws IOException if sending fails
     * @since 0.1.0
     */
    public void advertise(String location, String nt, String usn, String server, int maxAge) throws IOException {
        var message = SsdpMessage.alive(location, nt, usn, server, maxAge);
        sendMessage(message, SsdpConstants.getMulticastSocketAddress());
        LOG.debug("Sent alive advertisement for USN: {}", usn);
    }

    /**
     * Sends a NOTIFY ssdp:byebye message for device departure.
     *
     * @param nt  the notification type
     * @param usn the unique service name
     * @throws IOException if sending fails
     * @since 0.1.0
     */
    public void sendByebye(String nt, String usn) throws IOException {
        var message = SsdpMessage.byebye(nt, usn);
        sendMessage(message, SsdpConstants.getMulticastSocketAddress());
        LOG.debug("Sent byebye for USN: {}", usn);
    }

    /**
     * Returns an unmodifiable snapshot of the current device cache.
     *
     * @return a map of USN to cached device entries
     * @since 0.1.0
     */
    public Map<String, CachedDevice> getDeviceCache() {
        return Map.copyOf(deviceCache);
    }

    /**
     * Returns whether the service is currently running.
     *
     * @return {@code true} if the service is active
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the ServiceGroup this service is registered with, or {@code null}
     * if operating in standalone mode with a blocking receive loop.
     *
     * @return the service group, or {@code null}
     * @since 0.1.0
     */
    public ServiceGroup getServiceGroup() {
        return serviceGroup;
    }

    @Override
    public void close() throws IOException {
        stop();
        scheduler.shutdownNow();
        multicastChannel.close();
        deviceCache.clear();
        LOG.info("SSDP service closed");
    }

    /**
     * Sends an SSDP message to the specified target address.
     *
     * <p>If sending fails with {@link java.net.NoRouteToHostException}, this typically
     * indicates that a VPN has captured the multicast route (224.0.0.0/4). The fix is
     * to add a multicast route for the LAN interface:
     * <pre>sudo route add -net 224.0.0.0/4 -interface en0</pre>
     *
     * @param message the SSDP message to send
     * @param target  the target socket address
     * @throws IOException if sending fails
     */
    void sendMessage(SsdpMessage message, SocketAddress target) throws IOException {
        var data = message.serialize().getBytes(StandardCharsets.UTF_8);
        try {
            multicastChannel.send(ByteBuffer.wrap(data), target);
        } catch (java.net.NoRouteToHostException e) {
            LOG.error("Cannot send multicast to {} on interface {}: {}. "
                            + "A VPN may have captured the multicast route (224.0.0.0/4). "
                            + "Fix with: sudo route add -net 224.0.0.0/4 -interface {}",
                    target, networkInterface.getName(), e.getMessage(), networkInterface.getName());
            throw e;
        }
    }

    /**
     * Processes a received SSDP message, updating the device cache and notifying listeners.
     *
     * @param message the received SSDP message
     */
    void processMessage(SsdpMessage message) {
        switch (message.type()) {
            case NOTIFY_ALIVE, NOTIFY_UPDATE -> handleAlive(message);
            case NOTIFY_BYEBYE -> handleByebye(message);
            case M_SEARCH_RESPONSE -> handleSearchResponse(message);
            case M_SEARCH -> LOG.trace("Received M-SEARCH from {}", message.source());
        }
    }

    private void handleAlive(SsdpMessage message) {
        var usn = message.usn().orElse(null);
        var location = message.location().orElse(null);
        if (usn == null || location == null) {
            LOG.debug("Ignoring alive message without USN or LOCATION");
            return;
        }

        var maxAge = message.maxAge();
        var expiresAt = Instant.now().plusSeconds(maxAge);
        var previous = deviceCache.put(usn, new CachedDevice(message, location, expiresAt));

        if (previous == null) {
            LOG.debug("Discovered device: USN={}, LOCATION={}", usn, location);
            notifyListeners(new SsdpEvent.DeviceDiscovered(message, usn, location));
        } else {
            LOG.trace("Refreshed device: USN={}", usn);
        }
    }

    private void handleByebye(SsdpMessage message) {
        var usn = message.usn().orElse(null);
        if (usn == null) {
            return;
        }

        var removed = deviceCache.remove(usn);
        var location = removed != null ? removed.location() : null;
        LOG.debug("Device departed: USN={}", usn);
        notifyListeners(new SsdpEvent.DeviceLost(usn, location));
    }

    private void handleSearchResponse(SsdpMessage message) {
        var usn = message.usn().orElse(null);
        var location = message.location().orElse(null);

        if (usn != null && location != null) {
            var maxAge = message.maxAge();
            var expiresAt = Instant.now().plusSeconds(maxAge);
            deviceCache.putIfAbsent(usn, new CachedDevice(message, location, expiresAt));
        }

        notifyListeners(new SsdpEvent.SearchResponse(message));
    }

    private void purgeExpiredDevices() {
        var expired = new ArrayList<String>();
        deviceCache.forEach((usn, cached) -> {
            if (cached.isExpired()) {
                expired.add(usn);
            }
        });
        for (var usn : expired) {
            var removed = deviceCache.remove(usn);
            if (removed != null) {
                LOG.debug("Cache expired for device: USN={}", usn);
                notifyListeners(new SsdpEvent.DeviceLost(usn, removed.location()));
            }
        }
    }

    private void notifyListeners(SsdpEvent event) {
        for (var listener : listeners) {
            try {
                listener.onSsdpEvent(event);
            } catch (Exception e) {
                LOG.warn("SSDP listener threw exception", e);
            }
        }
    }

    private void receiveLoop() {
        var buffer = ByteBuffer.allocate(RECEIVE_BUFFER_SIZE);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                buffer.clear();
                var sender = multicastChannel.receive(buffer);
                if (sender != null) {
                    buffer.flip();
                    var text = StandardCharsets.UTF_8.decode(buffer).toString();
                    var message = SsdpMessage.parse(text, sender);
                    processMessage(message);
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error receiving SSDP message", e);
                }
            } catch (Exception e) {
                if (running.get()) {
                    LOG.warn("Error processing SSDP message", e);
                }
            }
        }
    }
}
