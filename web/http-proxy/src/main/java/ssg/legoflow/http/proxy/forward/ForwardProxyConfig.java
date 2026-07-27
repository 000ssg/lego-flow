package ssg.legoflow.http.proxy.forward;

import ssg.legoflow.http.core.HttpMethod;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration for the forward proxy.
 *
 * <p>Controls allowed methods, max connections, timeouts, and authentication requirements.</p>
 *
 * @since 1.0.0
 */
public class ForwardProxyConfig {

    private Set<HttpMethod> allowedMethods = EnumSet.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE,
            HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PATCH, HttpMethod.CONNECT);
    private int maxConnections = 200;
    private Duration connectionTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    private boolean authRequired = false;
    private String proxyName = "lego-flow-proxy";
    private boolean addViaHeader = true;
    private boolean addForwardedHeaders = true;
    private int maxTunnelIdleSeconds = 300;

    /**
     * Creates a new forward proxy configuration with defaults.
     *
     * @since 1.0.0
     */
    public ForwardProxyConfig() {
    }

    /**
     * Returns the set of allowed HTTP methods.
     *
     * @return the allowed methods
     * @since 1.0.0
     */
    public Set<HttpMethod> getAllowedMethods() {
        return allowedMethods;
    }

    /**
     * Sets the allowed HTTP methods.
     *
     * @param allowedMethods the allowed methods
     * @since 1.0.0
     */
    public void setAllowedMethods(Set<HttpMethod> allowedMethods) {
        this.allowedMethods = EnumSet.copyOf(allowedMethods);
    }

    /**
     * Returns the maximum number of simultaneous connections.
     *
     * @return the max connections
     * @since 1.0.0
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Sets the maximum number of simultaneous connections.
     *
     * @param maxConnections the max connections
     * @since 1.0.0
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * Returns the connection timeout.
     *
     * @return the connection timeout
     * @since 1.0.0
     */
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the connection timeout.
     *
     * @param connectionTimeout the connection timeout
     * @since 1.0.0
     */
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Returns the read timeout.
     *
     * @return the read timeout
     * @since 1.0.0
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the read timeout.
     *
     * @param readTimeout the read timeout
     * @since 1.0.0
     */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Returns whether proxy authentication is required.
     *
     * @return true if auth is required
     * @since 1.0.0
     */
    public boolean isAuthRequired() {
        return authRequired;
    }

    /**
     * Sets whether proxy authentication is required.
     *
     * @param authRequired true if auth is required
     * @since 1.0.0
     */
    public void setAuthRequired(boolean authRequired) {
        this.authRequired = authRequired;
    }

    /**
     * Returns the proxy name used in Via headers.
     *
     * @return the proxy name
     * @since 1.0.0
     */
    public String getProxyName() {
        return proxyName;
    }

    /**
     * Sets the proxy name used in Via headers.
     *
     * @param proxyName the proxy name
     * @since 1.0.0
     */
    public void setProxyName(String proxyName) {
        this.proxyName = proxyName;
    }

    /**
     * Returns whether Via headers should be added.
     *
     * @return true if Via header is added
     * @since 1.0.0
     */
    public boolean isAddViaHeader() {
        return addViaHeader;
    }

    /**
     * Sets whether Via headers should be added.
     *
     * @param addViaHeader true to add Via header
     * @since 1.0.0
     */
    public void setAddViaHeader(boolean addViaHeader) {
        this.addViaHeader = addViaHeader;
    }

    /**
     * Returns whether X-Forwarded-For headers should be added.
     *
     * @return true if forwarded headers are added
     * @since 1.0.0
     */
    public boolean isAddForwardedHeaders() {
        return addForwardedHeaders;
    }

    /**
     * Sets whether X-Forwarded-For headers should be added.
     *
     * @param addForwardedHeaders true to add forwarded headers
     * @since 1.0.0
     */
    public void setAddForwardedHeaders(boolean addForwardedHeaders) {
        this.addForwardedHeaders = addForwardedHeaders;
    }

    /**
     * Returns the maximum idle time in seconds for CONNECT tunnels.
     *
     * @return the max idle seconds
     * @since 1.0.0
     */
    public int getMaxTunnelIdleSeconds() {
        return maxTunnelIdleSeconds;
    }

    /**
     * Sets the maximum idle time in seconds for CONNECT tunnels.
     *
     * @param maxTunnelIdleSeconds the max idle seconds
     * @since 1.0.0
     */
    public void setMaxTunnelIdleSeconds(int maxTunnelIdleSeconds) {
        this.maxTunnelIdleSeconds = maxTunnelIdleSeconds;
    }
}
