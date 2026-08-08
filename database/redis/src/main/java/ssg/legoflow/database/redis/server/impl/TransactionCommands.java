package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.TransactionExecutor;

/**
 * Implements Redis transaction commands: MULTI, EXEC, DISCARD, WATCH, UNWATCH.
 *
 * @since 0.1.0
 */
public final class TransactionCommands {

    private static final RespType OK = new RespType.SimpleString("OK");
    private static final RespType QUEUED = new RespType.SimpleString("QUEUED");

    private TransactionCommands() {}

    /**
     * Registers all transaction commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("MULTI", TransactionCommands::multi);
        registry.register("EXEC", TransactionCommands::exec);
        registry.register("DISCARD", TransactionCommands::discard);
        registry.register("WATCH", TransactionCommands::watch);
        registry.register("UNWATCH", TransactionCommands::unwatch);
    }

    private static RespType multi(CommandArgs args, ClientConnection client) {
        TransactionExecutor.TransactionState state = client.transactionState();
        if (state.isInTransaction()) {
            return new RespType.Error("ERR", "MULTI calls can not be nested");
        }
        state.begin();
        return OK;
    }

    static RespType exec(CommandArgs args, ClientConnection client) {
        TransactionExecutor.TransactionState state = client.transactionState();
        if (!state.isInTransaction()) {
            return new RespType.Error("ERR", "EXEC without MULTI");
        }
        return client.transactionExecutor().exec(state,
                client.server().commandRegistry(), client);
    }

    private static RespType discard(CommandArgs args, ClientConnection client) {
        TransactionExecutor.TransactionState state = client.transactionState();
        if (!state.isInTransaction()) {
            return new RespType.Error("ERR", "DISCARD without MULTI");
        }
        state.discard();
        return OK;
    }

    private static RespType watch(CommandArgs args, ClientConnection client) {
        if (client.transactionState().isInTransaction()) {
            return new RespType.Error("ERR", "WATCH inside MULTI is not allowed");
        }
        String[] keys = new String[args.size() - 1];
        for (int i = 1; i < args.size(); i++) {
            keys[i - 1] = args.getString(i);
        }
        client.transactionExecutor().watch(client.transactionState(), keys);
        return OK;
    }

    private static RespType unwatch(CommandArgs args, ClientConnection client) {
        client.transactionState().reset();
        return OK;
    }

    /**
     * Returns the QUEUED response for commands queued during a transaction.
     *
     * @return the QUEUED simple string
     */
    public static RespType queued() {
        return QUEUED;
    }
}
