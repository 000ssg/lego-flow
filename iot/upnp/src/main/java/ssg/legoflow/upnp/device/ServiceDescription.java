package ssg.legoflow.upnp.device;

import java.util.Objects;

/**
 * Describes a UPnP service as listed in a device description XML.
 *
 * <p>Contains the service type, service ID, and URLs for the SCPD document,
 * control endpoint, and event subscription endpoint.
 *
 * @param serviceType the service type URN (e.g., "urn:schemas-upnp-org:service:ContentDirectory:1")
 * @param serviceId   the service identifier (e.g., "urn:upnp-org:serviceId:ContentDirectory")
 * @param scpdUrl     the relative URL to the SCPD XML document
 * @param controlUrl  the relative URL for SOAP action invocation
 * @param eventSubUrl the relative URL for GENA event subscription
 * @since 0.1.0
 */
public record ServiceDescription(
        String serviceType,
        String serviceId,
        String scpdUrl,
        String controlUrl,
        String eventSubUrl
) {

    /** Standard ContentDirectory service type. @since 0.1.0 */
    public static final String TYPE_CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1";

    /** Standard ConnectionManager service type. @since 0.1.0 */
    public static final String TYPE_CONNECTION_MANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1";

    /** Standard AVTransport service type. @since 0.1.0 */
    public static final String TYPE_AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1";

    /** Standard RenderingControl service type. @since 0.1.0 */
    public static final String TYPE_RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1";

    /**
     * Creates a new {@code ServiceDescription} with validation.
     *
     * @throws NullPointerException if any parameter is {@code null}
     */
    public ServiceDescription {
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        Objects.requireNonNull(serviceId, "serviceId must not be null");
        Objects.requireNonNull(scpdUrl, "scpdUrl must not be null");
        Objects.requireNonNull(controlUrl, "controlUrl must not be null");
        Objects.requireNonNull(eventSubUrl, "eventSubUrl must not be null");
    }

    /**
     * Serializes this service description to UPnP device description XML fragment.
     *
     * @return the XML representation
     * @since 0.1.0
     */
    public String toXml() {
        return "<service>" +
                "<serviceType>" + serviceType + "</serviceType>" +
                "<serviceId>" + serviceId + "</serviceId>" +
                "<SCPDURL>" + scpdUrl + "</SCPDURL>" +
                "<controlURL>" + controlUrl + "</controlURL>" +
                "<eventSubURL>" + eventSubUrl + "</eventSubURL>" +
                "</service>";
    }
}
