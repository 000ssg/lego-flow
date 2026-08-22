package ssg.legoflow.http.auth.reverse;

import java.util.Set;
/**
 * Configuration for reverse proxy SSO.
 *
 * @since 0.1.0
 */
public class ReverseProxySsoConfig {

    private final String userHeader;
    private final String rolesHeader;
    private final String emailHeader;
    private final String nameHeader;
    private final Set<String> trustedProxies;
    private final boolean requireProxy;

    /**
     * Creates reverse proxy SSO configuration.
     *
     * @param userHeader    the header containing the authenticated username
     * @param rolesHeader   the header containing user roles
     * @param emailHeader   the header containing user email
     * @param nameHeader    the header containing user display name
     * @param trustedProxies the set of trusted proxy IP addresses
     * @param requireProxy  whether requests must come from a trusted proxy
     * @since 0.1.0
     */
    public ReverseProxySsoConfig(String userHeader, String rolesHeader, String emailHeader,
                                  String nameHeader, Set<String> trustedProxies, boolean requireProxy) {
        this.userHeader = userHeader != null ? userHeader : "x-forwarded-user";
        this.rolesHeader = rolesHeader != null ? rolesHeader : "x-forwarded-roles";
        this.emailHeader = emailHeader != null ? emailHeader : "x-forwarded-email";
        this.nameHeader = nameHeader != null ? nameHeader : "x-forwarded-name";
        this.trustedProxies = trustedProxies != null ? Set.copyOf(trustedProxies) : Set.of();
        this.requireProxy = requireProxy;
    }

    /**
     * Creates a default configuration.
     *
     * @return the default config
     * @since 0.1.0
     */
    public static ReverseProxySsoConfig defaults() {
        return new ReverseProxySsoConfig(null, null, null, null, Set.of(), false);
    }

    // Getters

    public String getUserHeader() { return userHeader; }
    public String getRolesHeader() { return rolesHeader; }
    public String getEmailHeader() { return emailHeader; }
    public String getNameHeader() { return nameHeader; }
    public Set<String> getTrustedProxies() { return trustedProxies; }
    public boolean isRequireProxy() { return requireProxy; }
}
