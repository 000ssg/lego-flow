package ssg.legoflow.network.telnet.negotiation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Handler for NEW_ENVY (New Environment) option subnegotiation (RFC 1408).
 *
 * <p>This handler supports:
 * <ul>
 *   <li>INFO suboption — respond with environment variable info</li>
 *   <li>IS suboption — send or receive environment variables</li>
 *   <li>NO-PRODUCTS suboption — indicate environment not available</li>
 *   <li>INFOMASK filtering — filter response based on peer's request (INFO_TYPE, INFO_LENGTH)</li>
 *   <li>BOOL info type — boolean variables (0/1 values)</li>
 *   <li>Remote environment reading — parse variables sent by peer</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>ESCAPES mask — not needed; no Telnet escape character translation is part of NEW_ENV</li>
 *   <li>SCOPE mask — not supported; environment variables are local, no cross-host scope needed</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class NewEnvHandler {

    /** NEW_ENV INFO — request or provide environment info. */
    public static final int NEW_ENV_INFO = 0;
    /** NEW_ENV IS — send environment variables. */
    public static final int NEW_ENV_IS = 1;
    /** NEW_ENV NO-PRODUCTS — not available. */
    public static final int NEW_ENV_NO_PRODUCTS = 2;

    /** Info mask: include variable type. */
    public static final int INFO_TYPE = 0x01;
    /** Info mask: apply escape processing. */
    public static final int INFO_ESCAPES = 0x02;
    /** Info mask: include scope information. */
    public static final int INFO_SCOPE = 0x04;
    /** Info mask: include length prefix. */
    public static final int INFO_LENGTH = 0x08;

    /** All info types. */
    public static final int INFO_ALL = 0xFF;

    /** Variable type: STRING. */
    public static final int TYPE_STRING = 0;
    /** Variable type: BOOL. */
    public static final int TYPE_BOOL = 1;
    /** Variable type: BYTE. */
    public static final int TYPE_BYTE = 2;

    /**
     * Environment variable with type information.
     */
    public static class EnvVar {
        private final String name;
        private final String value;
        private final int type;

        public EnvVar(String name, String value) {
            this(name, value, TYPE_STRING);
        }

        public EnvVar(String name, String value, int type) {
            this.name = Objects.requireNonNull(name);
            this.value = value;
            this.type = type;
        }

        public String name() { return name; }
        public String value() { return value; }
        public int type() { return type; }
    }

    private final Map<String, EnvVar> environment;
    private final Map<String, EnvVar> remoteEnvironment;
    private BiConsumer<String, EnvVar> remoteVarCallback;

    private NewEnvHandler() {
        this.environment = new HashMap<>();
        this.remoteEnvironment = new HashMap<>();
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
        // Add standard STRING variables
        handler.environment.put("TERM", new EnvVar("TERM", termType));
        handler.environment.put("COLS", new EnvVar("COLS", String.valueOf(cols)));
        handler.environment.put("LINES", new EnvVar("LINES", String.valueOf(rows)));
        handler.environment.put("SHELL", new EnvVar("SHELL", "/bin/sh"));
        handler.environment.put("LANG", new EnvVar("LANG", "C"));

        // Add BOOL variables
        handler.environment.put("COLOR", new EnvVar("COLOR", "true", TYPE_BOOL));
        handler.environment.put("LOGIN", new EnvVar("LOGIN", "true", TYPE_BOOL));

        // Add BYTE variables
        handler.environment.put("TMOUT", new EnvVar("TMOUT", "0", TYPE_BYTE));

        return handler;
    }

    /**
     * Create a new NewEnvHandler with custom environment variables.
     *
     * @param vars the environment variables
     */
    public static NewEnvHandler create(Map<String, EnvVar> vars) {
        NewEnvHandler handler = new NewEnvHandler();
        handler.environment.putAll(vars);
        return handler;
    }

    /**
     * Set the callback for receiving remote environment variables.
     */
    public NewEnvHandler onRemoteVar(BiConsumer<String, EnvVar> callback) {
        this.remoteVarCallback = callback;
        return this;
    }

    /**
     * Get all local environment variables.
     */
    public Map<String, EnvVar> getEnvironment() {
        return Map.copyOf(environment);
    }

    /**
     * Get a local environment variable value by name.
     *
     * @param name the variable name
     * @return the String value, or null if not found
     */
    public String get(String name) {
        EnvVar var = environment.get(name);
        return var != null ? var.value() : null;
    }

    /**
     * Set a local environment variable (STRING type).
     */
    public void set(String name, String value) {
        environment.put(name, new EnvVar(name, value));
    }

    /**
     * Get the remote environment variables received from the peer.
     */
    public Map<String, EnvVar> getRemoteEnvironment() {
        return Map.copyOf(remoteEnvironment);
    }

    /**
     * Add an environment variable.
     */
    public void put(String name, String value) {
        environment.put(name, new EnvVar(name, value));
    }

    /**
     * Add a typed environment variable.
     */
    public void put(String name, String value, int type) {
        environment.put(name, new EnvVar(name, value, type));
    }

    /**
     * Add a boolean environment variable.
     */
    public void putBool(String name, boolean value) {
        environment.put(name, new EnvVar(name, value ? "true" : "false", TYPE_BOOL));
    }

    /**
     * Add a byte environment variable.
     */
    public void putByte(String name, byte value) {
        environment.put(name, new EnvVar(name, String.valueOf(value & 0xFF), TYPE_BYTE));
    }

    /**
     * Handle received subnegotiation data.
     *
     * @param data the subnegotiation payload
     * @return bytes to send back, or null if no response needed
     */
    public byte[] handle(List<Integer> data) {
        if (data.isEmpty()) return null;

        int command = data.get(0) & 0xFF;

        return switch (command) {
            case NEW_ENV_INFO -> handleInfo(data);
            case NEW_ENV_IS -> handleIs(data);
            case NEW_ENV_NO_PRODUCTS -> handleNoProducts(data);
            default -> null;
        };
    }

    /**
     * Handle INFO — peer requests environment info.
     * Format: INFO [<variable-name>] [<infomask>]
     *
     * <p>Supports INFOMASK filtering:
     * <ul>
     *   <li>INFO_TYPE (0x01) — include variable type byte in response</li>
     *   <li>INFO_LENGTH (0x08) — include explicit length prefix (always included by RFC 1408)</li>
     *   <li>INFO_ESCAPES (0x02) — not used; no escape processing needed</li>
     *   <li>INFO_SCOPE (0x04) — not used; environment is local-only</li>
     * </ul>
     *
     * @return IS response with variables, or null
     */
    private byte[] handleInfo(List<Integer> data) {
        // Default: return all variables
        List<EnvVar> vars = new ArrayList<>(environment.values());
        int infomask = INFO_ALL;

        if (data.size() >= 2) {
            // Parse variable name (length-prefixed)
            int varLen = data.get(1) & 0xFF;
            if (varLen > 0 && data.size() >= 2 + varLen) {
                StringBuilder name = new StringBuilder();
                for (int i = 2; i < 2 + varLen; i++) {
                    name.append((char) (data.get(i) & 0xFF));
                }
                String requestedName = name.toString();

                // Filter to requested variable
                EnvVar target = environment.get(requestedName);
                if (target != null) {
                    vars = List.of(target);
                } else {
                    vars = new ArrayList<>();
                }

                // Parse infomask if present
                if (data.size() >= 3 + varLen) {
                    infomask = data.get(2 + varLen) & 0xFF;
                }
            }
        }

        return buildIsResponse(vars, infomask);
    }

    /**
     * Handle IS — peer sends environment variables.
     * Format: IS <var-name> <var-len> <var-value> [<type>]...
     */
    private byte[] handleIs(List<Integer> data) {
        if (data.size() < 2) return null;

        int pos = 1;
        while (pos < data.size()) {
            // Parse variable name (length-prefixed)
            int nameLen = data.get(pos) & 0xFF;
            if (nameLen == 0 || pos + nameLen >= data.size()) break;

            StringBuilder name = new StringBuilder();
            for (int i = 1; i <= nameLen; i++) {
                name.append((char) (data.get(pos + i) & 0xFF));
            }
            pos += nameLen + 1;

            // Parse value length
            if (pos >= data.size()) break;
            int valLen = data.get(pos) & 0xFF;
            pos++;

            if (valLen == 0 || pos + valLen > data.size()) break;

            // Parse value
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < valLen; i++) {
                value.append((char) (data.get(pos + i) & 0xFF));
            }
            pos += valLen;

            // Parse optional type byte
            int type = TYPE_STRING;
            if (pos < data.size()) {
                type = data.get(pos) & 0xFF;
                pos++;
            }

            EnvVar var = new EnvVar(name.toString(), value.toString(), type);
            remoteEnvironment.put(var.name(), var);

            if (remoteVarCallback != null) {
                remoteVarCallback.accept(var.name(), var);
            }
        }

        return null; // No response needed for IS
    }

    /**
     * Handle NO-PRODUCTS — peer indicates environment not available.
     */
    private byte[] handleNoProducts(List<Integer> data) {
        // No response needed; peer just informing us
        return null;
    }

    /**
     * Build an IS response with the given variables and infomask.
     *
     * <p>Respects the following INFOMASK bits:
     * <ul>
     *   <li>INFO_TYPE — includes type byte after value</li>
     *   <li>INFO_LENGTH — length prefix always included per RFC 1408</li>
     * </ul>
     */
    private byte[] buildIsResponse(List<EnvVar> vars, int infomask) {
        if (vars.isEmpty()) return null;

        // Calculate total size
        int totalSize = 1; // IS command byte
        for (EnvVar var : vars) {
            totalSize += var.name().length() + 1; // name + length byte
            totalSize += var.value().length() + 1; // value + length byte
            if ((infomask & INFO_TYPE) != 0) {
                totalSize++; // type byte
            }
        }

        byte[] result = new byte[totalSize];
        int pos = 0;
        result[pos++] = (byte) NEW_ENV_IS;

        for (EnvVar var : vars) {
            String name = var.name();
            byte[] nameBytes = name.getBytes();
            result[pos++] = (byte) nameBytes.length;
            System.arraycopy(nameBytes, 0, result, pos, nameBytes.length);
            pos += nameBytes.length;

            String value = var.value();
            byte[] valueBytes = value.getBytes();
            result[pos++] = (byte) valueBytes.length;
            System.arraycopy(valueBytes, 0, result, pos, valueBytes.length);
            pos += valueBytes.length;

            if ((infomask & INFO_TYPE) != 0) {
                result[pos++] = (byte) var.type();
            }
        }

        return result;
    }
}
