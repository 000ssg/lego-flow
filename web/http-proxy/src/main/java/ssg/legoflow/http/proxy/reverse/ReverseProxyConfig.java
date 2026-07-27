package ssg.legoflow.http.proxy.reverse;

import java.time.Duration;

/**
 * Configuration for the reverse proxy.
 *
 * @since 1.0.0
 */
public class ReverseProxyConfig {

    private String proxyName = "lego-flow-reverse-proxy";
    private Duration connectionTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    private boolean addForwardedHeaders = true;
    private boolean addViaHeader = true;
    private boolean preserveHostHeader = false;
    private boolean webSocketSupport = true;
    private int maxRetries = 1;

    /**
     * Creates a new reverse proxy configuration with defaults.
     *
     * @since 1.0.0
     */
    public ReverseProxyConfig() {
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
     * Sets the proxy name.
     *
     * @param proxyName the proxy name
     * @since 1.0.0
     */
    public void setProxyName(String proxyName) {
        this.proxyName = proxyName;
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
     * Returns whether X-Forwarded-* headers are added.
     *
     * @return true if forwarded headers are added
     * @since 1.0.0
     */
    public boolean isAddForwardedHeaders() {
        return addForwardedHeaders;
    }

    /**
     * Sets whether X-Forwarded-* headers are added.
     *
     * @param addForwardedHeaders true to add
     * @since 1.0.0
     */
    public void setAddForwardedHeaders(boolean addForwardedHeaders) {
        this.addForwardedHeaders = addForwardedHeaders;
    }

    /**
     * Returns whether Via headers are added.
     *
     * @return true if Via is added
     * @since 1.0.0
     */
    public boolean isAddViaHeader() {
        return addViaHeader;
    }

    /**
     * Sets whether Via headers are added.
     *
     * @param addViaHeader true to add
     * @since 1.0.0
     */
    public void setAddViaHeader(boolean addViaHeader) {
        this.addViaHeader = addViaHeader;
    }

    /**
     * Returns whether the original Host header is preserved.
     *
     * @return true if preserved
     * @since 1.0.0
     */
    public boolean isPreserveHostHeader() {
        return preserveHostHeader;
    }

    /**
     * Sets whether the original Host header is preserved.
     *
     * @param preserveHostHeader true to preserve
     * @since 1.0.0
     */
    public void setPreserveHostHeader(boolean preserveHostHeader) {
        this.preserveHostHeader = preserveHostHeader;
    }

    /**
     * Returns whether WebSocket proxy support is enabled.
     *
     * @return true if WebSocket support is enabled
     * @since 1.0.0
     */
    public boolean isWebSocketSupport() {
        return webSocketSupport;
    }

    /**
     * Sets whether WebSocket proxy support is enabled.
     *
     * @param webSocketSupport true to enable
     * @since 1.0.0
     */
    public void setWebSocketSupport(boolean webSocketSupport) {
        this.webSocketSupport = webSocketSupport;
    }

    /**
     * Returns the maximum number of retries on failure.
     *
     * @return the max retries
     * @since 1.0.0
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum number of retries on failure.
     *
     * @param maxRetries the max retries
     * @since 1.0.0
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
