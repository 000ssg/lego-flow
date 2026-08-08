package ssg.legoflow.mqtt.client;

import ssg.legoflow.mqtt.broker.MqttTlsConfig;
import ssg.legoflow.mqtt.protocol.MqttVersion;
import ssg.legoflow.mqtt.protocol.WillMessage;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Configuration for {@link MqttClient} with a fluent builder API.
 *
 * @since 0.1.0
 */
public final class MqttClientConfig {

    private final String host;
    private final int port;
    private final String clientId;
    private final MqttVersion version;
    private final boolean cleanSession;
    private final String username;
    private final String password;
    private final WillMessage will;
    private final int keepAlive;
    private final Duration connectTimeout;
    private final boolean autoReconnect;
    private final Duration reconnectDelay;
    private final int maxInflightMessages;
    private final int receiveMaximum;
    private final MqttTlsConfig tlsConfig;

    private MqttClientConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.clientId = builder.clientId;
        this.version = builder.version;
        this.cleanSession = builder.cleanSession;
        this.username = builder.username;
        this.password = builder.password;
        this.will = builder.will;
        this.keepAlive = builder.keepAlive;
        this.connectTimeout = builder.connectTimeout;
        this.autoReconnect = builder.autoReconnect;
        this.reconnectDelay = builder.reconnectDelay;
        this.maxInflightMessages = builder.maxInflightMessages;
        this.receiveMaximum = builder.receiveMaximum;
        this.tlsConfig = builder.tlsConfig;
    }

    /** Returns the broker hostname. */
    public String host() { return host; }

    /** Returns the broker port. */
    public int port() { return port; }

    /** Returns the client identifier. */
    public String clientId() { return clientId; }

    /** Returns the MQTT version. */
    public MqttVersion version() { return version; }

    /** Returns whether a clean session is requested. */
    public boolean cleanSession() { return cleanSession; }

    /** Returns the username, or {@code null} if not set. */
    public String username() { return username; }

    /** Returns the password, or {@code null} if not set. */
    public String password() { return password; }

    /** Returns the will message, or {@code null} if not set. */
    public WillMessage will() { return will; }

    /** Returns the keep-alive interval in seconds. */
    public int keepAlive() { return keepAlive; }

    /** Returns the connect timeout duration. */
    public Duration connectTimeout() { return connectTimeout; }

    /** Returns whether automatic reconnect is enabled. */
    public boolean autoReconnect() { return autoReconnect; }

    /** Returns the delay between reconnect attempts. */
    public Duration reconnectDelay() { return reconnectDelay; }

    /** Returns the maximum number of in-flight messages. */
    public int maxInflightMessages() { return maxInflightMessages; }

    /** Returns the receive maximum (MQTT 5.0). */
    public int receiveMaximum() { return receiveMaximum; }

    /** Returns the TLS configuration, or {@code null} if TLS is not enabled. */
    public MqttTlsConfig tlsConfig() { return tlsConfig; }

    /**
     * Creates a builder with default settings.
     *
     * @return a new default builder
     */
    public static Builder defaults() {
        return new Builder();
    }

    /**
     * Creates a config for a clean session connection.
     *
     * @param host the broker hostname
     * @param port the broker port
     * @return a configured instance with clean session enabled
     */
    public static MqttClientConfig withCleanSession(String host, int port) {
        return new Builder()
                .host(host)
                .port(port)
                .cleanSession(true)
                .build();
    }

    /**
     * Fluent builder for {@link MqttClientConfig}.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private String host = "localhost";
        private int port = 1883;
        private String clientId = "lego-flow-" + UUID.randomUUID().toString().substring(0, 8);
        private MqttVersion version = MqttVersion.V3_1_1;
        private boolean cleanSession = true;
        private String username;
        private String password;
        private WillMessage will;
        private int keepAlive = 60;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private boolean autoReconnect = false;
        private Duration reconnectDelay = Duration.ofSeconds(5);
        private int maxInflightMessages = 10;
        private int receiveMaximum = 65535;
        private MqttTlsConfig tlsConfig;

        /** Sets the broker hostname. */
        public Builder host(String host) {
            this.host = Objects.requireNonNull(host);
            return this;
        }

        /** Sets the broker port. */
        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }

        /** Sets the client identifier. */
        public Builder clientId(String clientId) {
            this.clientId = Objects.requireNonNull(clientId);
            return this;
        }

        /** Sets the MQTT version. */
        public Builder version(MqttVersion version) {
            this.version = Objects.requireNonNull(version);
            return this;
        }

        /** Sets whether a clean session is requested. */
        public Builder cleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        /** Sets the username for authentication. */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /** Sets the password for authentication. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** Sets the last will and testament message. */
        public Builder will(WillMessage will) {
            this.will = will;
            return this;
        }

        /** Sets the keep-alive interval in seconds. */
        public Builder keepAlive(int keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /** Sets the connect timeout duration. */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout);
            return this;
        }

        /** Sets whether automatic reconnect is enabled. */
        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        /** Sets the delay between reconnect attempts. */
        public Builder reconnectDelay(Duration reconnectDelay) {
            this.reconnectDelay = Objects.requireNonNull(reconnectDelay);
            return this;
        }

        /** Sets the maximum number of in-flight messages. */
        public Builder maxInflightMessages(int maxInflightMessages) {
            this.maxInflightMessages = maxInflightMessages;
            return this;
        }

        /** Sets the receive maximum (MQTT 5.0). */
        public Builder receiveMaximum(int receiveMaximum) {
            this.receiveMaximum = receiveMaximum;
            return this;
        }

        /** Sets the TLS configuration for MQTTS connections. */
        public Builder tlsConfig(MqttTlsConfig tlsConfig) {
            this.tlsConfig = tlsConfig;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return a new immutable configuration
         */
        public MqttClientConfig build() {
            return new MqttClientConfig(this);
        }
    }
}
