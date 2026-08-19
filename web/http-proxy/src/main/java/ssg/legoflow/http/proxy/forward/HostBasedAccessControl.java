package ssg.legoflow.http.proxy.forward;

import ssg.legoflow.http.core.HttpRequest;
import java.util.Set;
/**
 * Host-based access control implementation for the forward proxy.
 *
 * <p>Supports allowlist and denylist modes for controlling access by hostname.</p>
 *
 * @since 0.1.0
 */
class HostBasedAccessControl implements ProxyAccessControl {

    private final Set<String> allowedHosts;
    private final Set<String> deniedHosts;
    private final boolean allowlistMode;
    private volatile String lastDenialReason;

    /**
     * Creates a host-based access control.
     *
     * @param allowedHosts the set of allowed hosts (used in allowlist mode)
     * @param deniedHosts the set of denied hosts (used in denylist mode)
     * @param allowlistMode true for allowlist mode, false for denylist mode
     * @since 0.1.0
     */
    HostBasedAccessControl(Set<String> allowedHosts, Set<String> deniedHosts, boolean allowlistMode) {
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.deniedHosts = Set.copyOf(deniedHosts);
        this.allowlistMode = allowlistMode;
    }

    @Override
    public boolean isAllowed(HttpRequest request, String targetHost, int targetPort) {
        String normalizedHost = targetHost.toLowerCase();
        if (allowlistMode) {
            if (allowedHosts.contains(normalizedHost)) {
                lastDenialReason = null;
                return true;
            }
            lastDenialReason = "Host not in allowlist: " + targetHost;
            return false;
        } else {
            if (deniedHosts.contains(normalizedHost)) {
                lastDenialReason = "Host in denylist: " + targetHost;
                return false;
            }
            lastDenialReason = null;
            return true;
        }
    }

    @Override
    public String getDenialReason() {
        return lastDenialReason;
    }
}
