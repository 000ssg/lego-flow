package ssg.legoflow.http.auth.saml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
/**
 * SAML 2.0 Single Logout support: generates LogoutRequest XML and
 * parses LogoutResponse XML.
 *
 * @since 0.1.0
 */
public class SamlLogout {

    private static final Logger LOG = LoggerFactory.getLogger(SamlLogout.class);
    private static final DateTimeFormatter SAML_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /**
     * Parsed LogoutResponse result.
     *
     * @param id            the response ID
     * @param inResponseTo  the ID of the original LogoutRequest
     * @param statusCode    the status code URI
     * @param issuer        the response issuer
     * @param success       whether the logout was successful
     * @since 0.1.0
     */
    public record LogoutResult(
            String id,
            String inResponseTo,
            String statusCode,
            String issuer,
            boolean success) {
    }

    /**
     * Generates a SAML LogoutRequest XML.
     *
     * @param issuer      the SP entity ID
     * @param destination the IdP SLO URL
     * @param nameId      the NameID of the user to log out
     * @param sessionIndex the session index from the original assertion (may be null)
     * @return the LogoutRequest XML string
     * @since 0.1.0
     */
    public static String generateLogoutRequest(String issuer, String destination,
                                                String nameId, String sessionIndex) {
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(nameId, "nameId must not be null");

        String id = "_" + UUID.randomUUID().toString();
        String issueInstant = SAML_DATE_FORMAT.format(Instant.now());

        var sb = new StringBuilder();
        sb.append("<samlp:LogoutRequest");
        sb.append(" xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"");
        sb.append(" xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"");
        sb.append(" ID=\"").append(id).append("\"");
        sb.append(" Version=\"2.0\"");
        sb.append(" IssueInstant=\"").append(issueInstant).append("\"");
        sb.append(" Destination=\"").append(escapeXml(destination)).append("\"");
        sb.append(">");
        sb.append("<saml:Issuer>").append(escapeXml(issuer)).append("</saml:Issuer>");
        sb.append("<saml:NameID>").append(escapeXml(nameId)).append("</saml:NameID>");
        if (sessionIndex != null) {
            sb.append("<samlp:SessionIndex>").append(escapeXml(sessionIndex)).append("</samlp:SessionIndex>");
        }
        sb.append("</samlp:LogoutRequest>");

        LOG.debug("Generated LogoutRequest with ID={} for NameID={}", id, nameId);
        return sb.toString();
    }

    /**
     * Generates a LogoutRequest from SamlConfig.
     *
     * @param config       the SAML configuration
     * @param spEntityId   the SP entity ID
     * @param nameId       the user's NameID
     * @param sessionIndex the session index (may be null)
     * @return the LogoutRequest XML
     * @since 0.1.0
     */
    public static String generateLogoutRequest(SamlConfig config, String spEntityId,
                                                String nameId, String sessionIndex) {
        return generateLogoutRequest(spEntityId, config.ssoUrl(), nameId, sessionIndex);
    }

    /**
     * Parses a SAML LogoutResponse XML.
     *
     * @param logoutResponseXml the LogoutResponse XML string
     * @return the parsed result, or empty if parsing fails
     * @since 0.1.0
     */
    public static Optional<LogoutResult> parseLogoutResponse(String logoutResponseXml) {
        if (logoutResponseXml == null || logoutResponseXml.isBlank()) {
            return Optional.empty();
        }

        try {
            String id = extractResponseAttribute(logoutResponseXml, "ID");
            String inResponseTo = extractResponseAttribute(logoutResponseXml, "InResponseTo");
            String issuer = SamlAssertionParser.extractElementContent(logoutResponseXml, "Issuer");

            // Extract StatusCode Value
            String statusCode = extractStatusCode(logoutResponseXml);
            boolean success = "urn:oasis:names:tc:SAML:2.0:status:Success".equals(statusCode);

            LOG.debug("Parsed LogoutResponse: id={}, inResponseTo={}, success={}", id, inResponseTo, success);
            return Optional.of(new LogoutResult(id, inResponseTo, statusCode, issuer, success));
        } catch (Exception e) {
            LOG.error("Failed to parse LogoutResponse", e);
            return Optional.empty();
        }
    }

    private static String extractResponseAttribute(String xml, String attrName) {
        for (String prefix : new String[]{"samlp:", ""}) {
            String tag = "<" + prefix + "LogoutResponse";
            int start = xml.indexOf(tag);
            if (start >= 0) {
                int tagEnd = xml.indexOf('>', start);
                if (tagEnd < 0) continue;
                String tagContent = xml.substring(start, tagEnd);
                String search = attrName + "=\"";
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

    private static String extractStatusCode(String xml) {
        for (String prefix : new String[]{"samlp:", ""}) {
            String tag = "<" + prefix + "StatusCode";
            int start = xml.indexOf(tag);
            if (start >= 0) {
                String search = "Value=\"";
                int valStart = xml.indexOf(search, start);
                if (valStart >= 0 && valStart < start + 200) {
                    valStart += search.length();
                    int valEnd = xml.indexOf('"', valStart);
                    if (valEnd >= 0) {
                        return xml.substring(valStart, valEnd);
                    }
                }
            }
        }
        return null;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
