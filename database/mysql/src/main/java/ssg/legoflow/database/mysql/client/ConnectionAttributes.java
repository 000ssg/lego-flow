package ssg.legoflow.database.mysql.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MySQL connection attributes sent during handshake.
 *
 * <p>Connection attributes are key-value pairs that the client sends
 * to the server for informational purposes (monitoring, debugging).
 *
 * @since 0.1.0
 */
public final class ConnectionAttributes {

    private ConnectionAttributes() {}

    /**
     * Returns the default connection attributes.
     *
     * @return map of attribute key-value pairs
     */
    public static Map<String, String> defaults() {
        var attrs = new LinkedHashMap<String, String>();
        attrs.put("_client_name", "lego-flow-mysql");
        attrs.put("_client_version", "1.0.0");
        attrs.put("_os", System.getProperty("os.name", "unknown"));
        attrs.put("_platform", System.getProperty("os.arch", "unknown"));
        attrs.put("_pid", String.valueOf(ProcessHandle.current().pid()));
        return attrs;
    }

    /**
     * Returns connection attributes with a custom client name.
     *
     * @param clientName the client application name
     * @return map of attribute key-value pairs
     */
    public static Map<String, String> withClientName(String clientName) {
        var attrs = defaults();
        attrs.put("_client_name", clientName);
        return attrs;
    }
}
