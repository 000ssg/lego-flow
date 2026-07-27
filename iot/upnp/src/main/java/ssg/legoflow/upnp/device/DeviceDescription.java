package ssg.legoflow.upnp.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a UPnP device description as defined in the device description XML.
 *
 * <p>A device description contains identification information, the list of services
 * offered by the device, any embedded sub-devices, and optional icon resources.
 *
 * <p>Parsing uses a namespace-aware DOM parser for maximum compatibility with
 * real-world UPnP devices which may use varying namespace declarations, missing
 * optional fields, or non-standard XML structures. Fields like manufacturer and
 * modelName default to "Unknown" if missing, rather than causing parse failures.
 *
 * @since 1.0.0
 */
public final class DeviceDescription {

    private static final Logger logger = LoggerFactory.getLogger(DeviceDescription.class);

    /** UPnP device description namespace. */
    private static final String DEVICE_NS = "urn:schemas-upnp-org:device-1-0";

    private final String deviceType;
    private final String friendlyName;
    private final String manufacturer;
    private final String modelName;
    private final String modelNumber;
    private final String udn;
    private final String serialNumber;
    private final String presentationUrl;
    private final List<ServiceDescription> services;
    private final List<DeviceDescription> embeddedDevices;
    private final List<DeviceIcon> icons;

    /**
     * Creates a new {@code DeviceDescription}.
     *
     * @param deviceType      the UPnP device type URN
     * @param friendlyName    a human-readable device name
     * @param manufacturer    the manufacturer name
     * @param modelName       the model name
     * @param modelNumber     the model number; may be {@code null}
     * @param udn             the Unique Device Name (UUID-based)
     * @param serialNumber    the serial number; may be {@code null}
     * @param presentationUrl the presentation URL; may be {@code null}
     * @param services        the list of services offered
     * @param embeddedDevices the list of embedded sub-devices
     * @param icons           the list of device icons
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 1.0.0
     */
    public DeviceDescription(String deviceType, String friendlyName, String manufacturer,
                             String modelName, String modelNumber, String udn,
                             String serialNumber, String presentationUrl,
                             List<ServiceDescription> services,
                             List<DeviceDescription> embeddedDevices,
                             List<DeviceIcon> icons) {
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType must not be null");
        this.friendlyName = Objects.requireNonNull(friendlyName, "friendlyName must not be null");
        this.manufacturer = Objects.requireNonNull(manufacturer, "manufacturer must not be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.modelNumber = modelNumber;
        this.udn = Objects.requireNonNull(udn, "udn must not be null");
        this.serialNumber = serialNumber;
        this.presentationUrl = presentationUrl;
        this.services = List.copyOf(Objects.requireNonNull(services, "services must not be null"));
        this.embeddedDevices = List.copyOf(
                Objects.requireNonNull(embeddedDevices, "embeddedDevices must not be null"));
        this.icons = List.copyOf(Objects.requireNonNull(icons, "icons must not be null"));
    }

    /**
     * Returns the UPnP device type URN.
     *
     * @return the device type
     * @since 1.0.0
     */
    public String deviceType() {
        return deviceType;
    }

    /**
     * Returns the human-readable device name.
     *
     * @return the friendly name
     * @since 1.0.0
     */
    public String friendlyName() {
        return friendlyName;
    }

    /**
     * Returns the manufacturer name.
     *
     * @return the manufacturer
     * @since 1.0.0
     */
    public String manufacturer() {
        return manufacturer;
    }

    /**
     * Returns the model name.
     *
     * @return the model name
     * @since 1.0.0
     */
    public String modelName() {
        return modelName;
    }

    /**
     * Returns the model number.
     *
     * @return the model number, or {@code null}
     * @since 1.0.0
     */
    public String modelNumber() {
        return modelNumber;
    }

    /**
     * Returns the Unique Device Name.
     *
     * @return the UDN
     * @since 1.0.0
     */
    public String udn() {
        return udn;
    }

    /**
     * Returns the serial number.
     *
     * @return the serial number, or {@code null}
     * @since 1.0.0
     */
    public String serialNumber() {
        return serialNumber;
    }

    /**
     * Returns the presentation URL.
     *
     * @return the presentation URL, or {@code null}
     * @since 1.0.0
     */
    public String presentationUrl() {
        return presentationUrl;
    }

    /**
     * Returns the list of services offered by this device.
     *
     * @return an unmodifiable list of service descriptions
     * @since 1.0.0
     */
    public List<ServiceDescription> services() {
        return services;
    }

    /**
     * Returns the list of embedded sub-devices.
     *
     * @return an unmodifiable list of embedded device descriptions
     * @since 1.0.0
     */
    public List<DeviceDescription> embeddedDevices() {
        return embeddedDevices;
    }

    /**
     * Returns the list of device icons.
     *
     * @return an unmodifiable list of device icons
     * @since 1.0.0
     */
    public List<DeviceIcon> icons() {
        return icons;
    }

    /**
     * Parses a UPnP device description from its XML representation using a
     * namespace-aware DOM parser.
     *
     * <p>Expects the standard UPnP device description XML format with a root
     * {@code <root>} element containing a {@code <device>} element. Handles:
     * <ul>
     *   <li>Namespace-prefixed and non-prefixed elements</li>
     *   <li>Missing optional fields (manufacturer, modelName default to "Unknown")</li>
     *   <li>Embedded devices and icons</li>
     *   <li>Various real-world formatting variations</li>
     * </ul>
     *
     * @param xml the device description XML
     * @return the parsed device description
     * @throws NullPointerException     if {@code xml} is {@code null}
     * @throws IllegalArgumentException if the XML cannot be parsed
     * @since 1.0.0
     */
    public static DeviceDescription parseXml(String xml) {
        Objects.requireNonNull(xml, "xml must not be null");
        try {
            var doc = parseXmlDocument(xml);

            // Find the first <device> element — try namespace-aware, then fallback
            Element deviceElement = findFirstElement(doc, DEVICE_NS, "device");
            if (deviceElement == null) {
                deviceElement = findFirstElement(doc, null, "device");
            }
            if (deviceElement == null) {
                throw new IllegalArgumentException("No <device> element found in XML");
            }

            return parseDeviceElement(deviceElement);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse device description XML: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes this device description to UPnP device description XML.
     *
     * @return the complete device description XML with root element
     * @since 1.0.0
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<root xmlns=\"urn:schemas-upnp-org:device-1-0\">");
        sb.append("<specVersion><major>1</major><minor>0</minor></specVersion>");
        appendDeviceXml(sb);
        sb.append("</root>");
        return sb.toString();
    }

    private void appendDeviceXml(StringBuilder sb) {
        sb.append("<device>");
        sb.append("<deviceType>").append(deviceType).append("</deviceType>");
        sb.append("<friendlyName>").append(friendlyName).append("</friendlyName>");
        sb.append("<manufacturer>").append(manufacturer).append("</manufacturer>");
        sb.append("<modelName>").append(modelName).append("</modelName>");
        if (modelNumber != null) {
            sb.append("<modelNumber>").append(modelNumber).append("</modelNumber>");
        }
        sb.append("<UDN>").append(udn).append("</UDN>");
        if (serialNumber != null) {
            sb.append("<serialNumber>").append(serialNumber).append("</serialNumber>");
        }
        if (presentationUrl != null) {
            sb.append("<presentationURL>").append(presentationUrl).append("</presentationURL>");
        }
        if (!icons.isEmpty()) {
            sb.append("<iconList>");
            for (var icon : icons) {
                sb.append(icon.toXml());
            }
            sb.append("</iconList>");
        }
        if (!services.isEmpty()) {
            sb.append("<serviceList>");
            for (var service : services) {
                sb.append(service.toXml());
            }
            sb.append("</serviceList>");
        }
        if (!embeddedDevices.isEmpty()) {
            sb.append("<deviceList>");
            for (var embedded : embeddedDevices) {
                embedded.appendDeviceXml(sb);
            }
            sb.append("</deviceList>");
        }
        sb.append("</device>");
    }

    // ── DOM-based parsing ────────────────────────────────────────────────

    private static DeviceDescription parseDeviceElement(Element deviceElement) {
        var deviceType = getChildText(deviceElement, "deviceType", null);
        var friendlyName = getChildText(deviceElement, "friendlyName", null);
        var manufacturer = getChildText(deviceElement, "manufacturer", "Unknown");
        var modelName = getChildText(deviceElement, "modelName", "Unknown");
        var modelNumber = getChildText(deviceElement, "modelNumber", null);
        var udn = getChildText(deviceElement, "UDN", null);
        var serialNumber = getChildText(deviceElement, "serialNumber", null);
        var presentationUrl = getChildText(deviceElement, "presentationURL", null);

        if (deviceType == null) {
            throw new IllegalArgumentException("Missing required field: deviceType");
        }
        if (friendlyName == null) {
            // Some devices omit friendlyName — use deviceType as fallback
            friendlyName = deviceType;
        }
        if (udn == null) {
            throw new IllegalArgumentException("Missing required field: UDN");
        }

        var services = parseServicesFromDom(deviceElement);
        var icons = parseIconsFromDom(deviceElement);
        var embeddedDevices = parseEmbeddedDevicesFromDom(deviceElement);

        return new DeviceDescription(deviceType, friendlyName, manufacturer, modelName,
                modelNumber, udn, serialNumber, presentationUrl, services, embeddedDevices, icons);
    }

    private static List<ServiceDescription> parseServicesFromDom(Element deviceElement) {
        var result = new ArrayList<ServiceDescription>();

        // Find <serviceList> direct child
        Element serviceList = getDirectChildElement(deviceElement, "serviceList");
        if (serviceList == null) return result;

        // Find all <service> children within the service list
        var children = serviceList.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && matchesLocalName(el, "service")) {
                try {
                    var serviceType = getChildText(el, "serviceType", null);
                    var serviceId = getChildText(el, "serviceId", null);
                    var scpdUrl = getChildText(el, "SCPDURL", null);
                    var controlUrl = getChildText(el, "controlURL", null);
                    var eventSubUrl = getChildText(el, "eventSubURL", null);

                    if (serviceType != null && serviceId != null && scpdUrl != null
                            && controlUrl != null && eventSubUrl != null) {
                        result.add(new ServiceDescription(serviceType, serviceId,
                                scpdUrl, controlUrl, eventSubUrl));
                    } else {
                        logger.debug("Skipping service with missing fields: type={}, id={}",
                                serviceType, serviceId);
                    }
                } catch (Exception e) {
                    logger.debug("Skipping malformed service element: {}", e.getMessage());
                }
            }
        }
        return result;
    }

    private static List<DeviceIcon> parseIconsFromDom(Element deviceElement) {
        var result = new ArrayList<DeviceIcon>();

        Element iconList = getDirectChildElement(deviceElement, "iconList");
        if (iconList == null) return result;

        var children = iconList.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && matchesLocalName(el, "icon")) {
                try {
                    var mimetype = getChildText(el, "mimetype", null);
                    var widthStr = getChildText(el, "width", null);
                    var heightStr = getChildText(el, "height", null);
                    var depthStr = getChildText(el, "depth", null);
                    var url = getChildText(el, "url", null);

                    if (mimetype != null && widthStr != null && heightStr != null
                            && depthStr != null && url != null) {
                        result.add(new DeviceIcon(mimetype,
                                Integer.parseInt(widthStr.trim()),
                                Integer.parseInt(heightStr.trim()),
                                Integer.parseInt(depthStr.trim()),
                                url.trim()));
                    }
                } catch (Exception e) {
                    logger.debug("Skipping malformed icon element: {}", e.getMessage());
                }
            }
        }
        return result;
    }

    private static List<DeviceDescription> parseEmbeddedDevicesFromDom(Element deviceElement) {
        var result = new ArrayList<DeviceDescription>();

        Element deviceList = getDirectChildElement(deviceElement, "deviceList");
        if (deviceList == null) return result;

        var children = deviceList.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && matchesLocalName(el, "device")) {
                try {
                    result.add(parseDeviceElement(el));
                } catch (Exception e) {
                    logger.debug("Skipping malformed embedded device: {}", e.getMessage());
                }
            }
        }
        return result;
    }

    // ── DOM helper methods ───────────────────────────────────────────────

    /**
     * Parses an XML string into a namespace-aware DOM Document.
     *
     * <p>Sanitizes the XML before parsing to handle HTML void elements
     * (e.g. {@code <img>}, {@code <br>}) that some devices embed in their
     * description documents.
     */
    private static Document parseXmlDocument(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
            // Not all parsers support these features
        }
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null);
        return builder.parse(new InputSource(new StringReader(sanitizeXml(xml))));
    }

    /**
     * Sanitizes XML to handle unclosed HTML void elements that some devices
     * embed in their description XML (e.g. {@code <img src="...">} in
     * icon or presentation elements).
     */
    private static String sanitizeXml(String xml) {
        return XmlSanitizer.sanitize(xml);
    }

    /**
     * Finds the first element matching the given namespace and local name in the document.
     * If namespace is null, matches by local name only.
     */
    private static Element findFirstElement(Document doc, String namespace, String localName) {
        NodeList elements;
        if (namespace != null) {
            elements = doc.getElementsByTagNameNS(namespace, localName);
        } else {
            elements = doc.getElementsByTagName(localName);
        }
        if (elements.getLength() > 0) {
            return (Element) elements.item(0);
        }
        // Additional fallback: search by local name matching
        if (namespace != null) {
            elements = doc.getElementsByTagName(localName);
            if (elements.getLength() > 0) {
                return (Element) elements.item(0);
            }
        }
        return null;
    }

    /**
     * Gets the text content of a direct child element by local name.
     * Searches by both namespace-qualified and unqualified names for maximum compatibility.
     */
    private static String getChildText(Element parent, String localName, String defaultValue) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && matchesLocalName(el, localName)) {
                String text = el.getTextContent();
                if (text != null) {
                    text = text.trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }
        }
        return defaultValue;
    }

    /**
     * Gets a direct child element by local name.
     */
    private static Element getDirectChildElement(Element parent, String localName) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && matchesLocalName(el, localName)) {
                return el;
            }
        }
        return null;
    }

    /**
     * Checks if an element matches the given local name, regardless of namespace prefix.
     */
    private static boolean matchesLocalName(Element el, String localName) {
        String elName = el.getLocalName();
        if (elName != null) {
            return localName.equals(elName);
        }
        // Fallback for non-namespace-aware: check tag name
        String tagName = el.getTagName();
        return localName.equals(tagName) || tagName.endsWith(":" + localName);
    }

    @Override
    public String toString() {
        return "DeviceDescription{" +
                "deviceType='" + deviceType + '\'' +
                ", friendlyName='" + friendlyName + '\'' +
                ", udn='" + udn + '\'' +
                '}';
    }
}
