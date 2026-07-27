package ssg.legoflow.http2.config;

import ssg.legoflow.http2.connection.Http2Settings;

public class Http2Config {

    private int initialWindowSize = Http2Settings.DEFAULT_INITIAL_WINDOW_SIZE;
    private int maxConcurrentStreams = Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
    private int maxFrameSize = Http2Settings.DEFAULT_MAX_FRAME_SIZE;
    private int maxHeaderListSize = Http2Settings.DEFAULT_MAX_HEADER_LIST_SIZE;
    private boolean enablePush = true;
    private int headerTableSize = Http2Settings.DEFAULT_HEADER_TABLE_SIZE;
    private int port = 8443;
    private String host = "0.0.0.0";

    public int initialWindowSize() {
        return initialWindowSize;
    }

    public Http2Config initialWindowSize(int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
        return this;
    }

    public int maxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public Http2Config maxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
        return this;
    }

    public int maxFrameSize() {
        return maxFrameSize;
    }

    public Http2Config maxFrameSize(int maxFrameSize) {
        if (maxFrameSize < 16384 || maxFrameSize > 16777215) {
            throw new IllegalArgumentException("MAX_FRAME_SIZE must be between 16384 and 16777215");
        }
        this.maxFrameSize = maxFrameSize;
        return this;
    }

    public int maxHeaderListSize() {
        return maxHeaderListSize;
    }

    public Http2Config maxHeaderListSize(int maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
        return this;
    }

    public boolean enablePush() {
        return enablePush;
    }

    public Http2Config enablePush(boolean enablePush) {
        this.enablePush = enablePush;
        return this;
    }

    public int headerTableSize() {
        return headerTableSize;
    }

    public Http2Config headerTableSize(int headerTableSize) {
        this.headerTableSize = headerTableSize;
        return this;
    }

    public int port() {
        return port;
    }

    public Http2Config port(int port) {
        this.port = port;
        return this;
    }

    public String host() {
        return host;
    }

    public Http2Config host(String host) {
        this.host = host;
        return this;
    }

    public static Http2Config defaults() {
        return new Http2Config();
    }

    public static Http2Config highPerformance() {
        return new Http2Config()
                .initialWindowSize(1048576)
                .maxConcurrentStreams(256)
                .maxFrameSize(65536)
                .maxHeaderListSize(32768);
    }
}
