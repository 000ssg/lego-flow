package ssg.legoflow.http.auth.saml;

import java.util.Objects;

/**
 * SAML 2.0 Identity Provider configuration.
 *
 * @param entityId      the IdP entity ID
 * @param ssoUrl        the IdP SSO URL (where to redirect for authentication)
 * @param certificate   the IdP X.509 certificate (PEM-encoded, for signature validation)
 * @param nameIdFormat  the expected NameID format
 * @since 0.1.0
 */
public record SamlConfig(
        String entityId,
        String ssoUrl,
        String certificate,
        String nameIdFormat) {

    /**
     * Creates SAML configuration.
     *
     * @param entityId    the IdP entity ID
     * @param ssoUrl      the IdP SSO URL
     * @param certificate the IdP certificate (PEM-encoded)
     * @param nameIdFormat the NameID format
     * @since 0.1.0
     */
    public SamlConfig {
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(ssoUrl, "ssoUrl must not be null");
        if (nameIdFormat == null) {
            nameIdFormat = "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress";
        }
    }

    /**
     * Creates minimal SAML config without certificate.
     *
     * @param entityId the entity ID
     * @param ssoUrl   the SSO URL
     * @return the config
     * @since 0.1.0
     */
    public static SamlConfig of(String entityId, String ssoUrl) {
        return new SamlConfig(entityId, ssoUrl, null, null);
    }
}
