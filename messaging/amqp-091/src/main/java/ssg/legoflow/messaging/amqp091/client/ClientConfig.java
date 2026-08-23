package ssg.legoflow.messaging.amqp091.client;

import java.util.Objects;
import java.util.UUID;

/**
 * Configuration for AMQP 0-9-1 client.
 *
 * @since 0.2.0
 */
public final class ClientConfig {
    private final String host;
    private final int port;
    private final String containerId;
    private final int frameMax;
    private final int channelMax;
    private final int heartbeat;
    private final String username;
    private final String virtualHost;
    private final String password;

    private ClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.containerId = builder.containerId;
        this.frameMax = builder.frameMax;
        this.channelMax = builder.channelMax;
        this.heartbeat = builder.heartbeat;
        this.username = builder.username;
        this.password = builder.password;
        this.virtualHost = builder.virtualHost;
    }

    public String host() { return host; }
    public int port() { return port; }
    public String containerId() { return containerId; }
    public int frameMax() { return frameMax; }
    public int channelMax() { return channelMax; }
    public int heartbeat() { return heartbeat; }
    public String username() { return username; }
    public String virtualHost() { return virtualHost; }
    public String password() { return password; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String host = "localhost";
        private int port = 5672;
        private String containerId = "amqp-091-client-" + UUID.randomUUID().toString().substring(0, 8);
        private int frameMax = 131072;
        private int channelMax = 2047;
        private int heartbeat = 60;
        private String virtualHost = "/";
        private String username = "guest";
        private String password = "guest";

        public Builder host(String host) { this.host = Objects.requireNonNull(host); return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder containerId(String cid) { this.containerId = cid; return this; }
        public Builder frameMax(int fm) { this.frameMax = fm; return this; }
        public Builder channelMax(int cm) { this.channelMax = cm; return this; }
        public Builder heartbeat(int hb) { this.heartbeat = hb; return this; }
        public Builder username(String u) { this.username = u; return this; }
        public Builder password(String p) { this.password = p; return this; }
        public ClientConfig build() { return new ClientConfig(this); }
    }
}
