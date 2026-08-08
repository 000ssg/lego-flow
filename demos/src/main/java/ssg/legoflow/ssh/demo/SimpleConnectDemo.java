package ssg.legoflow.ssh.demo;

import ssg.legoflow.ssh.auth.AuthResult;
import ssg.legoflow.ssh.auth.PasswordAuth;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.connection.SessionChannel;

import java.nio.charset.StandardCharsets;

/**
 * Demo: Connect to SSH server, authenticate, and execute a command.
 *
 * @since 0.1.0
 */
public final class SimpleConnectDemo {

    /**
     * Connects to a server and executes "uname -a".
     *
     * @param host     the server host
     * @param port     the server port
     * @param username the username
     * @param password the password
     * @return the command output
     * @throws Exception if an error occurs
     */
    public static String run(String host, int port, String username, String password)
            throws Exception {
        try (SshClient client = new SshClient()) {
            client.connect(host, port);

            AuthResult result = client.authenticate(username, new PasswordAuth(password));
            if (!(result instanceof AuthResult.Success)) {
                throw new RuntimeException("Authentication failed: " + result);
            }

            SessionChannel session = client.openSession();
            session.requestExec("uname -a");

            byte[] output = session.receiveData(5000);
            session.close();

            return output != null ? new String(output, StandardCharsets.UTF_8) : "";
        }
    }
}
