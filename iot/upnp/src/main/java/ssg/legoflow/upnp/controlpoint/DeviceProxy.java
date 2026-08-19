package ssg.legoflow.upnp.controlpoint;

import ssg.legoflow.upnp.device.DeviceDescription;
import ssg.legoflow.upnp.device.ServiceDescription;
import ssg.legoflow.upnp.soap.SoapClient;
import ssg.legoflow.upnp.soap.SoapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Base proxy for a remote UPnP device discovered on the network.
 *
 * <p>Provides access to the device description, service invocation,
 * and event subscription capabilities. Subclasses provide typed
 * interfaces for specific device types (media servers, renderers).
 *
 * <p>For network-discovered devices, SOAP action invocation is performed
 * by resolving the service control URL from the device description XML
 * and using {@link SoapClient} to send SOAP requests over HTTP.
 *
 * @since 0.1.0
 */
public class DeviceProxy {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceProxy.class);

    private final String udn;
    private final String friendlyName;
    private final String deviceType;
    private final URL baseUrl;
    private final String descriptionXml;
    private volatile List<ServiceDescription> serviceDescriptions;
    private final Map<String, ServiceDescription> serviceCache = new ConcurrentHashMap<>();
    private volatile SoapClient soapClient;
    private volatile UpnpMessageLog messageLog;

    /**
     * Creates a new device proxy.
     *
     * @param udn            the Unique Device Name
     * @param friendlyName   the human-readable name
     * @param deviceType     the UPnP device type URN
     * @param baseUrl        the base URL for the device
     * @param descriptionXml the raw device description XML
     * @since 0.1.0
     */
    public DeviceProxy(String udn, String friendlyName, String deviceType,
                       URL baseUrl, String descriptionXml) {
        this.udn = Objects.requireNonNull(udn, "udn must not be null");
        this.friendlyName = Objects.requireNonNull(friendlyName, "friendlyName must not be null");
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.descriptionXml = descriptionXml;
    }

    /**
     * Returns the Unique Device Name.
     *
     * @return the UDN
     * @since 0.1.0
     */
    public String getUdn() {
        return udn;
    }

    /**
     * Sets the message log for diagnostic capture of SOAP requests/responses.
     *
     * @param messageLog the message log, or {@code null} to disable
     * @since 0.1.0
     */
    void setMessageLog(UpnpMessageLog messageLog) {
        this.messageLog = messageLog;
    }

    /**
     * Returns the human-readable device name.
     *
     * @return the friendly name
     * @since 0.1.0
     */
    public String getFriendlyName() {
        return friendlyName;
    }

    /**
     * Returns the UPnP device type URN.
     *
     * @return the device type
     * @since 0.1.0
     */
    public String getDeviceType() {
        return deviceType;
    }

    /**
     * Returns the base URL for accessing the device's services.
     *
     * @return the base URL
     * @since 0.1.0
     */
    public URL getBaseUrl() {
        return baseUrl;
    }

    /**
     * Returns the raw device description XML.
     *
     * @return the description XML
     * @since 0.1.0
     */
    public String getDescriptionXml() {
        return descriptionXml;
    }

    /**
     * Invokes a SOAP action on the specified service.
     *
     * <p>For network-discovered devices, this method resolves the service control URL
     * from the device description XML and uses {@link SoapClient} to send a SOAP
     * request over HTTP. The service is identified by matching the {@code serviceId}
     * or {@code serviceType} against the device's service list.
     *
     * @param serviceId  the service ID or service type to invoke
     * @param actionName the action name
     * @param args       the input arguments
     * @return the output arguments
     * @throws UnsupportedOperationException if the service cannot be found or invocation fails
     * @since 0.1.0
     */
    public Map<String, String> invokeAction(String serviceId, String actionName,
                                            Map<String, String> args) {
        // Find the service description matching the given serviceId or serviceType
        var service = findService(serviceId);
        if (service == null) {
            throw new UnsupportedOperationException(
                    "Service not found: " + serviceId + " on device " + friendlyName
                            + " (available services: " + getAvailableServiceIds() + ")");
        }

        // Resolve the control URL against the device base URL
        URI controlUri = resolveControlUrl(service.controlUrl());

        LOG.debug("Invoking SOAP action {}.{} on {} (controlUrl={})",
                serviceId, actionName, friendlyName, controlUri);

        try {
            var client = getOrCreateSoapClient();
            // Log outgoing SOAP request
            if (messageLog != null) {
                messageLog.logOutgoing("SOAP",
                        actionName + " → " + friendlyName + " (" + controlUri + ")",
                        "Action: " + service.serviceType() + "#" + actionName + "\nArgs: " + args);
            }

            SoapResponse response = client.invoke(controlUri, service.serviceType(), actionName, args);

            if (response.success()) {
                // Log successful SOAP response
                if (messageLog != null) {
                    var output = response.outputArguments();
                    String resultSummary = output.containsKey("Result")
                            ? "DIDL-Lite (" + output.get("Result").length() + " chars)"
                            : output.keySet().toString();
                    messageLog.logIncoming("SOAP",
                            actionName + " OK from " + friendlyName + " → " + resultSummary,
                            output.getOrDefault("Result", output.toString()));
                }
                return response.outputArguments();
            } else {
                var fault = response.fault();
                String errorMsg = fault != null ? fault.toErrorString()
                        : "Unknown SOAP error";
                if (messageLog != null) {
                    messageLog.logIncoming("SOAP",
                            actionName + " FAULT from " + friendlyName,
                            errorMsg);
                }
                throw new RuntimeException("SOAP action " + actionName + " failed on "
                        + friendlyName + ": " + errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (messageLog != null) {
                messageLog.logIncoming("SOAP",
                        actionName + " ERROR from " + friendlyName,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            throw new RuntimeException("SOAP invocation failed for " + actionName
                    + " on " + friendlyName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Finds a service description matching the given service ID or service type.
     *
     * @param serviceIdOrType the service ID or service type to search for
     * @return the matching service description, or null if not found
     */
    private ServiceDescription findService(String serviceIdOrType) {
        return serviceCache.computeIfAbsent(serviceIdOrType, key -> {
            var services = getServiceDescriptions();
            for (var svc : services) {
                if (svc.serviceId().equals(key) || svc.serviceType().equals(key)) {
                    return svc;
                }
                // Also match by short form (e.g., "ContentDirectory" matches the full URN)
                if (svc.serviceId().contains(key) || svc.serviceType().contains(key)) {
                    return svc;
                }
            }
            return null;
        });
    }

    /**
     * Returns the parsed service descriptions from the device description XML.
     *
     * @return the list of service descriptions (lazy-parsed, cached)
     */
    private List<ServiceDescription> getServiceDescriptions() {
        if (serviceDescriptions == null) {
            synchronized (this) {
                if (serviceDescriptions == null) {
                    if (descriptionXml != null && !descriptionXml.isEmpty()) {
                        try {
                            var desc = DeviceDescription.parseXml(descriptionXml);
                            serviceDescriptions = desc.services();
                        } catch (Exception e) {
                            LOG.warn("Failed to parse device description XML for {}: {}",
                                    friendlyName, e.getMessage());
                            serviceDescriptions = List.of();
                        }
                    } else {
                        serviceDescriptions = List.of();
                    }
                }
            }
        }
        return serviceDescriptions;
    }

    /**
     * Resolves a relative control URL against the device's base URL.
     *
     * @param controlUrl the relative control URL from the service description
     * @return the absolute URI for SOAP invocation
     */
    private URI resolveControlUrl(String controlUrl) {
        try {
            if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) {
                return URI.create(controlUrl);
            }
            String base = baseUrl.toString();
            if (!base.endsWith("/") && !controlUrl.startsWith("/")) {
                base += "/";
            }
            if (base.endsWith("/") && controlUrl.startsWith("/")) {
                // Use the scheme+host+port from baseUrl with the absolute path
                return URI.create(baseUrl.getProtocol() + "://" + baseUrl.getHost()
                        + (baseUrl.getPort() > 0 ? ":" + baseUrl.getPort() : "")
                        + controlUrl);
            }
            return URI.create(base + controlUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve control URL: " + controlUrl
                    + " against base " + baseUrl, e);
        }
    }

    /**
     * Returns a comma-separated list of available service IDs for error messages.
     */
    private String getAvailableServiceIds() {
        var services = getServiceDescriptions();
        if (services.isEmpty()) return "(none - no description XML)";
        return services.stream()
                .map(ServiceDescription::serviceId)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");
    }

    /**
     * Returns the shared SoapClient, creating it lazily.
     */
    private SoapClient getOrCreateSoapClient() {
        if (soapClient == null) {
            synchronized (this) {
                if (soapClient == null) {
                    soapClient = new SoapClient();
                }
            }
        }
        return soapClient;
    }

    @Override
    public String toString() {
        return "DeviceProxy{udn='" + udn + "', name='" + friendlyName
                + "', type='" + deviceType + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceProxy that)) return false;
        return udn.equals(that.udn);
    }

    @Override
    public int hashCode() {
        return udn.hashCode();
    }
}
