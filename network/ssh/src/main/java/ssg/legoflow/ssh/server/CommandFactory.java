package ssg.legoflow.ssh.server;

import java.io.OutputStream;

/**
 * Factory for executing commands on behalf of SSH clients.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface CommandFactory {

    /**
     * Executes a command.
     *
     * @param command the command string
     * @param out     the output stream
     * @param err     the error stream
     * @return the exit status code
     */
    int executeCommand(String command, OutputStream out, OutputStream err);
}
