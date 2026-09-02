package ssg.legoflow.http3;

import ssg.legoflow.http3.quic.QuicPacketCodec;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * HTTP/3 SETTINGS parameters as defined in RFC 9114 section 7.2.4.
 *
 * <p>HTTP/3 settings are exchanged on the control stream using SETTINGS
 * frames. Unlike HTTP/2, there is no acknowledgement — settings take
 * effect immediately upon receipt.</p>
 *
 * @since 0.1.0
 */
public class Http3Settings {

    /** Maximum size of a header field section (RFC 9114). */
    public static final long SETTINGS_MAX_FIELD_SECTION_SIZE = 0x06;

    /** Maximum dynamic table capacity for QPACK (RFC 9204). */
    public static final long SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x01;

    /** Maximum number of blocked streams for QPACK (RFC 9204). */
    public static final long SETTINGS_QPACK_BLOCKED_STREAMS = 0x07;

    /** Default maximum field section size. */
    public static final int DEFAULT_MAX_FIELD_SECTION_SIZE = 8192;

    /** Default QPACK maximum table capacity. */
    public static final int DEFAULT_QPACK_MAX_TABLE_CAPACITY = 4096;

    /** Default QPACK blocked streams. */
    public static final int DEFAULT_QPACK_BLOCKED_STREAMS = 100;

    private final Map<Long, Long> settings = new LinkedHashMap<>();

    /**
     * Creates settings with all default values.
     *
     * @since 0.1.0
     */
    public Http3Settings() {
        settings.put(SETTINGS_MAX_FIELD_SECTION_SIZE, (long) DEFAULT_MAX_FIELD_SECTION_SIZE);
        settings.put(SETTINGS_QPACK_MAX_TABLE_CAPACITY, (long) DEFAULT_QPACK_MAX_TABLE_CAPACITY);
        settings.put(SETTINGS_QPACK_BLOCKED_STREAMS, (long) DEFAULT_QPACK_BLOCKED_STREAMS);
    }

    /**
     * Returns the value of a setting.
     *
     * @param id the setting identifier
     * @return the setting value, or 0 if not set
     * @since 0.1.0
     */
    public long get(long id) {
        return settings.getOrDefault(id, 0L);
    }

    /**
     * Sets a setting value.
     *
     * @param id    the setting identifier
     * @param value the setting value
     * @return this settings instance for chaining
     * @since 0.1.0
     */
    public Http3Settings set(long id, long value) {
        settings.put(id, value);
        return this;
    }

    /**
     * Returns the maximum field section size.
     *
     * @return the maximum field section size
     * @since 0.1.0
     */
    public long maxFieldSectionSize() {
        return settings.get(SETTINGS_MAX_FIELD_SECTION_SIZE);
    }

    /**
     * Returns the QPACK maximum table capacity.
     *
     * @return the QPACK maximum table capacity
     * @since 0.1.0
     */
    public long qpackMaxTableCapacity() {
        return settings.get(SETTINGS_QPACK_MAX_TABLE_CAPACITY);
    }

    /**
     * Returns the QPACK blocked streams limit.
     *
     * @return the maximum number of blocked streams
     * @since 0.1.0
     */
    public long qpackBlockedStreams() {
        return settings.get(SETTINGS_QPACK_BLOCKED_STREAMS);
    }

    /**
     * Encodes these settings into wire format for a SETTINGS frame.
     *
     * <p>Each setting is a pair of variable-length integers: identifier and value.</p>
     *
     * @return a {@link ByteBuffer} containing the encoded settings
     * @since 0.1.0
     */
    public ByteBuffer encode() {
        var buf = ByteBuffer.allocate(settings.size() * 16);
        for (var entry : settings.entrySet()) {
            QuicPacketCodec.encodeVarInt(buf, entry.getKey());
            QuicPacketCodec.encodeVarInt(buf, entry.getValue());
        }
        buf.flip();
        return buf;
    }

    /**
     * Decodes settings from wire format.
     *
     * @param data the encoded settings data
     * @return a new {@code Http3Settings} instance
     * @since 0.1.0
     */
    public static Http3Settings decode(ByteBuffer data) {
        var settings = new Http3Settings();
        var buf = data.duplicate();
        while (buf.hasRemaining()) {
            long id = QuicPacketCodec.decodeVarInt(buf);
            long value = QuicPacketCodec.decodeVarInt(buf);
            settings.set(id, value);
        }
        return settings;
    }

    /**
     * Applies settings from another instance, overwriting existing values.
     *
     * @param other the settings to apply
     * @since 0.1.0
     */
    public void applyFrom(Http3Settings other) {
        for (var entry : other.settings.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Returns an unmodifiable copy of all settings.
     *
     * @return a map of setting identifiers to values
     * @since 0.1.0
     */
    public Map<Long, Long> toMap() {
        return Map.copyOf(settings);
    }

    /**
     * Returns a new builder for constructing settings.
     *
     * @return a new builder
     * @since 0.1.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link Http3Settings}.
     *
     * @since 0.1.0
     */
    public static class Builder {

        private long maxFieldSectionSize = DEFAULT_MAX_FIELD_SECTION_SIZE;
        private long qpackMaxTableCapacity = DEFAULT_QPACK_MAX_TABLE_CAPACITY;
        private long qpackBlockedStreams = DEFAULT_QPACK_BLOCKED_STREAMS;

        /**
         * Sets the maximum field section size.
         *
         * @param maxFieldSectionSize the maximum size
         * @return this builder
         * @since 0.1.0
         */
        public Builder maxFieldSectionSize(long maxFieldSectionSize) {
            this.maxFieldSectionSize = maxFieldSectionSize;
            return this;
        }

        /**
         * Sets the QPACK maximum table capacity.
         *
         * @param qpackMaxTableCapacity the table capacity
         * @return this builder
         * @since 0.1.0
         */
        public Builder qpackMaxTableCapacity(long qpackMaxTableCapacity) {
            this.qpackMaxTableCapacity = qpackMaxTableCapacity;
            return this;
        }

        /**
         * Sets the QPACK blocked streams limit.
         *
         * @param qpackBlockedStreams the blocked streams limit
         * @return this builder
         * @since 0.1.0
         */
        public Builder qpackBlockedStreams(long qpackBlockedStreams) {
            this.qpackBlockedStreams = qpackBlockedStreams;
            return this;
        }

        /**
         * Builds the settings instance.
         *
         * @return a new {@code Http3Settings}
         * @since 0.1.0
         */
        public Http3Settings build() {
            var settings = new Http3Settings();
            settings.set(SETTINGS_MAX_FIELD_SECTION_SIZE, maxFieldSectionSize);
            settings.set(SETTINGS_QPACK_MAX_TABLE_CAPACITY, qpackMaxTableCapacity);
            settings.set(SETTINGS_QPACK_BLOCKED_STREAMS, qpackBlockedStreams);
            return settings;
        }
    }
}
