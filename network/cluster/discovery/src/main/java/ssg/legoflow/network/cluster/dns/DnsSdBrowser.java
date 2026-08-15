package ssg.legoflow.network.cluster.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Browses for DNS-SD services on the local network.
 *
 * <p>Periodically queries for services of a given type and maintains
 * a cache of discovered instances. Emits events when services are
 * added or removed from the cache.
 *
 * <p>Per RFC 8305 §4, the browser:
 * <ul>
 *   <li>Queries for PTR records of the service type</li>
 *   <li>For each PTR, queries for SRV and TXT records</li>
 *   <li>For each SRV target, queries for A/AAAA records</li>
 *   <li>Caches results with TTL; refreshes at half-TTL</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class DnsSdBrowser implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DnsSdBrowser.class);

    private final MdnsQuerier querier;
    private final String serviceType;
    private final String domain;
    private final Duration refreshInterval;
    private final Map<String, DnsSdServiceRecord> cache = new ConcurrentHashMap<>();
    private final List<Consumer<DnsSdBrowserEvent>> listeners = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private volatile boolean browsing;

    /**
     * Creates a browser for the given service type and domain.
     *
     * @param serviceType    the service type (e.g. "_http._tcp")
     * @param domain         the domain (e.g. "local")
     * @param interfaceAddr  the network interface (null for default)
     * @throws java.net.SocketException if the mDNS socket cannot be created
     * @since 0.2.0
     */
    public DnsSdBrowser(String serviceType, String domain, InetAddress interfaceAddr)
            throws java.net.SocketException {
        this(serviceType, domain, Duration.ofSeconds(60), interfaceAddr);
    }

    /**
     * Creates a browser with custom refresh interval.
     *
     * @param serviceType    the service type
     * @param domain         the domain
     * @param refreshInterval interval between refresh queries
     * @param interfaceAddr  the network interface (null for default)
     * @throws java.net.SocketException if the mDNS socket cannot be created
     * @since 0.2.0
     */
    public DnsSdBrowser(String serviceType, String domain, Duration refreshInterval,
            InetAddress interfaceAddr) throws java.net.SocketException {
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(refreshInterval, "refreshInterval must not be null");

        this.serviceType = serviceType;
        this.domain = domain;
        this.refreshInterval = refreshInterval;
        this.querier = new MdnsQuerier(interfaceAddr);
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("mdns-browser-" + serviceType);
            return t;
        });

        // Wire up the querier listeners
        querier.onResolved(this::onResolved);
        querier.onRemoved(this::onRemoved);
    }

    /**
     * Starts browsing for services.
     *
     * @since 0.2.0
     */
    public void start() {
        if (browsing) return;
        browsing = true;

        querier.start();

        // Initial query
        querier.query(serviceType, domain);

        // Schedule periodic refresh
        scheduler.scheduleAtFixedRate(
                () -> querier.query(serviceType, domain),
                refreshInterval.toMillis(),
                refreshInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        LOG.debug("Started browsing for {} services", serviceType + "." + domain);
    }

    /**
     * Stops browsing.
     *
     * @since 0.2.0
     */
    public void stop() {
        if (!browsing) return;
        browsing = false;
        scheduler.shutdownNow();
        querier.stop();
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Returns a snapshot of currently known services.
     *
     * @return unmodifiable list of service records
     * @since 0.2.0
     */
    public List<DnsSdServiceRecord> services() {
        return Collections.unmodifiableList(new ArrayList<>(cache.values()));
    }

    /**
     * Registers a listener for browser events.
     *
     * @param listener the listener
     * @since 0.2.0
     */
    public void onEvent(Consumer<DnsSdBrowserEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    private void onResolved(DnsSdServiceRecord record) {
        DnsSdServiceRecord previous = cache.put(record.instanceFqdn(), record);
        DnsSdBrowserEvent event = new DnsSdBrowserEvent(
                DnsSdBrowserEvent.Type.ADDED, record, null);
        fireEvent(event);
    }

    private void onRemoved(String instanceFqdn) {
        DnsSdServiceRecord removed = cache.remove(instanceFqdn);
        if (removed != null) {
            DnsSdBrowserEvent event = new DnsSdBrowserEvent(
                    DnsSdBrowserEvent.Type.REMOVED, removed, null);
            fireEvent(event);
        }
    }

    private void fireEvent(DnsSdBrowserEvent event) {
        for (Consumer<DnsSdBrowserEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.warn("Browser listener error: {}", e.getMessage());
            }
        }
    }

    /**
     * Event emitted by the browser when services change.
     *
     * @param type    the event type
     * @param record  the affected service record
     * @param message optional detail message
     * @since 0.2.0
     */
    public record DnsSdBrowserEvent(Type type, DnsSdServiceRecord record, String message) {

        /** Event types. */
        public enum Type {
            /** A new service was discovered. */
            ADDED,
            /** A service was removed (TTL expired or goodbye). */
            REMOVED
        }
    }

    /**
     * Returns whether browsing is currently active.
     */
    public boolean isBrowsing() {
        return browsing;
    }
}
