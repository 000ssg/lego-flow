package ssg.legoflow.messaging.amqp.message;

import ssg.legoflow.messaging.amqp.types.AmqpType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a complete AMQP 1.0 message with all sections.
 *
 * <p>An AMQP message consists of:
 * <ol>
 *   <li>Header (transport-related properties)</li>
 *   <li>Delivery annotations (per-hop key-value pairs)</li>
 *   <li>Message annotations (infrastructure key-value pairs)</li>
 *   <li>Properties (immutable application-level properties)</li>
 *   <li>Application properties (application key-value pairs)</li>
 *   <li>Body (Data, AmqpSequence, or AmqpValue)</li>
 *   <li>Footer (post-body annotations)</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class AmqpMessage {

    private Header header;
    private Map<String, Object> deliveryAnnotations;
    private Map<String, Object> messageAnnotations;
    private Properties properties;
    private Map<String, Object> applicationProperties;
    private AmqpType body;
    private BodyType bodyType;
    private Map<String, Object> footer;

    /**
     * Body section types.
     */
    public enum BodyType {
        /** Binary data (one or more data sections). */
        DATA,
        /** AMQP sequence (list). */
        AMQP_SEQUENCE,
        /** AMQP value (any AMQP type). */
        AMQP_VALUE
    }

    /** Creates an empty message. */
    public AmqpMessage() {
        this.deliveryAnnotations = new LinkedHashMap<>();
        this.messageAnnotations = new LinkedHashMap<>();
        this.applicationProperties = new LinkedHashMap<>();
        this.footer = new LinkedHashMap<>();
        this.bodyType = BodyType.AMQP_VALUE;
    }

    // ---- Getters ----

    /** Returns the header, or null if not set. */
    public Header header() { return header; }

    /** Returns delivery annotations. */
    public Map<String, Object> deliveryAnnotations() { return deliveryAnnotations; }

    /** Returns message annotations. */
    public Map<String, Object> messageAnnotations() { return messageAnnotations; }

    /** Returns properties, or null if not set. */
    public Properties properties() { return properties; }

    /** Returns application properties. */
    public Map<String, Object> applicationProperties() { return applicationProperties; }

    /** Returns the body. */
    public AmqpType body() { return body; }

    /** Returns the body type. */
    public BodyType bodyType() { return bodyType; }

    /** Returns the footer. */
    public Map<String, Object> footer() { return footer; }

    // ---- Setters (fluent) ----

    /** Sets the header. */
    public AmqpMessage header(Header header) { this.header = header; return this; }

    /** Sets delivery annotations. */
    public AmqpMessage deliveryAnnotations(Map<String, Object> deliveryAnnotations) {
        this.deliveryAnnotations = Objects.requireNonNull(deliveryAnnotations);
        return this;
    }

    /** Sets message annotations. */
    public AmqpMessage messageAnnotations(Map<String, Object> messageAnnotations) {
        this.messageAnnotations = Objects.requireNonNull(messageAnnotations);
        return this;
    }

    /** Sets properties. */
    public AmqpMessage properties(Properties properties) { this.properties = properties; return this; }

    /** Sets application properties. */
    public AmqpMessage applicationProperties(Map<String, Object> applicationProperties) {
        this.applicationProperties = Objects.requireNonNull(applicationProperties);
        return this;
    }

    /** Sets the body as binary data. */
    public AmqpMessage bodyData(byte[] data) {
        this.body = new AmqpType.Binary(data);
        this.bodyType = BodyType.DATA;
        return this;
    }

    /** Sets the body as an AMQP value. */
    public AmqpMessage bodyValue(AmqpType value) {
        this.body = value;
        this.bodyType = BodyType.AMQP_VALUE;
        return this;
    }

    /** Sets the body as a string value. */
    public AmqpMessage bodyString(String value) {
        return bodyValue(new AmqpType.AmqpString(value));
    }

    /** Sets the body as an AMQP sequence (list). */
    public AmqpMessage bodySequence(java.util.List<AmqpType> elements) {
        this.body = new AmqpType.AmqpList(elements);
        this.bodyType = BodyType.AMQP_SEQUENCE;
        return this;
    }

    /** Sets the footer. */
    public AmqpMessage footer(Map<String, Object> footer) {
        this.footer = Objects.requireNonNull(footer);
        return this;
    }

    /**
     * Convenience: extracts the body as a string if it is an AmqpString.
     *
     * @return the string body, or null
     */
    public String bodyAsString() {
        if (body instanceof AmqpType.AmqpString str) return str.value();
        return null;
    }

    /**
     * Convenience: extracts the body as bytes if it is Binary.
     *
     * @return the binary body, or null
     */
    public byte[] bodyAsBytes() {
        if (body instanceof AmqpType.Binary bin) return bin.value();
        return null;
    }

    /**
     * Creates a simple message with a string body.
     *
     * @param text the message text
     * @return a new message
     */
    public static AmqpMessage of(String text) {
        return new AmqpMessage().bodyString(text);
    }

    /**
     * Creates a simple message with binary data.
     *
     * @param data the message data
     * @return a new message
     */
    public static AmqpMessage of(byte[] data) {
        return new AmqpMessage().bodyData(data);
    }

    /**
     * Creates a message with properties and a string body.
     *
     * @param properties the message properties
     * @param text       the message text
     * @return a new message
     */
    public static AmqpMessage of(Properties properties, String text) {
        return new AmqpMessage().properties(properties).bodyString(text);
    }
}
