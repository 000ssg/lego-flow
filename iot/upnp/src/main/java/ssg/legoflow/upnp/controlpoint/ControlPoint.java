package ssg.legoflow.upnp.controlpoint;

import ssg.legoflow.upnp.device.DeviceDescription;
import ssg.legoflow.upnp.mediarenderer.MediaRendererDevice;
import ssg.legoflow.upnp.mediaserver.MediaServerDevice;
import ssg.legoflow.upnp.ssdp.MultiInterfaceSsdpService;
import ssg.legoflow.upnp.ssdp.SsdpEvent;
import ssg.legoflow.upnp.ssdp.SsdpMessage;
import ssg.legoflow.upnp.ssdp.SsdpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * UPnP Control Point for discovering and controlling devices on the local network.
 *
 * <p>Integrates with {@link SsdpService} to perform real network device discovery
 * via SSDP multicast M-SEARCH and NOTIFY messages. When a device is discovered,
 * the control point fetches its description XML over HTTP, parses it with
 * {@link DeviceDescription#parseXml(String)}, classifies the device by its type URN,
 * and creates the appropriate proxy object ({@link MediaServerProxy},
 * {@link MediaRendererProxy}, or {@link DeviceProxy}).
 *
 * <p>Backward compatibility is preserved: in-process devices registered via
 * {@link #registerLocalServer(MediaServerDevice)} and
 * {@link #registerLocalRenderer(MediaRendererDevice)} continue to work alongside
 * network-discovered devices.
 *
 * <p>Device description fetches run on virtual threads so that the SSDP listener
 * callback is never blocked by HTTP I/O.
 *
 * <p>This class implements {@link AutoCloseable}; calling {@link #close()} stops
 * the control point and releases all resources including the underlying
 * {@link SsdpService} and {@link HttpClient}.
 *
 * @since 0.1.0
 */
public class ControlPoint implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ControlPoint.class);

    /**
     * Timeout for HTTP GET requests when fetching device description XML.
     */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final Map<String, DeviceProxy> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, MediaServerProxy> serverCache = new ConcurrentHashMap<>();
    private final Map<String, MediaRendererProxy> rendererCache = new ConcurrentHashMap<>();
    private final Map<String, FailedDevice> failedDeviceCache = new ConcurrentHashMap<>();
    private final Set<String> pendingFetches = ConcurrentHashMap.newKeySet();
    private final List<DeviceListener> listeners = new CopyOnWriteArrayList<>();

    private final List<NetworkInterface> networkInterfaces;
    private final HttpClient httpClient;
    private final UpnpMessageLog messageLog = new UpnpMessageLog();

    private volatile SsdpService ssdpService;
    private volatile MultiInterfaceSsdpService multiSsdpService;
    private volatile boolean running;

    /**
     * Creates a new control point without a specific network interface.
     *
     * <p>SSDP discovery will not be available until a network interface is provided
     * via the {@link #ControlPoint(NetworkInterface)} constructor. In-process device
     * registration via {@link #registerLocalServer(MediaServerDevice)} and
     * {@link #registerLocalRenderer(MediaRendererDevice)} still works.
     *
     * @since 0.1.0
     */
    public ControlPoint() {
        this.networkInterfaces = List.of();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .executor(Runnable::run)
                .build();
    }

    /**
     * Creates a new control point bound to the specified network interface.
     *
     * <p>On {@link #start()}, an {@link SsdpService} will be created on this
     * interface to discover UPnP devices via multicast SSDP.
     *
     * @param networkInterface the network interface for SSDP multicast communication
     * @throws NullPointerException if {@code networkInterface} is {@code null}
     * @since 0.1.0
     */
    public ControlPoint(NetworkInterface networkInterface) {
        Objects.requireNonNull(networkInterface, "networkInterface must not be null");
        this.networkInterfaces = List.of(networkInterface);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .executor(Runnable::run)
                .build();
    }

    /**
     * Creates a new control point that discovers devices on all specified
     * network interfaces simultaneously.
     *
     * <p>On {@link #start()}, an {@link SsdpService} is created per interface.
     * Interfaces that fail to bind (e.g. because a VPN captured the multicast
     * route) are logged and skipped; discovery continues on the remaining ones.
     *
     * @param networkInterfaces the network interfaces for SSDP multicast
     * @throws NullPointerException if {@code networkInterfaces} is {@code null}
     * @since 0.1.0
     */
    public ControlPoint(List<NetworkInterface> networkInterfaces) {
        Objects.requireNonNull(networkInterfaces, "networkInterfaces must not be null");
        this.networkInterfaces = List.copyOf(networkInterfaces);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .executor(Runnable::run)
                .build();
    }

    /**
     * Starts the control point: creates and starts the SSDP service (if a network
     * interface was provided), registers the internal SSDP listener, and triggers
     * an initial {@code ssdp:all} M-SEARCH scan.
     *
     * <p>If no network interface was configured, the control point starts in
     * local-only mode where only in-process registered devices are available.
     * If the SSDP service fails to start (e.g., due to network issues), the
     * error is logged and the control point continues in local-only mode.
     *
     * @since 0.1.0
     */
    public void start() {
        if (running) {
            LOG.debug("Control point already running");
            return;
        }
        running = true;

        if (networkInterfaces.isEmpty()) {
            LOG.info("Control point started in local-only mode (no network interface)");
            return;
        }

        if (networkInterfaces.size() == 1) {
            // Single interface — use lightweight SsdpService directly
            var iface = networkInterfaces.getFirst();
            try {
                ssdpService = new SsdpService(iface);
                ssdpService.addListener(this::handleSsdpEvent);
                ssdpService.start();
                ssdpService.searchAll();
                LOG.info("Control point started with SSDP on interface {}", iface.getName());
            } catch (IOException e) {
                LOG.error("Failed to start SSDP service on interface {}: {}. "
                        + "Network device discovery will not be available. "
                        + "If a VPN is active, multicast may be blocked — "
                        + "try: sudo route add -net 224.0.0.0/4 -interface {}",
                        iface.getName(), e.getMessage(), iface.getName());
                LOG.debug("SSDP service start failure details", e);
            }
        } else {
            // Multiple interfaces — use MultiInterfaceSsdpService
            var multi = new MultiInterfaceSsdpService();
            multi.addListener(this::handleSsdpEvent);
            int added = 0;
            for (var iface : networkInterfaces) {
                try {
                    multi.addInterface(iface);
                    added++;
                    LOG.info("SSDP enabled on interface {}", iface.getName());
                } catch (IOException e) {
                    LOG.warn("Skipping interface {} for SSDP: {}", iface.getName(), e.getMessage());
                }
            }
            if (added > 0) {
                multi.start();
                multi.searchAll();
                multiSsdpService = multi;
                LOG.info("Control point started with SSDP on {} of {} interfaces",
                        added, networkInterfaces.size());
            } else {
                LOG.error("Failed to start SSDP on any interface. "
                        + "Network device discovery will not be available.");
            }
        }
    }

    /**
     * Stops the control point, closing the SSDP service and clearing all caches.
     *
     * <p>All previously discovered device proxies become stale after this call.
     * Registered {@link DeviceListener}s are retained and will receive events
     * if the control point is restarted.
     *
     * @since 0.1.0
     */
    public void stop() {
        running = false;
        closeSsdpService();
        pendingFetches.clear();
        deviceCache.clear();
        serverCache.clear();
        rendererCache.clear();
        failedDeviceCache.clear();
        LOG.info("Control point stopped");
    }

    /**
     * Closes this control point, releasing all resources.
     *
     * <p>Equivalent to calling {@link #stop()}.
     *
     * @since 0.1.0
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Returns whether the control point is currently running.
     *
     * @return {@code true} if the control point is running
     * @since 0.1.0
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Triggers a fresh discovery scan by sending an {@code ssdp:all} M-SEARCH
     * request via the underlying SSDP service.
     *
     * <p>For in-process devices, no action is needed as they are already registered.
     * If the SSDP service is not available (no network interface), this method
     * is a no-op.
     *
     * @since 0.1.0
     */
    public void refresh() {
        // Clear failed and pending devices on refresh so they get retried
        failedDeviceCache.clear();
        pendingFetches.clear();
        var service = ssdpService;
        if (service != null && service.isRunning()) {
            service.searchAll();
            LOG.debug("Triggered M-SEARCH refresh");
        }
        var multi = multiSsdpService;
        if (multi != null && multi.isRunning()) {
            multi.searchAll();
            LOG.debug("Triggered M-SEARCH refresh (multi-interface)");
        }
    }

    /**
     * Registers a local media server device for in-process discovery.
     *
     * <p>The device is immediately available via {@link #discoverMediaServers()}
     * and listeners are notified.
     *
     * @param device the media server device to register
     * @throws NullPointerException if {@code device} is {@code null}
     * @since 0.1.0
     */
    public void registerLocalServer(MediaServerDevice device) {
        Objects.requireNonNull(device, "device must not be null");
        var proxy = new MediaServerProxy(device);
        proxy.setMessageLog(messageLog);
        deviceCache.put(device.getUdn(), proxy);
        serverCache.put(device.getUdn(), proxy);
        notifyDeviceAdded(proxy);
    }

    /**
     * Registers a local media renderer device for in-process discovery.
     *
     * <p>The device is immediately available via {@link #discoverMediaRenderers()}
     * and listeners are notified.
     *
     * @param device the media renderer device to register
     * @throws NullPointerException if {@code device} is {@code null}
     * @since 0.1.0
     */
    public void registerLocalRenderer(MediaRendererDevice device) {
        Objects.requireNonNull(device, "device must not be null");
        var proxy = new MediaRendererProxy(device);
        proxy.setMessageLog(messageLog);
        deviceCache.put(device.getUdn(), proxy);
        rendererCache.put(device.getUdn(), proxy);
        notifyDeviceAdded(proxy);
    }

    /**
     * Returns all discovered media servers, including both network-discovered
     * and locally registered devices.
     *
     * @return an unmodifiable list of media server proxies
     * @since 0.1.0
     */
    public List<MediaServerProxy> discoverMediaServers() {
        return Collections.unmodifiableList(new ArrayList<>(serverCache.values()));
    }

    /**
     * Returns all discovered media renderers, including both network-discovered
     * and locally registered devices.
     *
     * @return an unmodifiable list of media renderer proxies
     * @since 0.1.0
     */
    public List<MediaRendererProxy> discoverMediaRenderers() {
        return Collections.unmodifiableList(new ArrayList<>(rendererCache.values()));
    }

    /**
     * Returns all discovered devices of any type, including media servers,
     * media renderers, and generic UPnP devices.
     *
     * @return an unmodifiable list of device proxies
     * @since 0.1.0
     */
    public List<DeviceProxy> getDevices() {
        // Deduplicate: the same proxy may be registered under multiple UDN variants
        // (SSDP-extracted UDN vs description XML UDN)
        var unique = new java.util.LinkedHashSet<>(deviceCache.values());
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }

    /**
     * Returns all devices that failed during discovery or description parsing.
     *
     * <p>These are devices whose SSDP advertisement was received but whose
     * description XML could not be fetched, parsed, or whose registration
     * threw an exception. Each entry contains the error details and, when
     * available, the raw response text for diagnostic purposes.
     *
     * @return an unmodifiable list of failed device records
     * @since 0.1.0
     */
    public List<FailedDevice> getFailedDevices() {
        return Collections.unmodifiableList(new ArrayList<>(failedDeviceCache.values()));
    }

    /**
     * Returns the UPnP message log for diagnostic purposes.
     *
     * <p>The log is disabled by default. Call {@code getMessageLog().setEnabled(true)}
     * to start capturing SSDP, SOAP, and HTTP description messages.
     *
     * @return the message log
     * @since 0.1.0
     */
    public UpnpMessageLog getMessageLog() {
        return messageLog;
    }

    /**
     * A record representing a device that failed during discovery or registration.
     *
     * @param udn          the extracted UDN (may be approximate if parsing failed)
     * @param location     the SSDP LOCATION URL
     * @param errorMessage a human-readable error description
     * @param responseText the raw HTTP response body (description XML), or {@code null}
     *                     if the HTTP fetch itself failed
     * @param timestamp    when the failure occurred (epoch millis)
     * @since 0.1.0
     */
    public record FailedDevice(String udn, String location, String errorMessage,
                                String responseText, long timestamp) {
    }

    /**
     * Adds a device discovery listener that will be notified when devices
     * are added or removed.
     *
     * @param listener the listener to add
     * @throws NullPointerException if {@code listener} is {@code null}
     * @since 0.1.0
     */
    public void addDeviceListener(DeviceListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously added device discovery listener.
     *
     * @param listener the listener to remove
     * @since 0.1.0
     */
    public void removeDeviceListener(DeviceListener listener) {
        listeners.remove(listener);
    }

    /**
     * Removes a device from all caches by its UDN and notifies listeners.
     *
     * <p>Used internally when an SSDP bye-bye is received or a device's cache
     * entry expires. Can also be called externally to force-remove a device.
     *
     * @param udn the Unique Device Name of the device to remove
     * @since 0.1.0
     */
    public void removeDevice(String udn) {
        var removed = deviceCache.remove(udn);
        serverCache.remove(udn);
        rendererCache.remove(udn);
        if (removed != null) {
            // Also remove any alias entries pointing to the same proxy object.
            // This handles the case where SSDP-extracted UDN differs from description UDN
            // and the proxy was registered under both keys.
            deviceCache.entrySet().removeIf(e -> e.getValue() == removed);
            serverCache.entrySet().removeIf(e -> e.getValue() == removed);
            rendererCache.entrySet().removeIf(e -> e.getValue() == removed);
            notifyDeviceRemoved(removed);
        }
    }

    // -----------------------------------------------------------------------
    // SSDP event handling
    // -----------------------------------------------------------------------

    /**
     * Handles an SSDP event from the underlying {@link SsdpService}.
     *
     * <p>Device description fetches are dispatched to virtual threads so that
     * this callback returns immediately without blocking the SSDP receiver.
     *
     * @param event the SSDP event to process
     */
    private void handleSsdpEvent(SsdpEvent event) {
        switch (event) {
            case SsdpEvent.DeviceDiscovered discovered -> {
                messageLog.logIncoming("SSDP", "NOTIFY alive: " + discovered.usn(),
                        "USN: " + discovered.usn() + "\nLOCATION: " + discovered.location());
                Thread.ofVirtual()
                        .name("cp-fetch-", 0)
                        .start(() -> handleDeviceDiscovered(
                                discovered.usn(), discovered.location()));
            }

            case SsdpEvent.DeviceLost lost -> {
                messageLog.logIncoming("SSDP", "NOTIFY byebye: " + lost.usn(), null);
                handleDeviceLost(lost.usn());
            }

            case SsdpEvent.SearchResponse response -> {
                var msg = response.message();
                messageLog.logIncoming("SSDP", "M-SEARCH response: "
                        + msg.usn().orElse("?") + " @ " + msg.location().orElse("?"), null);
                handleSearchResponse(msg);
            }
        }
    }

    /**
     * Handles an SSDP DeviceDiscovered event by fetching the device description
     * and creating the appropriate proxy.
     *
     * @param usn      the unique service name from the SSDP message
     * @param location the LOCATION URL pointing to the device description XML
     */
    private void handleDeviceDiscovered(String usn, String location) {
        var udn = extractUdn(usn);
        if (udn == null) {
            LOG.debug("Could not extract UDN from USN: {}", usn);
            return;
        }

        // Skip if we already know this device (success, failure, or in-flight fetch)
        if (deviceCache.containsKey(udn) || failedDeviceCache.containsKey(udn)) {
            LOG.trace("Device already cached: UDN={}", udn);
            return;
        }

        // Atomic check-and-mark to prevent duplicate concurrent fetches
        if (!pendingFetches.add(udn)) {
            LOG.trace("Device fetch already in progress: UDN={}", udn);
            return;
        }

        fetchAndRegisterDevice(udn, location);
    }

    /**
     * Handles an SSDP SearchResponse by extracting USN/location from the message
     * and fetching the device description on a virtual thread.
     *
     * @param message the SSDP search response message
     */
    private void handleSearchResponse(SsdpMessage message) {
        var usn = message.usn().orElse(null);
        var location = message.location().orElse(null);
        if (usn == null || location == null) {
            return;
        }

        var udn = extractUdn(usn);
        if (udn == null || deviceCache.containsKey(udn) || failedDeviceCache.containsKey(udn)) {
            return;
        }

        // Atomic check-and-mark to prevent duplicate concurrent fetches
        if (!pendingFetches.add(udn)) {
            return;
        }

        Thread.ofVirtual()
                .name("cp-fetch-sr-", 0)
                .start(() -> fetchAndRegisterDevice(udn, location));
    }

    /**
     * Handles an SSDP DeviceLost event by removing the device from all caches
     * and notifying listeners.
     *
     * @param usn the unique service name of the departed device
     */
    private void handleDeviceLost(String usn) {
        var udn = extractUdn(usn);
        if (udn != null) {
            removeDevice(udn);
            LOG.debug("Device lost: UDN={}", udn);
        }
    }

    // -----------------------------------------------------------------------
    // Device description fetching and classification
    // -----------------------------------------------------------------------

    /**
     * Fetches the device description XML from the given location URL, parses it,
     * classifies the device, creates the appropriate proxy, and registers it
     * in the caches.
     *
     * @param udn      the extracted UDN for cache keying
     * @param location the HTTP URL to the device description XML
     */
    private void fetchAndRegisterDevice(String udn, String location) {
        String responseXml = null;
        try {
            messageLog.logOutgoing("HTTP", "GET description " + location, null);
            responseXml = fetchDescriptionXml(location);
            messageLog.logIncoming("HTTP", "Description XML for " + udn + " (" + responseXml.length() + " chars)", responseXml);
            var description = DeviceDescription.parseXml(responseXml);
            var baseUrl = deriveBaseUrl(location);
            var deviceType = description.deviceType();
            var friendlyName = description.friendlyName();

            // Use UDN from the description XML if available, fall back to the SSDP-extracted one
            var resolvedUdn = description.udn() != null ? description.udn() : udn;

            var proxy = classifyAndCreateProxy(
                    resolvedUdn, friendlyName, deviceType, baseUrl, responseXml);

            // Add to the device cache; also to type-specific caches
            deviceCache.put(resolvedUdn, proxy);

            // If the SSDP-extracted UDN differs from the description UDN,
            // also register under the SSDP UDN so future SSDP events are deduped
            if (!udn.equals(resolvedUdn)) {
                deviceCache.put(udn, proxy);
            }

            if (proxy instanceof MediaServerProxy serverProxy) {
                serverCache.put(resolvedUdn, serverProxy);
            } else if (proxy instanceof MediaRendererProxy rendererProxy) {
                rendererCache.put(resolvedUdn, rendererProxy);
            }

            // Check for multi-service devices (e.g., smart TVs with both server and renderer)
            // Protected: exceptions here must not prevent the primary device from being notified
            try {
                registerAdditionalServices(resolvedUdn, friendlyName, baseUrl, responseXml, description, proxy);
            } catch (Exception e) {
                LOG.warn("Error registering additional services for device {}: {}", resolvedUdn, e.getMessage());
                LOG.debug("Additional services registration error details", e);
            }

            // Also register embedded devices — protected similarly
            try {
                registerEmbeddedDevices(resolvedUdn, baseUrl, responseXml, description);
            } catch (Exception e) {
                LOG.warn("Error registering embedded devices for device {}: {}", resolvedUdn, e.getMessage());
                LOG.debug("Embedded devices registration error details", e);
            }

            // Remove from failed cache if a retry succeeded (both UDN variants)
            failedDeviceCache.remove(resolvedUdn);
            failedDeviceCache.remove(udn);

            notifyDeviceAdded(proxy);
            LOG.info("Registered device: UDN={}, name='{}', type='{}'",
                    resolvedUdn, friendlyName, deviceType);

        } catch (Exception e) {
            LOG.warn("Failed to fetch/parse device description from {}: {}",
                    location, e.getMessage());
            LOG.debug("Device description fetch error details", e);

            // Track the failure for the "Unrecognized" UI category
            failedDeviceCache.put(udn, new FailedDevice(
                    udn, location, e.getMessage(), responseXml, System.currentTimeMillis()));
        } finally {
            // Always clean up pending marker so a retry is possible after refresh
            pendingFetches.remove(udn);
        }
    }

    private void registerAdditionalServices(String udn, String friendlyName, URL baseUrl,
                                             String xml, DeviceDescription description,
                                             DeviceProxy primaryProxy) {
        var services = description.services();
        boolean hasContentDirectory = services.stream()
                .anyMatch(s -> s.serviceType().contains("ContentDirectory"));
        boolean hasAvTransport = services.stream()
                .anyMatch(s -> s.serviceType().contains("AVTransport"));

        if (primaryProxy instanceof MediaRendererProxy && hasContentDirectory) {
            var serverProxy = new MediaServerProxy(udn, friendlyName + " (Server)", baseUrl, xml);
            serverProxy.setMessageLog(messageLog);
            serverCache.put(udn, serverProxy);
            LOG.info("Multi-service device: also registered as MediaServer: UDN={}", udn);
        }

        if (primaryProxy instanceof MediaServerProxy && hasAvTransport) {
            var rendererProxy = new MediaRendererProxy(udn, friendlyName + " (Renderer)", baseUrl, xml);
            rendererProxy.setMessageLog(messageLog);
            rendererCache.put(udn, rendererProxy);
            LOG.info("Multi-service device: also registered as MediaRenderer: UDN={}", udn);
        }

        if (!(primaryProxy instanceof MediaServerProxy) && !(primaryProxy instanceof MediaRendererProxy)) {
            if (hasContentDirectory) {
                var serverProxy = new MediaServerProxy(udn, friendlyName, baseUrl, xml);
                serverProxy.setMessageLog(messageLog);
                serverCache.put(udn, serverProxy);
                deviceCache.put(udn, serverProxy);
                LOG.info("Generic device with ContentDirectory: registered as MediaServer: UDN={}", udn);
            }
            if (hasAvTransport) {
                var rendererProxy = new MediaRendererProxy(udn, friendlyName, baseUrl, xml);
                rendererProxy.setMessageLog(messageLog);
                rendererCache.put(udn, rendererProxy);
                if (!hasContentDirectory) {
                    deviceCache.put(udn, rendererProxy);
                }
                LOG.info("Generic device with AVTransport: registered as MediaRenderer: UDN={}", udn);
            }
        }
    }

    private void registerEmbeddedDevices(String parentUdn, URL baseUrl, String xml,
                                          DeviceDescription description) {
        for (var embedded : description.embeddedDevices()) {
            var embeddedUdn = embedded.udn();
            if (embeddedUdn == null || embeddedUdn.isEmpty()) {
                embeddedUdn = parentUdn + "::" + embedded.deviceType();
            }
            if (deviceCache.containsKey(embeddedUdn)) {
                continue;
            }

            var proxy = classifyAndCreateProxy(
                    embeddedUdn, embedded.friendlyName(), embedded.deviceType(), baseUrl, xml);
            deviceCache.put(embeddedUdn, proxy);

            if (proxy instanceof MediaServerProxy serverProxy) {
                serverCache.put(embeddedUdn, serverProxy);
            } else if (proxy instanceof MediaRendererProxy rendererProxy) {
                rendererCache.put(embeddedUdn, rendererProxy);
            }

            notifyDeviceAdded(proxy);
            LOG.info("Registered embedded device: UDN={}, name='{}', type='{}'",
                    embeddedUdn, embedded.friendlyName(), embedded.deviceType());
        }
    }

    /**
     * Fetches device description XML from the given LOCATION URL using
     * {@link HttpClient} with a timeout.
     *
     * @param location the HTTP URL to fetch
     * @return the response body as a string
     * @throws IOException          if the HTTP request fails
     * @throws InterruptedException if the request is interrupted
     */
    private String fetchDescriptionXml(String location)
            throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(location))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode()
                    + " fetching device description from " + location);
        }
        return response.body();
    }

    /**
     * Classifies a discovered device by its device type URN and creates the
     * appropriate proxy type.
     *
     * <p>Media servers and renderers get their own specialized proxy classes;
     * all other device types (routers, WAPs, printers, IoT gateways, etc.)
     * receive a generic {@link DeviceProxy}.
     *
     * @param udn            the Unique Device Name
     * @param friendlyName   the human-readable device name
     * @param deviceType     the UPnP device type URN
     * @param baseUrl        the base URL derived from the LOCATION header
     * @param descriptionXml the raw device description XML
     * @return the classified device proxy
     */
    /**
     * Classifies a discovered device by its device type URN and creates the
     * appropriate proxy type.
     *
     * <p>Device type matching is version-agnostic: both {@code MediaServer:1}
     * and {@code MediaServer:2} (or higher) are recognized. This handles
     * real-world devices like LG TVs that may advertise as version 2 or 3.
     *
     * @param udn            the Unique Device Name
     * @param friendlyName   the human-readable device name
     * @param deviceType     the UPnP device type URN
     * @param baseUrl        the base URL derived from the LOCATION header
     * @param descriptionXml the raw device description XML
     * @return the classified device proxy
     */
    private DeviceProxy classifyAndCreateProxy(String udn, String friendlyName,
                                               String deviceType, URL baseUrl,
                                               String descriptionXml) {
        DeviceProxy proxy;
        if (isMediaServer(deviceType)) {
            proxy = new MediaServerProxy(udn, friendlyName, baseUrl, descriptionXml);
        } else if (isMediaRenderer(deviceType)) {
            proxy = new MediaRendererProxy(udn, friendlyName, baseUrl, descriptionXml);
        } else {
            proxy = new DeviceProxy(udn, friendlyName, deviceType, baseUrl, descriptionXml);
        }
        proxy.setMessageLog(messageLog);
        return proxy;
    }

    /**
     * Checks if a device type URN identifies a media server (any version).
     */
    private static boolean isMediaServer(String deviceType) {
        return deviceType != null
                && deviceType.startsWith("urn:schemas-upnp-org:device:MediaServer:");
    }

    /**
     * Checks if a device type URN identifies a media renderer (any version).
     */
    private static boolean isMediaRenderer(String deviceType) {
        return deviceType != null
                && deviceType.startsWith("urn:schemas-upnp-org:device:MediaRenderer:");
    }

    // -----------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------

    /**
     * Extracts the UDN (UUID) from a USN (Unique Service Name) string.
     *
     * <p>SSDP USN format is typically {@code uuid:XXXXXXXX-...::urn:schemas-...}
     * or just {@code uuid:XXXXXXXX-...}. This method extracts the {@code uuid:...}
     * part before the first {@code ::} separator.
     *
     * @param usn the USN string from the SSDP message
     * @return the extracted UDN, or {@code null} if the USN cannot be parsed
     * @since 0.1.0
     */
    static String extractUdn(String usn) {
        if (usn == null || usn.isEmpty()) {
            return null;
        }
        // USN format: "uuid:xxx::urn:..." or just "uuid:xxx"
        var separatorIndex = usn.indexOf("::");
        return separatorIndex > 0 ? usn.substring(0, separatorIndex) : usn;
    }

    /**
     * Derives the base URL from a device description LOCATION URL.
     *
     * <p>For example, given {@code http://192.168.1.10:8080/desc.xml},
     * the base URL is {@code http://192.168.1.10:8080/}.
     *
     * @param location the full LOCATION URL
     * @return the base URL
     */
    private static URL deriveBaseUrl(String location) {
        try {
            var uri = URI.create(location);
            var baseUri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    "/", null, null);
            return baseUri.toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot derive base URL from location: " + location, e);
        }
    }

    /**
     * Closes the SSDP service if it is currently active.
     */
    private void closeSsdpService() {
        var service = ssdpService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException e) {
                LOG.warn("Error closing SSDP service: {}", e.getMessage());
            }
            ssdpService = null;
        }
        var multi = multiSsdpService;
        if (multi != null) {
            try {
                multi.close();
            } catch (IOException e) {
                LOG.warn("Error closing multi-interface SSDP service: {}", e.getMessage());
            }
            multiSsdpService = null;
        }
    }

    /**
     * Notifies all registered listeners that a device was added.
     *
     * @param device the device proxy that was added
     */
    private void notifyDeviceAdded(DeviceProxy device) {
        for (var listener : listeners) {
            try {
                listener.onDeviceAdded(device);
            } catch (Exception e) {
                LOG.warn("DeviceListener.onDeviceAdded threw exception", e);
            }
        }
    }

    /**
     * Notifies all registered listeners that a device was removed.
     *
     * @param device the device proxy that was removed
     */
    private void notifyDeviceRemoved(DeviceProxy device) {
        for (var listener : listeners) {
            try {
                listener.onDeviceRemoved(device);
            } catch (Exception e) {
                LOG.warn("DeviceListener.onDeviceRemoved threw exception", e);
            }
        }
    }
}
