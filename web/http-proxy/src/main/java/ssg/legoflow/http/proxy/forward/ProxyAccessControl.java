package ssg.legoflow.http.proxy.forward;

import ssg.legoflow.http.core.HttpRequest;

/**
 * Interface for controlling which requests are allowed through the forward proxy.
 *
 * <p>Implementations can enforce rules based on target host, port, HTTP method,
 * client identity, or any other request attribute.</p>
 *
 * @since 0.1.0
 */
public interface ProxyAccessControl {

    /**
     * Determines whether the given request should be allowed through the proxy.
     *
     * @param request the incoming request
     * @param targetHost the target host being requested
     * @param targetPort the target port being requested
     * @return true if the request is allowed
     * @since 0.1.0
     */
    boolean isAllowed(HttpRequest request, String targetHost, int targetPort);

    /**
     * Returns a human-readable reason for the last denial, or null if the last
     * check was allowed.
     *
     * @return the denial reason, or null
     * @since 0.1.0
     */
    String getDenialReason();

    /**
     * An access control that allows all requests.
     *
     * @return a permissive access control
     * @since 0.1.0
     */
    static ProxyAccessControl allowAll() {
        return new ProxyAccessControl() {
            @Override
            public boolean isAllowed(HttpRequest request, String targetHost, int targetPort) {
                return true;
            }

            @Override
            public String getDenialReason() {
                return null;
            }
        };
    }

    /**
     * An access control that denies all requests.
     *
     * @return a restrictive access control
     * @since 0.1.0
     */
    static ProxyAccessControl denyAll() {
        return new ProxyAccessControl() {
            @Override
            public boolean isAllowed(HttpRequest request, String targetHost, int targetPort) {
                return false;
            }

            @Override
            public String getDenialReason() {
                return "All requests denied";
            }
        };
    }

    /**
     * Creates an access control that allows only the specified hosts.
     *
     * @param allowedHosts the set of allowed hostnames
     * @return the access control
     * @since 0.1.0
     */
    static ProxyAccessControl allowHosts(java.util.Set<String> allowedHosts) {
        return new HostBasedAccessControl(allowedHosts, java.util.Set.of(), true);
    }

    /**
     * Creates an access control that denies the specified hosts.
     *
     * @param deniedHosts the set of denied hostnames
     * @return the access control
     * @since 0.1.0
     */
    static ProxyAccessControl denyHosts(java.util.Set<String> deniedHosts) {
        return new HostBasedAccessControl(java.util.Set.of(), deniedHosts, false);
    }
}
