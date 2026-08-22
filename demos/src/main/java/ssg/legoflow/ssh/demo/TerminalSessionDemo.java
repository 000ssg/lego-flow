package ssg.legoflow.ssh.demo;

import ssg.legoflow.ssh.auth.PasswordAuth;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.connection.SessionChannel;
import java.nio.charset.StandardCharsets;
/**
 * Demo: Interactive terminal session with PTY allocation.
 *
 * @since 0.1.0
 */
public final class TerminalSessionDemo {

    /**
     * Opens an interactive terminal session.
     *
     * @param host     the server host
     * @param port     the server port
     * @param username the username
     * @param password the password
     * @return the initial terminal output
     * @throws Exception if an error occurs
     */
    public static String run(String host, int port, String username, String password)
            throws Exception {
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(username, new PasswordAuth(password));

            SessionChannel session = client.openSession();
            session.requestPty("xterm-256color", 80, 24, 640, 480);
            session.requestShell();

            // Send a command
            session.sendData("echo Hello from LegoFlow SSH\n".getBytes(StandardCharsets.UTF_8));

            // Read response
            byte[] output = session.receiveData(3000);
            session.close();

            return output != null ? new String(output, StandardCharsets.UTF_8) : "";
        }
    }
}
