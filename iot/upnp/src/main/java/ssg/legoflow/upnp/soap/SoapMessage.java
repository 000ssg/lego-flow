package ssg.legoflow.upnp.soap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a UPnP SOAP message (request or response envelope).
 *
 * <p>UPnP uses SOAP over HTTP for action invocation. A SOAP request contains
 * the service type, action name, and input arguments. A SOAP response contains
 * the output arguments or a fault.
 *
 * <p>Parsing uses a namespace-aware DOM parser for maximum compatibility with
 * real-world UPnP devices (MiniDLNA, Plex, Jellyfin, Windows Media Player, etc.)
 * which may use varying namespace prefixes, CDATA sections, and XML-escaped
 * content in output arguments like {@code Result}.
 *
 * @since 0.1.0
 */
public final class SoapMessage {

    private final String serviceType;
    private final String actionName;
    private final Map<String, String> arguments;
    private final Map<String, String> outputArguments;

    /**
     * Creates a new {@code SoapMessage}.
     *
     * @param serviceType     the UPnP service type URN
     * @param actionName      the action name
     * @param arguments       the input arguments
     * @param outputArguments the output arguments (for responses)
     * @throws NullPointerException if any required parameter is {@code null}
     * @since 0.1.0
     */
    public SoapMessage(String serviceType, String actionName,
                       Map<String, String> arguments, Map<String, String> outputArguments) {
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType must not be null");
        this.actionName = Objects.requireNonNull(actionName, "actionName must not be null");
        this.arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
        this.outputArguments = Map.copyOf(
                Objects.requireNonNull(outputArguments, "outputArguments must not be null"));
    }

    /**
     * Creates a SOAP request message for action invocation.
     *
     * @param serviceType the service type URN
     * @param actionName  the action name
     * @param arguments   the input arguments
     * @return a new SOAP request message
     * @since 0.1.0
     */
    public static SoapMessage request(String serviceType, String actionName, Map<String, String> arguments) {
        return new SoapMessage(serviceType, actionName, arguments, Map.of());
    }

    /**
     * Creates a SOAP response message for a successful action.
     *
     * @param serviceType     the service type URN
     * @param actionName      the action name
     * @param outputArguments the output arguments
     * @return a new SOAP response message
     * @since 0.1.0
     */
    public static SoapMessage response(String serviceType, String actionName,
                                       Map<String, String> outputArguments) {
        return new SoapMessage(serviceType, actionName, Map.of(), outputArguments);
    }

    /**
     * Returns the service type URN.
     *
     * @return the service type
     * @since 0.1.0
     */
    public String serviceType() {
        return serviceType;
    }

    /**
     * Returns the action name.
     *
     * @return the action name
     * @since 0.1.0
     */
    public String actionName() {
        return actionName;
    }

    /**
     * Returns the input arguments.
     *
     * @return an unmodifiable map of input argument names to values
     * @since 0.1.0
     */
    public Map<String, String> arguments() {
        return arguments;
    }

    /**
     * Returns the output arguments.
     *
     * @return an unmodifiable map of output argument names to values
     * @since 0.1.0
     */
    public Map<String, String> outputArguments() {
        return outputArguments;
    }

    /**
     * Serializes this message as a SOAP request envelope.
     *
     * @return the SOAP XML request
     * @since 0.1.0
     */
    public String serializeRequest() {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        sb.append("<s:Envelope xmlns:s=\"").append(SoapConstants.SOAP_ENVELOPE_NS).append("\"");
        sb.append(" s:encodingStyle=\"").append(SoapConstants.SOAP_ENCODING_NS).append("\">");
        sb.append("<s:Body>");
        sb.append("<u:").append(actionName).append(" xmlns:u=\"").append(serviceType).append("\">");
        for (var entry : arguments.entrySet()) {
            sb.append("<").append(entry.getKey()).append(">");
            sb.append(escapeXml(entry.getValue()));
            sb.append("</").append(entry.getKey()).append(">");
        }
        sb.append("</u:").append(actionName).append(">");
        sb.append("</s:Body>");
        sb.append("</s:Envelope>");
        return sb.toString();
    }

    /**
     * Serializes this message as a SOAP response envelope.
     *
     * @return the SOAP XML response
     * @since 0.1.0
     */
    public String serializeResponse() {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        sb.append("<s:Envelope xmlns:s=\"").append(SoapConstants.SOAP_ENVELOPE_NS).append("\"");
        sb.append(" s:encodingStyle=\"").append(SoapConstants.SOAP_ENCODING_NS).append("\">");
        sb.append("<s:Body>");
        sb.append("<u:").append(actionName).append("Response xmlns:u=\"").append(serviceType).append("\">");
        for (var entry : outputArguments.entrySet()) {
            sb.append("<").append(entry.getKey()).append(">");
            sb.append(escapeXml(entry.getValue()));
            sb.append("</").append(entry.getKey()).append(">");
        }
        sb.append("</u:").append(actionName).append("Response>");
        sb.append("</s:Body>");
        sb.append("</s:Envelope>");
        return sb.toString();
    }

    /**
     * Serializes a SOAP fault envelope for an error response.
     *
     * @param fault the SOAP fault
     * @return the SOAP XML fault response
     * @since 0.1.0
     */
    public static String serializeFault(SoapFault fault) {
        Objects.requireNonNull(fault, "fault must not be null");
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        sb.append("<s:Envelope xmlns:s=\"").append(SoapConstants.SOAP_ENVELOPE_NS).append("\"");
        sb.append(" s:encodingStyle=\"").append(SoapConstants.SOAP_ENCODING_NS).append("\">");
        sb.append("<s:Body>");
        sb.append("<s:Fault>");
        sb.append("<faultcode>s:Client</faultcode>");
        sb.append("<faultstring>UPnPError</faultstring>");
        sb.append("<detail>");
        sb.append("<UPnPError xmlns=\"").append(SoapConstants.UPNP_CONTROL_NS).append("\">");
        sb.append("<errorCode>").append(fault.errorCode()).append("</errorCode>");
        sb.append("<errorDescription>").append(escapeXml(fault.errorDescription()))
                .append("</errorDescription>");
        sb.append("</UPnPError>");
        sb.append("</detail>");
        sb.append("</s:Fault>");
        sb.append("</s:Body>");
        sb.append("</s:Envelope>");
        return sb.toString();
    }

    /**
     * Parses a SOAP request envelope using a namespace-aware DOM parser.
     *
     * <p>Supports any namespace prefix on the SOAP envelope and action elements.
     * The action name and service type are extracted from the first child element
     * of the Body, regardless of its namespace prefix.
     *
     * @param xml the SOAP XML request
     * @return the parsed SOAP message
     * @throws IllegalArgumentException if the XML cannot be parsed
     * @since 0.1.0
     */
    public static SoapMessage parseRequest(String xml) {
        Objects.requireNonNull(xml, "xml must not be null");
        try {
            var doc = parseXmlDocument(xml);
            var body = findSoapBody(doc);
            var actionElement = findFirstChildElement(body);

            if (actionElement == null) {
                throw new IllegalArgumentException("Invalid SOAP request: no action element found in Body");
            }

            String actionName = actionElement.getLocalName();
            String serviceType = actionElement.getNamespaceURI();

            // If namespace URI is null, try to extract from xmlns attribute
            if (serviceType == null || serviceType.isEmpty()) {
                serviceType = extractNamespaceFromAttributes(actionElement);
            }

            var arguments = extractChildElements(actionElement);
            return new SoapMessage(serviceType, actionName, arguments, Map.of());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse SOAP request: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a SOAP response envelope using a namespace-aware DOM parser.
     *
     * <p>Handles all real-world SOAP response variants:
     * <ul>
     *   <li>Any namespace prefix on envelope and action elements</li>
     *   <li>XML-escaped content in output arguments (auto-unescaped by DOM parser)</li>
     *   <li>CDATA-wrapped content in output arguments</li>
     *   <li>SOAP faults with UPnP error codes</li>
     * </ul>
     *
     * @param xml the SOAP XML response
     * @return the SOAP response with output arguments or fault
     * @throws IllegalArgumentException if the XML cannot be parsed
     * @since 0.1.0
     */
    public static SoapResponse parseResponse(String xml) {
        Objects.requireNonNull(xml, "xml must not be null");
        try {
            var doc = parseXmlDocument(xml);

            // Check for fault first — look for Fault element in SOAP namespace
            var faultElement = findFaultElement(doc);
            if (faultElement != null) {
                return parseFaultFromDom(faultElement);
            }

            var body = findSoapBody(doc);
            var responseElement = findFirstChildElement(body);

            if (responseElement == null) {
                throw new IllegalArgumentException("Invalid SOAP response: no response element found");
            }

            // Extract output arguments; getTextContent() auto-unescapes XML entities and handles CDATA
            var outputArguments = extractChildElements(responseElement);
            return SoapResponse.success(outputArguments);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse SOAP response: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a SOAP fault from the given XML.
     *
     * @param xml the SOAP XML containing a fault
     * @return a failed SOAP response with fault details
     * @since 0.1.0
     */
    public static SoapResponse parseFault(String xml) {
        Objects.requireNonNull(xml, "xml must not be null");
        try {
            var doc = parseXmlDocument(xml);
            var faultElement = findFaultElement(doc);
            if (faultElement != null) {
                return parseFaultFromDom(faultElement);
            }
            return SoapResponse.failure(new SoapFault(0, "Unknown error"));
        } catch (Exception e) {
            return SoapResponse.failure(new SoapFault(0, "Failed to parse fault: " + e.getMessage()));
        }
    }

    // ── DOM parsing helpers ──────────────────────────────────────────────

    /**
     * Parses an XML string into a namespace-aware DOM Document.
     * Configured to not load external DTDs or entities for security and performance.
     */
    private static Document parseXmlDocument(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Security: prevent external entity loading
        try {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
            // Not all parsers support these features
        }
        var builder = factory.newDocumentBuilder();
        // Suppress default error handler stderr output
        builder.setErrorHandler(null);
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Finds the SOAP Body element. Tries namespace-aware lookup first,
     * then falls back to local name matching for maximum compatibility.
     */
    private static Element findSoapBody(Document doc) {
        // Try namespace-aware lookup
        var bodyList = doc.getElementsByTagNameNS(SoapConstants.SOAP_ENVELOPE_NS, "Body");
        if (bodyList.getLength() > 0) {
            return (Element) bodyList.item(0);
        }
        // Fallback: search by local name (handles missing/wrong namespace declarations)
        return findElementByLocalName(doc.getDocumentElement(), "Body");
    }

    /**
     * Finds the SOAP Fault element anywhere in the document.
     */
    private static Element findFaultElement(Document doc) {
        // Try namespace-aware lookup
        var faultList = doc.getElementsByTagNameNS(SoapConstants.SOAP_ENVELOPE_NS, "Fault");
        if (faultList.getLength() > 0) {
            return (Element) faultList.item(0);
        }
        // Fallback: search by local name
        var fallback = doc.getElementsByTagName("Fault");
        if (fallback.getLength() > 0) {
            return (Element) fallback.item(0);
        }
        return null;
    }

    /**
     * Parses SOAP fault details from a DOM Fault element.
     * Extracts UPnP errorCode and errorDescription from the detail section.
     */
    private static SoapResponse parseFaultFromDom(Element faultElement) {
        int errorCode = 0;
        String errorDescription = "Unknown error";

        // Search for errorCode and errorDescription elements anywhere within the fault
        var errorCodeElements = faultElement.getElementsByTagName("errorCode");
        if (errorCodeElements.getLength() > 0) {
            try {
                errorCode = Integer.parseInt(errorCodeElements.item(0).getTextContent().trim());
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }

        var errorDescElements = faultElement.getElementsByTagName("errorDescription");
        if (errorDescElements.getLength() > 0) {
            errorDescription = errorDescElements.item(0).getTextContent().trim();
        }

        return SoapResponse.failure(new SoapFault(errorCode, errorDescription));
    }

    /**
     * Finds the first child Element of a parent node, skipping text nodes and whitespace.
     */
    private static Element findFirstChildElement(Node parent) {
        if (parent == null) return null;
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                return el;
            }
        }
        return null;
    }

    /**
     * Extracts all direct child elements as a name→value map.
     * Uses {@code getLocalName()} for the key (ignoring namespace prefix)
     * and {@code getTextContent()} for the value (auto-unescaping XML entities
     * and handling CDATA sections).
     */
    private static Map<String, String> extractChildElements(Element parent) {
        var result = new LinkedHashMap<String, String>();
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String name = el.getLocalName();
                if (name == null) {
                    name = el.getTagName(); // fallback for non-namespace-aware
                }
                result.put(name, el.getTextContent());
            }
        }
        return result;
    }

    /**
     * Finds a child element by local name (ignoring namespace prefix).
     */
    private static Element findElementByLocalName(Element parent, String localName) {
        if (parent == null) return null;
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String name = el.getLocalName();
                if (name == null) name = el.getTagName();
                if (localName.equals(name) || name.endsWith(":" + localName)) {
                    return el;
                }
                // Recurse into child elements
                var found = findElementByLocalName(el, localName);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Extracts the namespace URI from xmlns:* attributes on the element.
     * Used as fallback when the DOM parser doesn't resolve the namespace.
     */
    private static String extractNamespaceFromAttributes(Element element) {
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = attrs.item(i);
            if (attr.getNodeName().startsWith("xmlns:") || attr.getNodeName().equals("xmlns")) {
                String value = attr.getNodeValue();
                if (value != null && value.startsWith("urn:")) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
