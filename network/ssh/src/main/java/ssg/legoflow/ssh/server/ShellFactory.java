package ssg.legoflow.ssh.server;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Factory for creating shell processes for SSH sessions.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface ShellFactory {

    /**
     * Creates a new shell process.
     *
     * @param in  the input stream from the client
     * @param out the output stream to the client
     * @param err the error stream to the client
     */
    void createShell(InputStream in, OutputStream out, OutputStream err);
}
