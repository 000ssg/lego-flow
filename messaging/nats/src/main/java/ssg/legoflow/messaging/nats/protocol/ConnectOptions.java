package ssg.legoflow.messaging.nats.protocol;

/**
 * CONNECT payload sent by the client as JSON.
 *
 * <p>Contains client identification, authentication credentials, and
 * protocol capability negotiation flags.
 *
 * @param verbose      whether the server should send +OK for each operation
 * @param pedantic     whether the server should perform strict subject checking
 * @param tlsRequired  whether TLS is required by the client
 * @param authToken    token-based authentication credential
 * @param user         username for user/pass authentication
 * @param pass         password for user/pass authentication
 * @param name         client connection name
 * @param lang         client implementation language
 * @param version      client library version
 * @param protocol     protocol version (0 or 1)
 * @param echo         whether the server should echo published messages back
 * @param headers      whether the client supports headers (HPUB/HMSG)
 * @param noResponders whether the server should send no-responders status
 * @since 0.1.0
 */
public record ConnectOptions(
        boolean verbose,
        boolean pedantic,
        boolean tlsRequired,
        String authToken,
        String user,
        String pass,
        String name,
        String lang,
        String version,
        int protocol,
        boolean echo,
        boolean headers,
        boolean noResponders
) {

    /**
     * Creates default connect options with standard settings.
     *
     * @param name the client name
     * @return connect options with sensible defaults
     */
    public static ConnectOptions withDefaults(String name) {
        return new ConnectOptions(
                false, false, false,
                null, null, null,
                name, NatsProtocol.LANG, NatsProtocol.VERSION,
                NatsProtocol.PROTOCOL_VERSION,
                true, true, true
        );
    }

    /**
     * Returns a copy with token authentication.
     *
     * @param token the auth token
     * @return new options with token set
     */
    public ConnectOptions withToken(String token) {
        return new ConnectOptions(verbose, pedantic, tlsRequired,
                token, user, pass, name, lang, version, protocol, echo, headers, noResponders);
    }

    /**
     * Returns a copy with user/pass authentication.
     *
     * @param user the username
     * @param pass the password
     * @return new options with credentials set
     */
    public ConnectOptions withUserPass(String user, String pass) {
        return new ConnectOptions(verbose, pedantic, tlsRequired,
                authToken, user, pass, name, lang, version, protocol, echo, headers, noResponders);
    }

    /**
     * Returns a copy with verbose mode.
     *
     * @param verbose whether to enable verbose mode
     * @return new options with verbose flag set
     */
    public ConnectOptions withVerbose(boolean verbose) {
        return new ConnectOptions(verbose, pedantic, tlsRequired,
                authToken, user, pass, name, lang, version, protocol, echo, headers, noResponders);
    }

    /**
     * Encodes this connect options as a JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
        var sb = new StringBuilder(256);
        sb.append('{');
        appendBool(sb, "verbose", verbose);
        sb.append(',');
        appendBool(sb, "pedantic", pedantic);
        sb.append(',');
        appendBool(sb, "tls_required", tlsRequired);
        if (authToken != null) {
            sb.append(',');
            appendString(sb, "auth_token", authToken);
        }
        if (user != null) {
            sb.append(',');
            appendString(sb, "user", user);
        }
        if (pass != null) {
            sb.append(',');
            appendString(sb, "pass", pass);
        }
        if (name != null) {
            sb.append(',');
            appendString(sb, "name", name);
        }
        sb.append(',');
        appendString(sb, "lang", lang);
        sb.append(',');
        appendString(sb, "version", version);
        sb.append(',');
        appendInt(sb, "protocol", protocol);
        sb.append(',');
        appendBool(sb, "echo", echo);
        sb.append(',');
        appendBool(sb, "headers", headers);
        sb.append(',');
        appendBool(sb, "no_responders", noResponders);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses a JSON string into ConnectOptions.
     *
     * @param json the JSON string
     * @return parsed connect options
     */
    public static ConnectOptions fromJson(String json) {
        boolean verbose = ServerInfo.extractBool(json, "verbose");
        boolean pedantic = ServerInfo.extractBool(json, "pedantic");
        boolean tlsRequired = ServerInfo.extractBool(json, "tls_required");
        String authToken = ServerInfo.extractString(json, "auth_token", null);
        String user = ServerInfo.extractString(json, "user", null);
        String pass = ServerInfo.extractString(json, "pass", null);
        String name = ServerInfo.extractString(json, "name", null);
        String lang = ServerInfo.extractString(json, "lang", "java");
        String version = ServerInfo.extractString(json, "version", "0.0.0");
        int protocol = ServerInfo.extractInt(json, "protocol", 0);
        boolean echo = ServerInfo.extractBool(json, "echo");
        boolean headers = ServerInfo.extractBool(json, "headers");
        boolean noResponders = ServerInfo.extractBool(json, "no_responders");

        // Handle empty string as null for optional fields
        if (authToken != null && authToken.isEmpty()) authToken = null;
        if (user != null && user.isEmpty()) user = null;
        if (pass != null && pass.isEmpty()) pass = null;
        if (name != null && name.isEmpty()) name = null;

        return new ConnectOptions(verbose, pedantic, tlsRequired,
                authToken, user, pass, name, lang, version, protocol,
                echo, headers, noResponders);
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"").append(value).append('"');
    }

    private static void appendInt(StringBuilder sb, String key, int value) {
        sb.append('"').append(key).append("\":").append(value);
    }

    private static void appendBool(StringBuilder sb, String key, boolean value) {
        sb.append('"').append(key).append("\":").append(value);
    }
}
