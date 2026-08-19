package ssg.legoflow.network.terminals.base.io;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;

/**
 * Registry for terminal type implementations.
 *
 * <p>Terminal implementations register themselves at class load time.
 * The factory then creates instances by type name.
 *
 * @since 0.2.0
 */
public final class TerminalFactory {

    private TerminalFactory() {}

    /**
     * Lookup a terminal implementation by type name.
     *
     * <p>Terminal modules register their implementations via {@link #register(String, Creator)}.
     *
     * @param type the terminal type name
     * @return the creator function, or null if not registered
     */
    public static Creator lookup(String type) {
        if (type == null) return null;
        return registry.get(type.toLowerCase());
    }

    /**
     * Register a terminal type implementation.
     *
     * @param type    the type name (e.g., "vt100", "xterm")
     * @param creator function that creates instances
     */
    public static void register(String type, Creator creator) {
        registry.put(type.toLowerCase(), creator);
    }

    /**
     * Create a terminal of the specified type with default configuration.
     *
     * @param type the terminal type
     * @return a terminal instance
     * @throws IllegalArgumentException if the type is not registered
     */
    public static Terminal create(String type) {
        Creator c = lookup(type);
        if (c == null) throw new IllegalArgumentException("Unknown terminal type: " + type);
        return c.create(TerminalConfig.builder().build());
    }

    /**
     * Create a terminal of the specified type with the given configuration.
     *
     * @param type   the terminal type
     * @param config the configuration
     * @return a terminal instance
     * @throws IllegalArgumentException if the type is not registered
     */
    public static Terminal create(String type, TerminalConfig config) {
        Creator c = lookup(type);
        if (c == null) throw new IllegalArgumentException("Unknown terminal type: " + type);
        return c.create(config);
    }

    /**
     * List all registered terminal types.
     */
    public static String[] registeredTypes() {
        return registry.keySet().toArray(String[]::new);
    }

    /**
     * Factory function for terminal instances.
     */
    @FunctionalInterface
    public interface Creator {
        Terminal create(TerminalConfig config);
    }

    private static final java.util.Map<String, Creator> registry = new java.util.HashMap<>();
}
