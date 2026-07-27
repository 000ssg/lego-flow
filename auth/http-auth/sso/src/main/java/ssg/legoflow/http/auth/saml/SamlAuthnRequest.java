package ssg.legoflow.http.auth.saml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Generates SAML 2.0 AuthnRequest XML for initiating SSO authentication.
 * Supports both HTTP-Redirect binding (deflate + base64) and HTTP-POST binding
 * (base64 in auto-submitting HTML form).
 *
 * @since 1.0.0
 */
public class SamlAuthnRequest {

    private static final Logger LOG = LoggerFactory.getLogger(SamlAuthnRequest.class);
    private static final DateTimeFormatter SAML_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String id;
    private final Instant issueInstant;
    private final String assertionConsumerServiceUrl;
    private final String issuer;
    private final String destination;
    private final String nameIdFormat;

    /**
     * Creates a SAML AuthnRequest.
     *
     * @param assertionConsumerServiceUrl the SP's ACS URL (where the IdP sends the response)
     * @param issuer                      the SP entity ID
     * @param destination                 the IdP SSO URL
     * @param nameIdFormat                the requested NameID format (may be null)
     * @since 1.0.0
     */
    public SamlAuthnRequest(String assertionConsumerServiceUrl, String issuer,
                            String destination, String nameIdFormat) {
        this.id = "_" + UUID.randomUUID().toString();
        this.issueInstant = Instant.now();
        this.assertionConsumerServiceUrl = Objects.requireNonNull(assertionConsumerServiceUrl);
        this.issuer = Objects.requireNonNull(issuer);
        this.destination = Objects.requireNonNull(destination);
        this.nameIdFormat = nameIdFormat;
    }

    /**
     * Creates a SAML AuthnRequest from SamlConfig.
     *
     * @param config                      the SAML configuration
     * @param assertionConsumerServiceUrl the ACS URL
     * @param spEntityId                  the SP entity ID
     * @return the AuthnRequest
     * @since 1.0.0
     */
    public static SamlAuthnRequest fromConfig(SamlConfig config, String assertionConsumerServiceUrl,
                                               String spEntityId) {
        return new SamlAuthnRequest(assertionConsumerServiceUrl, spEntityId,
                config.ssoUrl(), config.nameIdFormat());
    }

    /**
     * Generates the AuthnRequest XML string.
     *
     * @return the XML string
     * @since 1.0.0
     */
    public String toXml() {
        var sb = new StringBuilder();
        sb.append("<samlp:AuthnRequest");
        sb.append(" xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"");
        sb.append(" xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"");
        sb.append(" ID=\"").append(id).append("\"");
        sb.append(" Version=\"2.0\"");
        sb.append(" IssueInstant=\"").append(SAML_DATE_FORMAT.format(issueInstant)).append("\"");
        sb.append(" Destination=\"").append(escapeXml(destination)).append("\"");
        sb.append(" AssertionConsumerServiceURL=\"").append(escapeXml(assertionConsumerServiceUrl)).append("\"");
        sb.append(" ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\"");
        sb.append(">");
        sb.append("<saml:Issuer>").append(escapeXml(issuer)).append("</saml:Issuer>");
        if (nameIdFormat != null) {
            sb.append("<samlp:NameIDPolicy Format=\"").append(escapeXml(nameIdFormat)).append("\" AllowCreate=\"true\"/>");
        }
        sb.append("</samlp:AuthnRequest>");
        return sb.toString();
    }

    /**
     * Encodes the AuthnRequest for HTTP-Redirect binding (deflate + base64url).
     *
     * @return the deflated and base64-encoded request
     * @since 1.0.0
     */
    public String toRedirectBinding() {
        String xml = toXml();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.DEFLATED, true); // raw deflate (no zlib header)
            DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater);
            dos.write(xml.getBytes(StandardCharsets.UTF_8));
            dos.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOG.error("Failed to deflate AuthnRequest", e);
            // Fallback to plain base64
            return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generates an auto-submitting HTML form for HTTP-POST binding.
     *
     * @return the HTML string with a form containing SAMLRequest as a hidden field
     * @since 1.0.0
     */
    public String toPostBindingForm() {
        String base64 = Base64.getEncoder().encodeToString(toXml().getBytes(StandardCharsets.UTF_8));
        return SamlPostBinding.generateRequestForm(destination, base64, null);
    }

    /**
     * Returns the request ID.
     *
     * @return the ID
     * @since 1.0.0
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the issue instant.
     *
     * @return the instant
     * @since 1.0.0
     */
    public Instant getIssueInstant() {
        return issueInstant;
    }

    /**
     * Returns the ACS URL.
     *
     * @return the ACS URL
     * @since 1.0.0
     */
    public String getAssertionConsumerServiceUrl() {
        return assertionConsumerServiceUrl;
    }

    /**
     * Returns the issuer.
     *
     * @return the issuer
     * @since 1.0.0
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * Returns the destination.
     *
     * @return the destination
     * @since 1.0.0
     */
    public String getDestination() {
        return destination;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
