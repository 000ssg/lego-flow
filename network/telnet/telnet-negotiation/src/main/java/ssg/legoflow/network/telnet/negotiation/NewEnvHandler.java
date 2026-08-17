package ssg.legoflow.network.telnet.negotiation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for NEW_ENVY (New Environment) option subnegotiation (RFC 1408).
 *
 * <p>This is a partial implementation. Full NEW_ENVY support requires
 * tracking environment variables and responding to info requests.
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Only provides TERM, COLS, LINES by default</li>
 *   <li>No support for INFOMASK-based variable filtering</li>
 *   <li>No support for reading remote environment variables</li>
 *   <li>No support for BOOL info type</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class NewEnvHandler {

    /** NEW_ENV INFO — request or provide environment info. */
    private static final int NEW_ENV_INFO = 0;
    /** NEW_ENV IS — send environment variables. */
    private static final int NEW_ENV_IS = 1;
    /** NEW_ENV NO-PRODUCTS — not available. */
    private static final int NEW_ENV_NO_PRODUCTS = 2;

    /** Info mask for all variables. */
    private static final int INFO_ALL = 0xFF;

    private final Map<String, String> environment;

    private NewEnvHandler() {
        this.environment = new HashMap<>();
    }

    /**
     * Create a new NewEnvHandler with default environment variables.
     *
     * @param termType the terminal type (e.g., "xterm")
     * @param cols     terminal columns
     * @param rows     terminal rows
     */
    public static NewEnvHandler create(String termType, int cols, int rows) {
        NewEnvHandler handler = new NewEnvHandler();
        handler.environment.put("TERM", termType);
        handler.environment.put("COLS", String.valueOf(cols));
        handler.environment.put("LINES", String.valueOf(rows));
        return handler;
    }

    /**
     * Handle NEW_ENV subnegotiation data.
     *
     * @param data the subnegotiation bytes
     * @return response bytes to send back, or null if no response needed
     */
    public byte[] handle(List<Integer> data) {
        if (data.isEmpty()) return null;

        int command = data.get(0);
        return switch (command) {
            case NEW_ENV_INFO -> {
                // Peer requests environment info — build response with all variables
                byte[] result = new byte[1 + calculateSize()];
                int pos = 0;
                result[pos++] = NEW_ENV_IS;
                for (Map.Entry<String, String> entry : environment.entrySet()) {
                    String name = entry.getKey();
                    String value = entry.getValue();
                    System.arraycopy(name.getBytes(), 0, result, pos, name.length());
                    pos += name.length();
                    result[pos++] = (byte) value.length();
                    System.arraycopy(value.getBytes(), 0, result, pos, value.length());
                    pos += value.length();
                }
                yield result;
            }
            case NEW_ENV_IS -> {
                // Peer sends environment variables — store them
                parseEnvironment(data.subList(1, data.size()));
                yield null;
            }
            case NEW_ENV_NO_PRODUCTS -> {
                // Not available
                yield null;
            }
            default -> null;
        };
    }

    /** Parse NEW_ENV IS data into environment variables. */
    private void parseEnvironment(List<Integer> data) {
        if (data.size() < 2) return;
        int pos = 0;
        // First byte is INFOMASK
        pos++;
        while (pos < data.size()) {
            // Read variable name length
            int nameLen = data.get(pos++);
            if (pos + nameLen > data.size()) break;
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < nameLen; i++) {
                name.append((char) (int) data.get(pos++));
            }
            // Read value length
            int valueLen = data.get(pos++);
            if (pos + valueLen > data.size()) break;
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < valueLen; i++) {
                value.append((char) (int) data.get(pos++));
            }
            environment.put(name.toString(), value.toString());
        }
    }

    private int calculateSize() {
        int size = 0;
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            size += entry.getKey().length() + 1 + entry.getValue().length();
        }
        return size;
    }

    /**
     * Get an environment variable.
     *
     * @param name the variable name
     * @return the value, or null if not set
     */
    public String get(String name) {
        return environment.get(name);
    }

    /**
     * Set an environment variable.
     *
     * @param name  the variable name
     * @param value the value
     */
    public void set(String name, String value) {
        environment.put(name, value);
    }
}
