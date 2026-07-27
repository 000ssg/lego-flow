package ssg.legoflow.messaging.nats.protocol;

/**
 * Server INFO payload sent as JSON in the INFO operation.
 *
 * <p>Contains server identification, capability flags, and connection limits.
 * Sent by the server immediately upon client connection.
 *
 * @param serverId    unique server identifier
 * @param serverName  human-readable server name
 * @param version     server software version
 * @param host        server host address
 * @param port        server port
 * @param maxPayload  maximum allowed payload size in bytes
 * @param proto       protocol version (0 or 1)
 * @param headers     whether server supports headers (HMSG)
 * @param jetstream   whether JetStream is enabled
 * @param authRequired whether authentication is required
 * @param tlsRequired whether TLS is required
 * @param clientId    assigned client ID
 * @since 1.0.0
 */
public record ServerInfo(
        String serverId,
        String serverName,
        String version,
        String host,
        int port,
        int maxPayload,
        int proto,
        boolean headers,
        boolean jetstream,
        boolean authRequired,
        boolean tlsRequired,
        long clientId
) {

    /**
     * Creates a default server info with common defaults.
     *
     * @param serverId   unique server identifier
     * @param serverName human-readable server name
     * @param port       server port
     * @return a server info with sensible defaults
     */
    public static ServerInfo withDefaults(String serverId, String serverName, int port) {
        return new ServerInfo(
                serverId, serverName, NatsProtocol.VERSION,
                "0.0.0.0", port, NatsProtocol.DEFAULT_MAX_PAYLOAD,
                NatsProtocol.PROTOCOL_VERSION, true, true,
                false, false, 0
        );
    }

    /**
     * Returns a copy with the specified client ID.
     *
     * @param clientId the client ID to assign
     * @return new server info with updated client ID
     */
    public ServerInfo withClientId(long clientId) {
        return new ServerInfo(serverId, serverName, version, host, port,
                maxPayload, proto, headers, jetstream, authRequired, tlsRequired, clientId);
    }

    /**
     * Returns a copy with auth required flag set.
     *
     * @param required whether auth is required
     * @return new server info with updated auth flag
     */
    public ServerInfo withAuthRequired(boolean required) {
        return new ServerInfo(serverId, serverName, version, host, port,
                maxPayload, proto, headers, jetstream, required, tlsRequired, clientId);
    }

    /**
     * Encodes this server info as a JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
        var sb = new StringBuilder(256);
        sb.append('{');
        appendString(sb, "server_id", serverId);
        sb.append(',');
        appendString(sb, "server_name", serverName);
        sb.append(',');
        appendString(sb, "version", version);
        sb.append(',');
        appendString(sb, "host", host);
        sb.append(',');
        appendInt(sb, "port", port);
        sb.append(',');
        appendInt(sb, "max_payload", maxPayload);
        sb.append(',');
        appendInt(sb, "proto", proto);
        sb.append(',');
        appendBool(sb, "headers", headers);
        sb.append(',');
        appendBool(sb, "jetstream", jetstream);
        sb.append(',');
        appendBool(sb, "auth_required", authRequired);
        sb.append(',');
        appendBool(sb, "tls_required", tlsRequired);
        sb.append(',');
        appendLong(sb, "client_id", clientId);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses a JSON string into a ServerInfo record.
     *
     * @param json the JSON string
     * @return parsed server info
     */
    public static ServerInfo fromJson(String json) {
        String serverId = extractString(json, "server_id", "");
        String serverName = extractString(json, "server_name", "");
        String version = extractString(json, "version", "");
        String host = extractString(json, "host", "0.0.0.0");
        int port = extractInt(json, "port", NatsProtocol.DEFAULT_PORT);
        int maxPayload = extractInt(json, "max_payload", NatsProtocol.DEFAULT_MAX_PAYLOAD);
        int proto = extractInt(json, "proto", 0);
        boolean headers = extractBool(json, "headers");
        boolean jetstream = extractBool(json, "jetstream");
        boolean authRequired = extractBool(json, "auth_required");
        boolean tlsRequired = extractBool(json, "tls_required");
        long clientId = extractLong(json, "client_id", 0);
        return new ServerInfo(serverId, serverName, version, host, port,
                maxPayload, proto, headers, jetstream, authRequired, tlsRequired, clientId);
    }

    // --- JSON helpers ---

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"").append(value != null ? value : "").append('"');
    }

    private static void appendInt(StringBuilder sb, String key, int value) {
        sb.append('"').append(key).append("\":").append(value);
    }

    private static void appendLong(StringBuilder sb, String key, long value) {
        sb.append('"').append(key).append("\":").append(value);
    }

    private static void appendBool(StringBuilder sb, String key, boolean value) {
        sb.append('"').append(key).append("\":").append(value);
    }

    public static String extractString(String json, String key, String defaultValue) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return defaultValue;
        int start = idx + search.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : defaultValue;
    }

    public static int extractInt(String json, String key, int defaultValue) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return defaultValue;
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) return defaultValue;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long extractLong(String json, String key, long defaultValue) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return defaultValue;
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) return defaultValue;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean extractBool(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return false;
        int start = idx + search.length();
        return json.regionMatches(start, "true", 0, 4);
    }
}
