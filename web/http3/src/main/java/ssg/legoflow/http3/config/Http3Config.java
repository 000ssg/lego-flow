package ssg.legoflow.http3.config;

import ssg.legoflow.http3.Http3Settings;

/**
 * Fluent configuration for HTTP/3 connections.
 *
 * <p>Combines HTTP/3-specific settings (QPACK parameters, max field section size)
 * with QUIC transport settings (idle timeout, stream limits, flow control).
 * Provides factory methods for common configurations.</p>
 *
 * @since 1.0.0
 */
public class Http3Config {

    private long maxFieldSectionSize = Http3Settings.DEFAULT_MAX_FIELD_SECTION_SIZE;
    private int qpackMaxTableCapacity = Http3Settings.DEFAULT_QPACK_MAX_TABLE_CAPACITY;
    private int qpackBlockedStreams = Http3Settings.DEFAULT_QPACK_BLOCKED_STREAMS;
    private long maxIdleTimeout = 30_000;
    private int maxConcurrentStreams = 100;
    private long initialMaxData = 1_048_576;
    private int port = 443;
    private String host = "0.0.0.0";
    private boolean enablePush = false;
    private boolean enable0Rtt = false;

    /**
     * Returns the maximum field section size.
     *
     * @return the maximum field section size
     * @since 1.0.0
     */
    public long maxFieldSectionSize() {
        return maxFieldSectionSize;
    }

    /**
     * Sets the maximum field section size.
     *
     * @param maxFieldSectionSize the maximum size
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config maxFieldSectionSize(long maxFieldSectionSize) {
        this.maxFieldSectionSize = maxFieldSectionSize;
        return this;
    }

    /**
     * Returns the QPACK maximum table capacity.
     *
     * @return the QPACK max table capacity
     * @since 1.0.0
     */
    public int qpackMaxTableCapacity() {
        return qpackMaxTableCapacity;
    }

    /**
     * Sets the QPACK maximum table capacity.
     *
     * @param qpackMaxTableCapacity the capacity
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config qpackMaxTableCapacity(int qpackMaxTableCapacity) {
        this.qpackMaxTableCapacity = qpackMaxTableCapacity;
        return this;
    }

    /**
     * Returns the QPACK blocked streams limit.
     *
     * @return the blocked streams limit
     * @since 1.0.0
     */
    public int qpackBlockedStreams() {
        return qpackBlockedStreams;
    }

    /**
     * Sets the QPACK blocked streams limit.
     *
     * @param qpackBlockedStreams the limit
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config qpackBlockedStreams(int qpackBlockedStreams) {
        this.qpackBlockedStreams = qpackBlockedStreams;
        return this;
    }

    /**
     * Returns the maximum idle timeout in milliseconds.
     *
     * @return the max idle timeout
     * @since 1.0.0
     */
    public long maxIdleTimeout() {
        return maxIdleTimeout;
    }

    /**
     * Sets the maximum idle timeout in milliseconds.
     *
     * @param maxIdleTimeout the timeout
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config maxIdleTimeout(long maxIdleTimeout) {
        this.maxIdleTimeout = maxIdleTimeout;
        return this;
    }

    /**
     * Returns the maximum number of concurrent streams.
     *
     * @return the max concurrent streams
     * @since 1.0.0
     */
    public int maxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    /**
     * Sets the maximum number of concurrent streams.
     *
     * @param maxConcurrentStreams the limit
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config maxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
        return this;
    }

    /**
     * Returns the initial maximum data for the connection.
     *
     * @return the initial max data
     * @since 1.0.0
     */
    public long initialMaxData() {
        return initialMaxData;
    }

    /**
     * Sets the initial maximum data for the connection.
     *
     * @param initialMaxData the data limit
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config initialMaxData(long initialMaxData) {
        this.initialMaxData = initialMaxData;
        return this;
    }

    /**
     * Returns the server port.
     *
     * @return the port number
     * @since 1.0.0
     */
    public int port() {
        return port;
    }

    /**
     * Sets the server port.
     *
     * @param port the port number
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config port(int port) {
        this.port = port;
        return this;
    }

    /**
     * Returns the server host.
     *
     * @return the host address
     * @since 1.0.0
     */
    public String host() {
        return host;
    }

    /**
     * Sets the server host.
     *
     * @param host the host address
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config host(String host) {
        this.host = host;
        return this;
    }

    /**
     * Returns whether server push is enabled.
     *
     * @return {@code true} if push is enabled
     * @since 1.0.0
     */
    public boolean enablePush() {
        return enablePush;
    }

    /**
     * Sets whether server push is enabled.
     *
     * @param enablePush {@code true} to enable push
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config enablePush(boolean enablePush) {
        this.enablePush = enablePush;
        return this;
    }

    /**
     * Returns whether 0-RTT is enabled.
     *
     * @return {@code true} if 0-RTT is enabled
     * @since 1.0.0
     */
    public boolean enable0Rtt() {
        return enable0Rtt;
    }

    /**
     * Sets whether 0-RTT connection resumption is enabled.
     *
     * @param enable0Rtt {@code true} to enable 0-RTT
     * @return this config for chaining
     * @since 1.0.0
     */
    public Http3Config enable0Rtt(boolean enable0Rtt) {
        this.enable0Rtt = enable0Rtt;
        return this;
    }

    /**
     * Creates a default configuration.
     *
     * @return a new default config
     * @since 1.0.0
     */
    public static Http3Config defaults() {
        return new Http3Config();
    }

    /**
     * Creates a high-performance configuration with larger buffers and stream limits.
     *
     * @return a new high-performance config
     * @since 1.0.0
     */
    public static Http3Config highPerformance() {
        return new Http3Config()
                .maxFieldSectionSize(32768)
                .qpackMaxTableCapacity(16384)
                .maxConcurrentStreams(256)
                .initialMaxData(4_194_304)
                .maxIdleTimeout(60_000);
    }

    /**
     * Creates a low-latency configuration optimised for fast response times.
     *
     * @return a new low-latency config
     * @since 1.0.0
     */
    public static Http3Config lowLatency() {
        return new Http3Config()
                .maxFieldSectionSize(4096)
                .qpackMaxTableCapacity(2048)
                .maxConcurrentStreams(50)
                .maxIdleTimeout(10_000)
                .enable0Rtt(true);
    }
}
