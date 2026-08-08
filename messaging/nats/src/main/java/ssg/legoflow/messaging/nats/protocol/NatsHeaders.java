package ssg.legoflow.messaging.nats.protocol;

import java.util.*;

/**
 * NATS message headers using the NATS/1.0 header format.
 *
 * <p>Headers follow the format:
 * <pre>
 * NATS/1.0 [status] [description]\r\n
 * Key: Value\r\n
 * Key: Value\r\n
 * \r\n
 * </pre>
 *
 * <p>Header keys are case-insensitive for lookup but preserve original case.
 *
 * @since 0.1.0
 */
public final class NatsHeaders {

    private final Map<String, List<String>> headers;
    private NatsStatus status;
    private String statusDescription;

    /**
     * Creates empty headers.
     */
    public NatsHeaders() {
        this.headers = new LinkedHashMap<>();
    }

    /**
     * Creates headers with a status line.
     *
     * @param status      the status code
     * @param description the status description
     */
    public NatsHeaders(NatsStatus status, String description) {
        this.headers = new LinkedHashMap<>();
        this.status = status;
        this.statusDescription = description;
    }

    /**
     * Adds a header value. Multiple values per key are supported.
     *
     * @param key   the header key
     * @param value the header value
     * @return this headers instance for chaining
     */
    public NatsHeaders add(String key, String value) {
        headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * Sets a header value, replacing any existing values for the key.
     *
     * @param key   the header key
     * @param value the header value
     * @return this headers instance for chaining
     */
    public NatsHeaders set(String key, String value) {
        var list = new ArrayList<String>();
        list.add(value);
        headers.put(key, list);
        return this;
    }

    /**
     * Returns the first value for a header key (case-insensitive).
     *
     * @param key the header key
     * @return the first value, or null if not present
     */
    public String getFirst(String key) {
        for (var entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                var values = entry.getValue();
                return values.isEmpty() ? null : values.getFirst();
            }
        }
        return null;
    }

    /**
     * Returns all values for a header key (case-insensitive).
     *
     * @param key the header key
     * @return list of values, or empty list if not present
     */
    public List<String> getAll(String key) {
        for (var entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return Collections.unmodifiableList(entry.getValue());
            }
        }
        return List.of();
    }

    /**
     * Returns whether a header key is present.
     *
     * @param key the header key
     * @return true if present
     */
    public boolean contains(String key) {
        for (var k : headers.keySet()) {
            if (k.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    /**
     * Returns the header status, if any.
     *
     * @return the status or null
     */
    public NatsStatus status() {
        return status;
    }

    /**
     * Returns the status description, if any.
     *
     * @return the description or null
     */
    public String statusDescription() {
        return statusDescription;
    }

    /**
     * Returns all header keys.
     *
     * @return unmodifiable set of keys
     */
    public Set<String> keys() {
        return Collections.unmodifiableSet(headers.keySet());
    }

    /**
     * Returns whether these headers are empty (no keys).
     *
     * @return true if no headers
     */
    public boolean isEmpty() {
        return headers.isEmpty() && status == null;
    }

    /**
     * Returns the number of header keys.
     *
     * @return the size
     */
    public int size() {
        return headers.size();
    }

    /**
     * Serializes these headers to the NATS/1.0 format.
     *
     * @return the serialized header block including trailing CRLF
     */
    public String serialize() {
        var sb = new StringBuilder();
        sb.append(NatsProtocol.HDR_VERSION);
        if (status != null) {
            sb.append(' ').append(status.code());
            if (statusDescription != null) {
                sb.append(' ').append(statusDescription);
            }
        }
        sb.append(NatsProtocol.CRLF);
        for (var entry : headers.entrySet()) {
            for (var value : entry.getValue()) {
                sb.append(entry.getKey()).append(": ").append(value).append(NatsProtocol.CRLF);
            }
        }
        sb.append(NatsProtocol.CRLF);
        return sb.toString();
    }

    /**
     * Parses a NATS/1.0 header block.
     *
     * @param headerBlock the raw header text
     * @return parsed headers
     * @throws IllegalArgumentException if the header block is malformed
     */
    public static NatsHeaders parse(String headerBlock) {
        if (headerBlock == null || headerBlock.isEmpty()) {
            throw new IllegalArgumentException("Empty header block");
        }

        var headers = new NatsHeaders();
        var lines = headerBlock.split("\r\n");

        if (lines.length == 0 || !lines[0].startsWith(NatsProtocol.HDR_VERSION)) {
            throw new IllegalArgumentException("Invalid header version: " + (lines.length > 0 ? lines[0] : ""));
        }

        // Parse status line: NATS/1.0 [code] [description]
        String statusLine = lines[0].substring(NatsProtocol.HDR_VERSION.length()).trim();
        if (!statusLine.isEmpty()) {
            int spaceIdx = statusLine.indexOf(' ');
            String codeStr = spaceIdx > 0 ? statusLine.substring(0, spaceIdx) : statusLine;
            try {
                int code = Integer.parseInt(codeStr);
                headers.status = NatsStatus.fromCode(code);
                if (spaceIdx > 0) {
                    headers.statusDescription = statusLine.substring(spaceIdx + 1);
                }
            } catch (NumberFormatException e) {
                // No status code
            }
        }

        // Parse header key-value pairs
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) break;
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                headers.add(key, value);
            }
        }

        return headers;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
