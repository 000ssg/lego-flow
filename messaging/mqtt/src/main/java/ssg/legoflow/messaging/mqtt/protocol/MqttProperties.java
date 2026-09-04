package ssg.legoflow.messaging.mqtt.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MQTT 5.0 properties container.
 *
 * <p>Stores typed property values keyed by their specification-defined property identifiers.
 * Provides typed getters/setters for all standard MQTT 5.0 properties and
 * encode/decode methods for wire-format serialization.
 *
 * @since 0.1.0
 */
public final class MqttProperties {

    /** Property identifier constants as defined in the MQTT 5.0 specification. */
    public static final int PAYLOAD_FORMAT_INDICATOR = 0x01;
    public static final int MESSAGE_EXPIRY_INTERVAL = 0x02;
    public static final int CONTENT_TYPE = 0x03;
    public static final int RESPONSE_TOPIC = 0x08;
    public static final int CORRELATION_DATA = 0x09;
    public static final int SESSION_EXPIRY_INTERVAL = 0x11;
    public static final int ASSIGNED_CLIENT_IDENTIFIER = 0x12;
    public static final int SERVER_KEEP_ALIVE = 0x13;
    public static final int AUTHENTICATION_METHOD = 0x15;
    public static final int AUTHENTICATION_DATA = 0x16;
    public static final int TOPIC_ALIAS = 0x23;
    public static final int MAXIMUM_QOS = 0x24;
    public static final int RETAIN_AVAILABLE = 0x25;
    public static final int USER_PROPERTY = 0x26;
    public static final int MAXIMUM_PACKET_SIZE = 0x27;
    public static final int WILDCARD_SUBSCRIPTION_AVAILABLE = 0x28;
    public static final int SUBSCRIPTION_IDENTIFIER_AVAILABLE = 0x29;
    public static final int SHARED_SUBSCRIPTION_AVAILABLE = 0x2A;

    private final Map<Integer, Object> properties = new LinkedHashMap<>();
    private final List<UserProperty> userProperties = new ArrayList<>();

    /**
     * Creates an empty properties container.
     */
    public MqttProperties() {
    }

    // --- Typed getters ---

    /**
     * Returns the payload format indicator (0 = unspecified, 1 = UTF-8).
     *
     * @return the payload format indicator, or empty if not set
     */
    public Optional<Integer> getPayloadFormatIndicator() {
        return getInt(PAYLOAD_FORMAT_INDICATOR);
    }

    /**
     * Returns the message expiry interval in seconds.
     *
     * @return the expiry interval, or empty if not set
     */
    public Optional<Long> getMessageExpiryInterval() {
        return getLong(MESSAGE_EXPIRY_INTERVAL);
    }

    /**
     * Returns the content type string.
     *
     * @return the content type, or empty if not set
     */
    public Optional<String> getContentType() {
        return getString(CONTENT_TYPE);
    }

    /**
     * Returns the response topic.
     *
     * @return the response topic, or empty if not set
     */
    public Optional<String> getResponseTopic() {
        return getString(RESPONSE_TOPIC);
    }

    /**
     * Returns the correlation data.
     *
     * @return the correlation data, or empty if not set
     */
    public Optional<byte[]> getCorrelationData() {
        return getBytes(CORRELATION_DATA);
    }

    /**
     * Returns the session expiry interval in seconds.
     *
     * @return the session expiry interval, or empty if not set
     */
    public Optional<Long> getSessionExpiryInterval() {
        return getLong(SESSION_EXPIRY_INTERVAL);
    }

    /**
     * Returns the assigned client identifier.
     *
     * @return the assigned client identifier, or empty if not set
     */
    public Optional<String> getAssignedClientIdentifier() {
        return getString(ASSIGNED_CLIENT_IDENTIFIER);
    }

    /**
     * Returns the server keep-alive value in seconds.
     *
     * @return the server keep-alive, or empty if not set
     */
    public Optional<Integer> getServerKeepAlive() {
        return getInt(SERVER_KEEP_ALIVE);
    }

    /**
     * Returns the authentication method.
     *
     * @return the authentication method, or empty if not set
     */
    public Optional<String> getAuthenticationMethod() {
        return getString(AUTHENTICATION_METHOD);
    }

    /**
     * Returns the authentication data.
     *
     * @return the authentication data, or empty if not set
     */
    public Optional<byte[]> getAuthenticationData() {
        return getBytes(AUTHENTICATION_DATA);
    }

    /**
     * Returns the topic alias.
     *
     * @return the topic alias, or empty if not set
     */
    public Optional<Integer> getTopicAlias() {
        return getInt(TOPIC_ALIAS);
    }

    /**
     * Returns the maximum QoS level supported by the server.
     *
     * @return the maximum QoS, or empty if not set
     */
    public Optional<Integer> getMaximumQos() {
        return getInt(MAXIMUM_QOS);
    }

    /**
     * Returns whether retained messages are available on the server.
     *
     * @return retain available flag, or empty if not set
     */
    public Optional<Boolean> getRetainAvailable() {
        return getInt(RETAIN_AVAILABLE).map(v -> v != 0);
    }

    /**
     * Returns the user properties as an unmodifiable list.
     *
     * @return the user properties
     */
    public List<UserProperty> getUserProperties() {
        return Collections.unmodifiableList(userProperties);
    }

    /**
     * Returns the maximum packet size.
     *
     * @return the maximum packet size, or empty if not set
     */
    public Optional<Long> getMaximumPacketSize() {
        return getLong(MAXIMUM_PACKET_SIZE);
    }

    /**
     * Returns whether wildcard subscriptions are available.
     *
     * @return wildcard subscription available flag, or empty if not set
     */
    public Optional<Boolean> getWildcardSubscriptionAvailable() {
        return getInt(WILDCARD_SUBSCRIPTION_AVAILABLE).map(v -> v != 0);
    }

    /**
     * Returns whether subscription identifiers are available.
     *
     * @return subscription identifier available flag, or empty if not set
     */
    public Optional<Boolean> getSubscriptionIdentifierAvailable() {
        return getInt(SUBSCRIPTION_IDENTIFIER_AVAILABLE).map(v -> v != 0);
    }

    /**
     * Returns whether shared subscriptions are available.
     *
     * @return shared subscription available flag, or empty if not set
     */
    public Optional<Boolean> getSharedSubscriptionAvailable() {
        return getInt(SHARED_SUBSCRIPTION_AVAILABLE).map(v -> v != 0);
    }

    // --- Typed setters ---

    /**
     * Sets the payload format indicator.
     *
     * @param value 0 for unspecified bytes, 1 for UTF-8 encoded
     * @return this properties instance for chaining
     */
    public MqttProperties setPayloadFormatIndicator(int value) {
        properties.put(PAYLOAD_FORMAT_INDICATOR, value);
        return this;
    }

    /**
     * Sets the message expiry interval in seconds.
     *
     * @param seconds the expiry interval
     * @return this properties instance for chaining
     */
    public MqttProperties setMessageExpiryInterval(long seconds) {
        properties.put(MESSAGE_EXPIRY_INTERVAL, seconds);
        return this;
    }

    /**
     * Sets the content type string.
     *
     * @param contentType the MIME content type
     * @return this properties instance for chaining
     */
    public MqttProperties setContentType(String contentType) {
        properties.put(CONTENT_TYPE, contentType);
        return this;
    }

    /**
     * Sets the response topic.
     *
     * @param topic the response topic
     * @return this properties instance for chaining
     */
    public MqttProperties setResponseTopic(String topic) {
        properties.put(RESPONSE_TOPIC, topic);
        return this;
    }

    /**
     * Sets the correlation data.
     *
     * @param data the correlation data bytes
     * @return this properties instance for chaining
     */
    public MqttProperties setCorrelationData(byte[] data) {
        properties.put(CORRELATION_DATA, data);
        return this;
    }

    /**
     * Sets the session expiry interval in seconds.
     *
     * @param seconds the session expiry interval
     * @return this properties instance for chaining
     */
    public MqttProperties setSessionExpiryInterval(long seconds) {
        properties.put(SESSION_EXPIRY_INTERVAL, seconds);
        return this;
    }

    /**
     * Sets the assigned client identifier.
     *
     * @param clientId the assigned client identifier
     * @return this properties instance for chaining
     */
    public MqttProperties setAssignedClientIdentifier(String clientId) {
        properties.put(ASSIGNED_CLIENT_IDENTIFIER, clientId);
        return this;
    }

    /**
     * Sets the server keep-alive value in seconds.
     *
     * @param seconds the server keep-alive
     * @return this properties instance for chaining
     */
    public MqttProperties setServerKeepAlive(int seconds) {
        properties.put(SERVER_KEEP_ALIVE, seconds);
        return this;
    }

    /**
     * Sets the authentication method.
     *
     * @param method the authentication method
     * @return this properties instance for chaining
     */
    public MqttProperties setAuthenticationMethod(String method) {
        properties.put(AUTHENTICATION_METHOD, method);
        return this;
    }

    /**
     * Sets the authentication data.
     *
     * @param data the authentication data bytes
     * @return this properties instance for chaining
     */
    public MqttProperties setAuthenticationData(byte[] data) {
        properties.put(AUTHENTICATION_DATA, data);
        return this;
    }

    /**
     * Sets the topic alias.
     *
     * @param alias the topic alias value
     * @return this properties instance for chaining
     */
    public MqttProperties setTopicAlias(int alias) {
        properties.put(TOPIC_ALIAS, alias);
        return this;
    }

    /**
     * Sets the maximum QoS supported by the server.
     *
     * @param maxQos the maximum QoS (0, 1, or 2)
     * @return this properties instance for chaining
     */
    public MqttProperties setMaximumQos(int maxQos) {
        properties.put(MAXIMUM_QOS, maxQos);
        return this;
    }

    /**
     * Sets the retain available flag.
     *
     * @param available whether retained messages are available
     * @return this properties instance for chaining
     */
    public MqttProperties setRetainAvailable(boolean available) {
        properties.put(RETAIN_AVAILABLE, available ? 1 : 0);
        return this;
    }

    /**
     * Adds a user property (key-value pair).
     *
     * @param key   the property key
     * @param value the property value
     * @return this properties instance for chaining
     */
    public MqttProperties addUserProperty(String key, String value) {
        userProperties.add(new UserProperty(key, value));
        return this;
    }

    /**
     * Sets the maximum packet size.
     *
     * @param size the maximum packet size in bytes
     * @return this properties instance for chaining
     */
    public MqttProperties setMaximumPacketSize(long size) {
        properties.put(MAXIMUM_PACKET_SIZE, size);
        return this;
    }

    /**
     * Sets the wildcard subscription available flag.
     *
     * @param available whether wildcard subscriptions are available
     * @return this properties instance for chaining
     */
    public MqttProperties setWildcardSubscriptionAvailable(boolean available) {
        properties.put(WILDCARD_SUBSCRIPTION_AVAILABLE, available ? 1 : 0);
        return this;
    }

    /**
     * Sets the subscription identifier available flag.
     *
     * @param available whether subscription identifiers are available
     * @return this properties instance for chaining
     */
    public MqttProperties setSubscriptionIdentifierAvailable(boolean available) {
        properties.put(SUBSCRIPTION_IDENTIFIER_AVAILABLE, available ? 1 : 0);
        return this;
    }

    /**
     * Sets the shared subscription available flag.
     *
     * @param available whether shared subscriptions are available
     * @return this properties instance for chaining
     */
    public MqttProperties setSharedSubscriptionAvailable(boolean available) {
        properties.put(SHARED_SUBSCRIPTION_AVAILABLE, available ? 1 : 0);
        return this;
    }

    /**
     * Returns whether this container has any properties set.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return properties.isEmpty() && userProperties.isEmpty();
    }

    /**
     * Encodes all properties into a {@link ByteBuffer}.
     *
     * @return the encoded properties
     */
    public ByteBuffer encode() {
        var buf = ByteBuffer.allocate(4096);
        for (var entry : properties.entrySet()) {
            int id = entry.getKey();
            Object value = entry.getValue();
            switch (id) {
                case PAYLOAD_FORMAT_INDICATOR, MAXIMUM_QOS, RETAIN_AVAILABLE,
                     WILDCARD_SUBSCRIPTION_AVAILABLE, SUBSCRIPTION_IDENTIFIER_AVAILABLE,
                     SHARED_SUBSCRIPTION_AVAILABLE -> {
                    buf.put((byte) id);
                    buf.put(((Integer) value).byteValue());
                }
                case SERVER_KEEP_ALIVE, TOPIC_ALIAS -> {
                    buf.put((byte) id);
                    buf.putShort(((Integer) value).shortValue());
                }
                case MESSAGE_EXPIRY_INTERVAL, SESSION_EXPIRY_INTERVAL, MAXIMUM_PACKET_SIZE -> {
                    buf.put((byte) id);
                    buf.putInt(((Long) value).intValue());
                }
                case CONTENT_TYPE, RESPONSE_TOPIC, ASSIGNED_CLIENT_IDENTIFIER,
                     AUTHENTICATION_METHOD -> {
                    buf.put((byte) id);
                    encodeUtf8String(buf, (String) value);
                }
                case CORRELATION_DATA, AUTHENTICATION_DATA -> {
                    buf.put((byte) id);
                    byte[] data = (byte[]) value;
                    buf.putShort((short) data.length);
                    buf.put(data);
                }
                default -> { /* skip unknown */ }
            }
        }
        for (var up : userProperties) {
            buf.put((byte) USER_PROPERTY);
            encodeUtf8String(buf, up.key());
            encodeUtf8String(buf, up.value());
        }
        buf.flip();
        return buf;
    }

    /**
     * Decodes properties from a {@link ByteBuffer}.
     *
     * @param buf    the buffer to decode from
     * @param length the number of bytes to read
     * @return the decoded properties
     */
    public static MqttProperties decode(ByteBuffer buf, int length) {
        var props = new MqttProperties();
        int endPos = buf.position() + length;
        while (buf.position() < endPos) {
            int id = buf.get() & 0xFF;
            switch (id) {
                case PAYLOAD_FORMAT_INDICATOR, MAXIMUM_QOS, RETAIN_AVAILABLE,
                     WILDCARD_SUBSCRIPTION_AVAILABLE, SUBSCRIPTION_IDENTIFIER_AVAILABLE,
                     SHARED_SUBSCRIPTION_AVAILABLE -> props.properties.put(id, (int) (buf.get() & 0xFF));
                case SERVER_KEEP_ALIVE, TOPIC_ALIAS -> props.properties.put(id, (int) (buf.getShort() & 0xFFFF));
                case MESSAGE_EXPIRY_INTERVAL, SESSION_EXPIRY_INTERVAL, MAXIMUM_PACKET_SIZE ->
                        props.properties.put(id, buf.getInt() & 0xFFFFFFFFL);
                case CONTENT_TYPE, RESPONSE_TOPIC, ASSIGNED_CLIENT_IDENTIFIER,
                     AUTHENTICATION_METHOD -> props.properties.put(id, decodeUtf8String(buf));
                case CORRELATION_DATA, AUTHENTICATION_DATA -> {
                    int len = buf.getShort() & 0xFFFF;
                    byte[] data = new byte[len];
                    buf.get(data);
                    props.properties.put(id, data);
                }
                case USER_PROPERTY -> {
                    String key = decodeUtf8String(buf);
                    String value = decodeUtf8String(buf);
                    props.userProperties.add(new UserProperty(key, value));
                }
                default -> throw new IllegalArgumentException("Unknown property ID: 0x" + Integer.toHexString(id));
            }
        }
        return props;
    }

    private static void encodeUtf8String(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    private static String decodeUtf8String(ByteBuffer buf) {
        int len = buf.getShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // --- Private helpers ---

    private Optional<Integer> getInt(int propertyId) {
        Object v = properties.get(propertyId);
        return v instanceof Integer i ? Optional.of(i) : Optional.empty();
    }

    private Optional<Long> getLong(int propertyId) {
        Object v = properties.get(propertyId);
        return v instanceof Long l ? Optional.of(l) : Optional.empty();
    }

    private Optional<String> getString(int propertyId) {
        Object v = properties.get(propertyId);
        return v instanceof String s ? Optional.of(s) : Optional.empty();
    }

    private Optional<byte[]> getBytes(int propertyId) {
        Object v = properties.get(propertyId);
        return v instanceof byte[] b ? Optional.of(b) : Optional.empty();
    }

    /**
     * A user property key-value pair.
     *
     * @param key   the property key
     * @param value the property value
     * @since 0.1.0
     */
    public record UserProperty(String key, String value) {
    }
}
