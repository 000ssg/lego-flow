package ssg.legoflow.http.auth.sso;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * SSO configuration specifying the federation domain, session parameters,
 * and participating services.
 *
 * @since 0.1.0
 */
public class SsoConfig {

    private final String domain;
    private final String cookieName;
    private final Duration sessionTimeout;
    private final Set<String> trustedServices;
    private final boolean secureCookies;

    /**
     * Creates SSO configuration.
     *
     * @param domain          the SSO domain (e.g., "example.com")
     * @param cookieName      the SSO cookie name
     * @param sessionTimeout  the session timeout
     * @param trustedServices the set of trusted service URLs
     * @param secureCookies   whether cookies require HTTPS
     * @since 0.1.0
     */
    public SsoConfig(String domain, String cookieName, Duration sessionTimeout,
                     Set<String> trustedServices, boolean secureCookies) {
        this.domain = Objects.requireNonNull(domain);
        this.cookieName = cookieName != null ? cookieName : "LF_SSO";
        this.sessionTimeout = sessionTimeout != null ? sessionTimeout : Duration.ofHours(8);
        this.trustedServices = trustedServices != null ? Set.copyOf(trustedServices) : Set.of();
        this.secureCookies = secureCookies;
    }

    /**
     * Creates minimal SSO configuration.
     *
     * @param domain the SSO domain
     * @return the configuration
     * @since 0.1.0
     */
    public static SsoConfig forDomain(String domain) {
        return new SsoConfig(domain, null, null, null, true);
    }

    // Getters

    public String getDomain() { return domain; }
    public String getCookieName() { return cookieName; }
    public Duration getSessionTimeout() { return sessionTimeout; }
    public Set<String> getTrustedServices() { return trustedServices; }
    public boolean isSecureCookies() { return secureCookies; }
}
