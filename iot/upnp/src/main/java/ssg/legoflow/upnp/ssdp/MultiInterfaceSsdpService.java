package ssg.legoflow.upnp.ssdp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * SSDP discovery service that operates across multiple network interfaces simultaneously.
 *
 * <p>This service manages one {@link SsdpService} per network interface, aggregating
 * discovery events and device caches from all interfaces. This enables multi-homed
 * hosts (e.g., a device with both Wi-Fi and Ethernet) to discover UPnP devices on
 * all connected subnets.
 *
 * <p>Listeners registered on this service receive events from all underlying
 * per-interface services.
 *
 * @since 0.1.0
 */
public class MultiInterfaceSsdpService implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(MultiInterfaceSsdpService.class);

    private final List<SsdpService> services = new CopyOnWriteArrayList<>();
    private final List<SsdpListener> listeners = new CopyOnWriteArrayList<>();
    private final int multicastTtl;
    private volatile boolean running;

    /**
     * Creates a new multi-interface SSDP service with the default multicast TTL.
     *
     * @since 0.1.0
     */
    public MultiInterfaceSsdpService() {
        this(SsdpService.DEFAULT_MULTICAST_TTL);
    }

    /**
     * Creates a new multi-interface SSDP service with the specified multicast TTL.
     *
     * @param multicastTtl the multicast Time-To-Live for all interfaces
     * @throws IllegalArgumentException if {@code multicastTtl} is out of range
     * @since 0.1.0
     */
    public MultiInterfaceSsdpService(int multicastTtl) {
        if (multicastTtl < 1 || multicastTtl > 255) {
            throw new IllegalArgumentException("multicastTtl must be between 1 and 255: " + multicastTtl);
        }
        this.multicastTtl = multicastTtl;
    }

    /**
     * Adds a network interface to this service, creating an underlying {@link SsdpService}
     * for it.
     *
     * @param networkInterface the network interface to add
     * @throws IOException          if the SSDP service for this interface cannot be created
     * @throws NullPointerException if {@code networkInterface} is {@code null}
     * @since 0.1.0
     */
    public void addInterface(NetworkInterface networkInterface) throws IOException {
        Objects.requireNonNull(networkInterface, "networkInterface must not be null");
        var service = new SsdpService(networkInterface, multicastTtl);
        // Forward events from the per-interface service to our aggregate listeners
        service.addListener(event -> {
            for (var listener : listeners) {
                try {
                    listener.onSsdpEvent(event);
                } catch (Exception e) {
                    LOG.warn("Aggregate SSDP listener threw exception", e);
                }
            }
        });
        services.add(service);
        LOG.info("Added SSDP interface: {}", networkInterface.getName());
    }

    /**
     * Starts SSDP discovery on all registered interfaces.
     *
     * @since 0.1.0
     */
    public void start() {
        running = true;
        for (var service : services) {
            service.start();
        }
        LOG.info("Multi-interface SSDP service started on {} interfaces", services.size());
    }

    /**
     * Stops SSDP discovery on all interfaces.
     *
     * @since 0.1.0
     */
    public void stop() {
        running = false;
        for (var service : services) {
            service.stop();
        }
        LOG.info("Multi-interface SSDP service stopped");
    }

    /**
     * Sends an M-SEARCH for all devices on all interfaces.
     *
     * @return a future that completes with all collected responses from all interfaces
     * @since 0.1.0
     */
    public CompletableFuture<List<SsdpMessage>> searchAll() {
        var futures = services.stream()
                .map(SsdpService::searchAll)
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    var combined = new ArrayList<SsdpMessage>();
                    for (var future : futures) {
                        combined.addAll(future.join());
                    }
                    return combined;
                });
    }

    /**
     * Sends a NOTIFY ssdp:alive advertisement on all interfaces.
     *
     * @param location the device description URL
     * @param nt       the notification type
     * @param usn      the unique service name
     * @param server   the server identification string
     * @param maxAge   the cache max-age in seconds
     * @throws IOException if sending fails on any interface
     * @since 0.1.0
     */
    public void advertise(String location, String nt, String usn, String server, int maxAge) throws IOException {
        for (var service : services) {
            service.advertise(location, nt, usn, server, maxAge);
        }
    }

    /**
     * Sends a NOTIFY ssdp:byebye on all interfaces.
     *
     * @param nt  the notification type
     * @param usn the unique service name
     * @throws IOException if sending fails on any interface
     * @since 0.1.0
     */
    public void sendByebye(String nt, String usn) throws IOException {
        for (var service : services) {
            service.sendByebye(nt, usn);
        }
    }

    /**
     * Adds a listener that receives events from all interfaces.
     *
     * @param listener the listener to add
     * @since 0.1.0
     */
    public void addListener(SsdpListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     * @since 0.1.0
     */
    public void removeListener(SsdpListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns an unmodifiable view of the underlying per-interface services.
     *
     * @return the list of per-interface SSDP services
     * @since 0.1.0
     */
    public List<SsdpService> getServices() {
        return Collections.unmodifiableList(services);
    }

    /**
     * Returns whether the service is currently running.
     *
     * @return {@code true} if running
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() throws IOException {
        stop();
        for (var service : services) {
            service.close();
        }
        services.clear();
        LOG.info("Multi-interface SSDP service closed");
    }
}
