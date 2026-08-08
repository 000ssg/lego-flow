package ssg.legoflow.http.auth.saml;

/**
 * Generates auto-submitting HTML forms for SAML HTTP-POST binding.
 * The generated forms contain SAMLRequest or SAMLResponse as hidden fields
 * and auto-submit via JavaScript.
 *
 * @since 0.1.0
 */
public final class SamlPostBinding {

    private SamlPostBinding() {}

    /**
     * Generates an auto-submitting HTML form for sending a SAMLRequest via POST binding.
     *
     * @param destination  the IdP SSO URL (form action)
     * @param base64Request the base64-encoded SAMLRequest XML
     * @param relayState   optional RelayState value (may be null)
     * @return the HTML string
     * @since 0.1.0
     */
    public static String generateRequestForm(String destination, String base64Request, String relayState) {
        return generateForm(destination, "SAMLRequest", base64Request, relayState);
    }

    /**
     * Generates an auto-submitting HTML form for sending a SAMLResponse via POST binding.
     *
     * @param destination   the SP ACS URL (form action)
     * @param base64Response the base64-encoded SAMLResponse XML
     * @param relayState    optional RelayState value (may be null)
     * @return the HTML string
     * @since 0.1.0
     */
    public static String generateResponseForm(String destination, String base64Response, String relayState) {
        return generateForm(destination, "SAMLResponse", base64Response, relayState);
    }

    private static String generateForm(String destination, String fieldName, String base64Value, String relayState) {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><title>SAML POST</title></head>\n<body onload=\"document.forms[0].submit()\">\n");
        sb.append("<noscript><p>JavaScript is disabled. Click the button below to continue.</p></noscript>\n");
        sb.append("<form method=\"post\" action=\"").append(escapeHtml(destination)).append("\">\n");
        sb.append("  <input type=\"hidden\" name=\"").append(fieldName).append("\" value=\"")
                .append(escapeHtml(base64Value)).append("\"/>\n");
        if (relayState != null) {
            sb.append("  <input type=\"hidden\" name=\"RelayState\" value=\"")
                    .append(escapeHtml(relayState)).append("\"/>\n");
        }
        sb.append("  <noscript><input type=\"submit\" value=\"Continue\"/></noscript>\n");
        sb.append("</form>\n</body></html>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
