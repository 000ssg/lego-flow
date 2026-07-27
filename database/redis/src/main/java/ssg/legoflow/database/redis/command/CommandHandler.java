package ssg.legoflow.database.redis.command;

import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;

/**
 * Functional interface for handling a Redis command.
 *
 * <p>Implementations receive parsed command arguments and the client
 * connection context, and return a RESP response.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Executes a Redis command and returns the response.
     *
     * @param args   the parsed command arguments
     * @param client the client connection context
     * @return the RESP response to send back
     */
    RespType handle(CommandArgs args, ClientConnection client);
}
