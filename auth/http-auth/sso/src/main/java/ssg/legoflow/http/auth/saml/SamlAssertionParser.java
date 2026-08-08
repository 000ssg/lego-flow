package ssg.legoflow.http.auth.saml;

import ssg.legoflow.http.auth.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * SAML 2.0 assertion parser. Parses SAML Response/Assertion XML to extract
 * attributes and NameID. This is NOT a full SAML implementation -- it handles
 * assertion parsing for SSO integration.
 *
 * <p>Supports extraction of:</p>
 * <ul>
 *   <li>NameID (subject identifier)</li>
 *   <li>Attributes (name, email, roles, etc.)</li>
 *   <li>Conditions (NotBefore, NotOnOrAfter)</li>
 *   <li>Issuer</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class SamlAssertionParser {

    private static final Logger LOG = LoggerFactory.getLogger(SamlAssertionParser.class);

    private final SamlConfig config;

    /**
     * Creates a SAML assertion parser.
     *
     * @param config the SAML IdP configuration
     * @since 0.1.0
     */
    public SamlAssertionParser(SamlConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Parsed SAML assertion data.
     *
     * @param nameId     the NameID (subject)
     * @param issuer     the assertion issuer
     * @param attributes the assertion attributes
     * @param notBefore  the NotBefore condition
     * @param notOnOrAfter the NotOnOrAfter condition
     * @since 0.1.0
     */
    public record SamlAssertion(
            String nameId,
            String issuer,
            Map<String, String> attributes,
            String notBefore,
            String notOnOrAfter) {

        /**
         * Converts this assertion to an AuthPrincipal.
         *
         * @return the principal
         * @since 0.1.0
         */
        public AuthPrincipal toPrincipal() {
            Set<String> roles = Set.of();
            String rolesAttr = attributes.get("Role");
            if (rolesAttr == null) rolesAttr = attributes.get("roles");
            if (rolesAttr == null) rolesAttr = attributes.get("memberOf");
            if (rolesAttr != null) {
                roles = Set.of(rolesAttr.split(","));
            }

            Map<String, Object> attrs = new HashMap<>(attributes);
            return new AuthPrincipal(nameId, roles, attrs);
        }
    }

    /**
     * Parses a SAML Response XML to extract the assertion.
     *
     * @param samlResponseXml the SAML Response XML string
     * @return the parsed assertion, or empty if parsing fails
     * @since 0.1.0
     */
    public Optional<SamlAssertion> parseResponse(String samlResponseXml) {
        if (samlResponseXml == null || samlResponseXml.isBlank()) {
            return Optional.empty();
        }

        try {
            // Extract Issuer
            String issuer = extractElementContent(samlResponseXml, "Issuer");

            // Validate issuer matches config
            if (issuer != null && !issuer.equals(config.entityId())) {
                LOG.warn("SAML issuer mismatch: expected {}, got {}", config.entityId(), issuer);
                return Optional.empty();
            }

            // Extract NameID
            String nameId = extractElementContent(samlResponseXml, "NameID");
            if (nameId == null) {
                LOG.warn("No NameID found in SAML assertion");
                return Optional.empty();
            }

            // Extract Conditions
            String notBefore = extractAttribute(samlResponseXml, "Conditions", "NotBefore");
            String notOnOrAfter = extractAttribute(samlResponseXml, "Conditions", "NotOnOrAfter");

            // Extract Attributes
            Map<String, String> attributes = extractAttributes(samlResponseXml);

            LOG.debug("Parsed SAML assertion for NameID: {}", nameId);
            return Optional.of(new SamlAssertion(nameId, issuer, attributes, notBefore, notOnOrAfter));

        } catch (Exception e) {
            LOG.error("Failed to parse SAML response", e);
            return Optional.empty();
        }
    }

    /**
     * Parses a Base64-encoded SAML Response.
     *
     * @param base64Response the Base64-encoded response
     * @return the parsed assertion
     * @since 0.1.0
     */
    public Optional<SamlAssertion> parseBase64Response(String base64Response) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Response.trim());
            String xml = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            return parseResponse(xml);
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid Base64 SAML response", e);
            return Optional.empty();
        }
    }

    /**
     * Returns the SAML configuration.
     *
     * @return the config
     * @since 0.1.0
     */
    public SamlConfig getConfig() {
        return config;
    }

    // ---- Simple XML extraction helpers (no external XML parser needed) ----

    /**
     * Extracts the text content of an XML element (handles namespace prefixes).
     *
     * @param xml         the XML string
     * @param elementName the local element name
     * @return the text content, or null
     * @since 0.1.0
     */
    static String extractElementContent(String xml, String elementName) {
        // Try with common SAML namespace prefixes
        for (String prefix : new String[]{"saml:", "saml2:", "samlp:", ""}) {
            String openTag = "<" + prefix + elementName;
            int start = xml.indexOf(openTag);
            if (start >= 0) {
                int tagEnd = xml.indexOf('>', start);
                if (tagEnd < 0) continue;
                // Check for self-closing
                if (xml.charAt(tagEnd - 1) == '/') continue;
                int contentStart = tagEnd + 1;
                String closeTag = "</" + prefix + elementName + ">";
                int contentEnd = xml.indexOf(closeTag, contentStart);
                if (contentEnd >= 0) {
                    return xml.substring(contentStart, contentEnd).trim();
                }
            }
        }
        return null;
    }

    /**
     * Extracts an attribute value from an XML element.
     *
     * @param xml           the XML string
     * @param elementName   the element name
     * @param attributeName the attribute name
     * @return the attribute value, or null
     * @since 0.1.0
     */
    static String extractAttribute(String xml, String elementName, String attributeName) {
        for (String prefix : new String[]{"saml:", "saml2:", ""}) {
            String openTag = "<" + prefix + elementName;
            int start = xml.indexOf(openTag);
            if (start >= 0) {
                int tagEnd = xml.indexOf('>', start);
                if (tagEnd < 0) continue;
                String tagContent = xml.substring(start, tagEnd);
                String search = attributeName + "=\"";
                int attrStart = tagContent.indexOf(search);
                if (attrStart >= 0) {
                    attrStart += search.length();
                    int attrEnd = tagContent.indexOf('"', attrStart);
                    if (attrEnd >= 0) {
                        return tagContent.substring(attrStart, attrEnd);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts SAML attributes from AttributeStatement.
     *
     * @param xml the XML string
     * @return map of attribute names to values
     * @since 0.1.0
     */
    static Map<String, String> extractAttributes(String xml) {
        Map<String, String> attributes = new LinkedHashMap<>();

        // Find all Attribute elements
        int searchFrom = 0;
        while (true) {
            int attrStart = -1;
            for (String prefix : new String[]{"saml:", "saml2:", ""}) {
                int idx = xml.indexOf("<" + prefix + "Attribute ", searchFrom);
                if (idx >= 0 && (attrStart < 0 || idx < attrStart)) {
                    attrStart = idx;
                }
            }
            if (attrStart < 0) break;

            // Extract Name attribute
            String nameSearch = "Name=\"";
            int nameStart = xml.indexOf(nameSearch, attrStart);
            if (nameStart < 0 || nameStart > attrStart + 200) {
                searchFrom = attrStart + 1;
                continue;
            }
            nameStart += nameSearch.length();
            int nameEnd = xml.indexOf('"', nameStart);
            if (nameEnd < 0) break;
            String name = xml.substring(nameStart, nameEnd);

            // Extract AttributeValue
            String value = null;
            for (String prefix : new String[]{"saml:", "saml2:", ""}) {
                String valueTag = "<" + prefix + "AttributeValue";
                int valueStart = xml.indexOf(valueTag, attrStart);
                if (valueStart >= 0 && valueStart < attrStart + 500) {
                    int valueContentStart = xml.indexOf('>', valueStart) + 1;
                    String closeTag = "</" + prefix + "AttributeValue>";
                    int valueEnd = xml.indexOf(closeTag, valueContentStart);
                    if (valueEnd >= 0) {
                        value = xml.substring(valueContentStart, valueEnd).trim();
                        break;
                    }
                }
            }

            if (value != null) {
                attributes.put(name, value);
            }
            searchFrom = attrStart + 1;
        }

        return attributes;
    }
}
