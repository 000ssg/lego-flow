package ssg.legoflow.upnp.mediaserver;

import ssg.legoflow.upnp.device.XmlSanitizer;
import ssg.legoflow.upnp.dlna.DlnaProtocolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parser and serializer for DIDL-Lite XML used in UPnP ContentDirectory responses.
 *
 * <p>DIDL-Lite (Digital Item Declaration Language — Lite) is the XML format
 * used to describe content items in UPnP AV. This parser handles both
 * {@code <container>} and {@code <item>} elements with Dublin Core (dc:)
 * and UPnP (upnp:) namespace attributes.
 *
 * <p>Parsing uses a namespace-aware DOM parser for maximum compatibility with
 * real-world UPnP/DLNA servers (MiniDLNA, Plex, Jellyfin, Windows Media Player,
 * Kodi, ReadyMedia, TVMOBiLi, Serviio, Twonky, Asset UPnP, PS3 Media Server,
 * Universal Media Server, etc.) which may use:
 * <ul>
 *   <li>Varying namespace prefixes (e.g. {@code dc:}, {@code DC:}, or default namespace)</li>
 *   <li>CDATA-wrapped content in text elements</li>
 *   <li>Multiple {@code <res>} elements per item (different formats/bitrates)</li>
 *   <li>Non-standard or extended UPnP class values</li>
 *   <li>Missing optional metadata elements</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class DidlLiteParser {

    private static final Logger logger = LoggerFactory.getLogger(DidlLiteParser.class);

    /** DIDL-Lite XML namespace. */
    public static final String DIDL_LITE_NS = "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/";

    /** Dublin Core namespace. */
    public static final String DC_NS = "http://purl.org/dc/elements/1.1/";

    /** UPnP metadata namespace. */
    public static final String UPNP_NS = "urn:schemas-upnp-org:metadata-1-0/upnp/";

    private static final String DIDL_LITE_HEADER =
            "<DIDL-Lite xmlns=\"" + DIDL_LITE_NS + "\" "
                    + "xmlns:dc=\"" + DC_NS + "\" "
                    + "xmlns:upnp=\"" + UPNP_NS + "\">";
    private static final String DIDL_LITE_FOOTER = "</DIDL-Lite>";

    /**
     * Parses a DIDL-Lite XML string into a list of content items.
     *
     * <p>Uses a namespace-aware DOM parser to correctly handle all real-world
     * DIDL-Lite variants from different UPnP/DLNA server implementations.
     * Falls back to non-namespace-aware parsing if namespace-aware fails.
     * Returns an empty list (never throws) if parsing fails completely.
     *
     * @param xml the DIDL-Lite XML string
     * @return the list of parsed content items
     * @throws IllegalArgumentException if the XML is null
     * @since 0.1.0
     */
    public List<ContentItem> parse(String xml) {
        Objects.requireNonNull(xml, "xml must not be null");
        if (xml.isBlank()) {
            return List.of();
        }

        try {
            var doc = parseXmlDocument(xml);
            List<ContentItem> items = new ArrayList<>();

            // Parse containers — try namespace-aware first
            NodeList containers = doc.getElementsByTagNameNS(DIDL_LITE_NS, "container");
            if (containers.getLength() == 0) {
                // Fallback: try without namespace (some servers don't declare namespaces properly)
                containers = doc.getElementsByTagName("container");
            }
            for (int i = 0; i < containers.getLength(); i++) {
                try {
                    items.add(parseContainerElement((Element) containers.item(i)));
                } catch (Exception e) {
                    logger.warn("Skipping malformed container element: {}", e.getMessage());
                }
            }

            // Parse items
            NodeList itemElements = doc.getElementsByTagNameNS(DIDL_LITE_NS, "item");
            if (itemElements.getLength() == 0) {
                itemElements = doc.getElementsByTagName("item");
            }
            for (int i = 0; i < itemElements.getLength(); i++) {
                try {
                    items.add(parseItemElement((Element) itemElements.item(i)));
                } catch (Exception e) {
                    logger.warn("Skipping malformed item element: {}", e.getMessage());
                }
            }

            return items;
        } catch (Exception e) {
            logger.warn("Failed to parse DIDL-Lite XML: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Serializes a list of content items to DIDL-Lite XML.
     *
     * @param items the content items to serialize
     * @return the DIDL-Lite XML string
     * @since 0.1.0
     */
    public String serialize(List<ContentItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        var sb = new StringBuilder(DIDL_LITE_HEADER);
        for (ContentItem item : items) {
            if (item.getType() == ContentItemType.CONTAINER) {
                serializeContainer(sb, item);
            } else {
                serializeItem(sb, item);
            }
        }
        sb.append(DIDL_LITE_FOOTER);
        return sb.toString();
    }

    /**
     * Serializes a single content item to DIDL-Lite XML (without the wrapping DIDL-Lite element).
     *
     * @param item the content item
     * @return the XML fragment
     * @since 0.1.0
     */
    public String serializeItem(ContentItem item) {
        var sb = new StringBuilder();
        if (item.getType() == ContentItemType.CONTAINER) {
            serializeContainer(sb, item);
        } else {
            serializeItem(sb, item);
        }
        return sb.toString();
    }

    // ── DOM-based parsing ────────────────────────────────────────────────

    private ContentItem parseContainerElement(Element element) {
        String id = element.getAttribute("id");
        if (id.isEmpty()) id = "0";
        String parentId = element.getAttribute("parentID");
        if (parentId.isEmpty()) parentId = "-1";

        String title = getElementText(element, DC_NS, "title", "Unknown");

        var item = new ContentItem(id, parentId, title, ContentItemType.CONTAINER);
        setOptionalText(element, DC_NS, "creator", item::setCreator);
        setOptionalText(element, UPNP_NS, "genre", item::setGenre);
        setOptionalText(element, DC_NS, "date", item::setDate);
        setOptionalText(element, UPNP_NS, "albumArtURI", item::setAlbumArtUri);

        return item;
    }

    private ContentItem parseItemElement(Element element) {
        String id = element.getAttribute("id");
        if (id.isEmpty()) id = "0";
        String parentId = element.getAttribute("parentID");
        if (parentId.isEmpty()) parentId = "-1";

        String title = getElementText(element, DC_NS, "title", "Unknown");
        String upnpClass = getElementText(element, UPNP_NS, "class", "object.item");

        ContentItemType type = ContentItemType.fromUpnpClass(upnpClass);

        var item = new ContentItem(id, parentId, title, type);
        setOptionalText(element, DC_NS, "creator", item::setCreator);
        setOptionalText(element, UPNP_NS, "genre", item::setGenre);
        setOptionalText(element, DC_NS, "date", item::setDate);
        setOptionalText(element, UPNP_NS, "albumArtURI", item::setAlbumArtUri);

        // Parse <res> elements — try all of them, use the first one with a valid URL
        // Some servers provide multiple <res> for different formats/bitrates
        NodeList resElements = element.getElementsByTagNameNS(DIDL_LITE_NS, "res");
        if (resElements.getLength() == 0) {
            resElements = element.getElementsByTagName("res");
        }

        for (int i = 0; i < resElements.getLength(); i++) {
            var resElement = (Element) resElements.item(i);
            parseResElement(resElement, item);
            // Use the first <res> that provides a URL; keep parsing for protocol info
            if (item.getResourceUrl() != null) {
                break;
            }
        }

        return item;
    }

    private void parseResElement(Element resElement, ContentItem item) {
        String protocolInfoStr = resElement.getAttribute("protocolInfo");
        if (!protocolInfoStr.isEmpty()) {
            try {
                item.setProtocolInfo(DlnaProtocolInfo.parse(protocolInfoStr));
            } catch (IllegalArgumentException e) {
                logger.debug("Skipping invalid protocolInfo: {}", protocolInfoStr);
            }
        }

        String sizeStr = resElement.getAttribute("size");
        if (!sizeStr.isEmpty()) {
            try {
                item.setSize(Long.parseLong(sizeStr));
            } catch (NumberFormatException ignored) {
                // skip invalid size
            }
        }

        String durationStr = resElement.getAttribute("duration");
        if (!durationStr.isEmpty()) {
            try {
                item.setDuration(ContentItem.parseDuration(durationStr));
            } catch (Exception ignored) {
                // skip invalid duration
            }
        }

        String resolutionStr = resElement.getAttribute("resolution");
        if (!resolutionStr.isEmpty()) {
            item.setResolution(resolutionStr);
        }

        String bitrateStr = resElement.getAttribute("bitrate");
        // Note: bitrate is available but ContentItem doesn't have a field for it currently

        // Resource URL is the text content of the <res> element
        String resUrl = resElement.getTextContent().trim();
        if (!resUrl.isEmpty()) {
            try {
                item.setResourceUrl(URI.create(resUrl).toURL());
            } catch (MalformedURLException | IllegalArgumentException e) {
                logger.debug("Skipping invalid resource URL: {}", resUrl);
            }
        }
    }

    // ── Element text extraction helpers ──────────────────────────────────

    /**
     * Gets the text content of a child element by namespace URI and local name.
     * Falls back to matching by local name only if namespace-aware lookup fails.
     * This handles servers that use non-standard namespace prefixes or don't declare namespaces.
     */
    private String getElementText(Element parent, String namespaceUri, String localName, String defaultValue) {
        // Try namespace-aware lookup first
        NodeList elements = parent.getElementsByTagNameNS(namespaceUri, localName);
        if (elements.getLength() > 0) {
            return elements.item(0).getTextContent().trim();
        }

        // Fallback: search by local name (handles missing namespace declarations)
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String elLocalName = el.getLocalName();
                if (elLocalName == null) elLocalName = el.getTagName();
                // Match "title", "dc:title", "DC:title", etc.
                if (localName.equals(elLocalName)
                        || elLocalName.endsWith(":" + localName)) {
                    return el.getTextContent().trim();
                }
            }
        }

        return defaultValue;
    }

    /**
     * Sets an optional text value on an item if the element exists.
     */
    private void setOptionalText(Element parent, String namespaceUri, String localName,
                                 java.util.function.Consumer<String> setter) {
        String value = getElementText(parent, namespaceUri, localName, null);
        if (value != null && !value.isEmpty()) {
            setter.accept(value);
        }
    }

    // ── XML document parsing ─────────────────────────────────────────────

    /**
     * Parses an XML string into a namespace-aware DOM Document.
     *
     * <p>Before parsing, the XML is sanitized to handle common real-world
     * DIDL-Lite issues from NAS devices and media servers that embed HTML
     * fragments containing unclosed void elements (e.g. {@code <img>},
     * {@code <br>}, {@code <hr>}).
     */
    private Document parseXmlDocument(String xml) throws Exception {
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
        builder.setErrorHandler(null); // suppress stderr output
        return builder.parse(new InputSource(new StringReader(sanitizeXml(xml))));
    }

    /**
     * Sanitizes XML to handle common issues from real-world UPnP/DLNA devices.
     *
     * <p>Many NAS devices (Synology, QNAP, WD, etc.) embed HTML fragments in
     * DIDL-Lite metadata fields (e.g. {@code <desc>} or {@code <upnp:albumArtURI>}
     * descriptions) that contain unclosed HTML void elements such as
     * {@code <img src="...">}, {@code <br>}, {@code <hr>}, and {@code <input ...>}.
     * These are valid in HTML but invalid in XML, causing the DOM parser to fail.
     *
     * <p>This method uses a character-level scanner (not regex) to correctly
     * handle quoted attributes containing {@code >} characters. It converts
     * unclosed HTML void elements to self-closing XML form
     * (e.g. {@code <img src="x">} becomes {@code <img src="x" />}).
     *
     * @param xml the raw XML string from the device
     * @return sanitized XML safe for DOM parsing
     */
    static String sanitizeXml(String xml) {
        return XmlSanitizer.sanitize(xml);
    }

    // ── Serialization (unchanged) ────────────────────────────────────────

    private void serializeContainer(StringBuilder sb, ContentItem item) {
        sb.append("<container id=\"").append(escapeXml(item.getId()))
                .append("\" parentID=\"").append(escapeXml(item.getParentId()))
                .append("\" searchable=\"1\" childCount=\"0\">");
        sb.append("<dc:title>").append(escapeXml(item.getTitle())).append("</dc:title>");
        sb.append("<upnp:class>").append(item.getType().upnpClass()).append("</upnp:class>");
        appendOptional(sb, "dc:creator", item.getCreator());
        appendOptional(sb, "upnp:genre", item.getGenre());
        appendOptional(sb, "dc:date", item.getDate());
        appendOptional(sb, "upnp:albumArtURI", item.getAlbumArtUri());
        sb.append("</container>");
    }

    private void serializeItem(StringBuilder sb, ContentItem item) {
        sb.append("<item id=\"").append(escapeXml(item.getId()))
                .append("\" parentID=\"").append(escapeXml(item.getParentId()))
                .append("\">");
        sb.append("<dc:title>").append(escapeXml(item.getTitle())).append("</dc:title>");
        sb.append("<upnp:class>").append(item.getType().upnpClass()).append("</upnp:class>");
        appendOptional(sb, "dc:creator", item.getCreator());
        appendOptional(sb, "upnp:genre", item.getGenre());
        appendOptional(sb, "dc:date", item.getDate());
        appendOptional(sb, "upnp:albumArtURI", item.getAlbumArtUri());

        // Resource element
        if (item.getResourceUrl() != null || item.getProtocolInfo() != null) {
            sb.append("<res");
            if (item.getProtocolInfo() != null) {
                sb.append(" protocolInfo=\"").append(escapeXml(item.getProtocolInfo().toString())).append("\"");
            }
            if (item.getSize() > 0) {
                sb.append(" size=\"").append(item.getSize()).append("\"");
            }
            if (item.getDuration() != null) {
                sb.append(" duration=\"").append(ContentItem.formatDuration(item.getDuration())).append("\"");
            }
            if (item.getResolution() != null) {
                sb.append(" resolution=\"").append(escapeXml(item.getResolution())).append("\"");
            }
            sb.append(">");
            if (item.getResourceUrl() != null) {
                sb.append(escapeXml(item.getResourceUrl().toString()));
            }
            sb.append("</res>");
        }

        sb.append("</item>");
    }

    private void appendOptional(StringBuilder sb, String element, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append("<").append(element).append(">")
                    .append(escapeXml(value))
                    .append("</").append(element).append(">");
        }
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
