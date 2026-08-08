package ssg.legoflow.database.redis.command;

import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps command names to their handlers.
 *
 * <p>Command names are stored in uppercase. When a command is not found,
 * an error response is returned automatically.
 *
 * @since 0.1.0
 */
public final class CommandRegistry {

    private final Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a handler for the given command name.
     *
     * @param name    the command name (case-insensitive)
     * @param handler the handler implementation
     */
    public void register(String name, CommandHandler handler) {
        handlers.put(name.toUpperCase(), handler);
    }

    /**
     * Dispatches a command to its registered handler.
     *
     * @param args   the parsed command arguments
     * @param client the client connection
     * @return the RESP response
     */
    public RespType dispatch(CommandArgs args, ClientConnection client) {
        String name = args.commandName();
        CommandHandler handler = handlers.get(name);
        if (handler == null) {
            return new RespType.Error("ERR",
                    "unknown command '" + name.toLowerCase() + "', with args beginning with: ");
        }
        return handler.handle(args, client);
    }

    /**
     * Returns whether a handler is registered for the given command name.
     *
     * @param name the command name (case-insensitive)
     * @return true if registered
     */
    public boolean hasCommand(String name) {
        return handlers.containsKey(name.toUpperCase());
    }

    /**
     * Returns the number of registered commands.
     *
     * @return command count
     */
    public int size() {
        return handlers.size();
    }

    /**
     * Returns all registered command names.
     *
     * @return set of uppercase command names
     */
    public java.util.Set<String> commandNames() {
        return java.util.Collections.unmodifiableSet(handlers.keySet());
    }
}
