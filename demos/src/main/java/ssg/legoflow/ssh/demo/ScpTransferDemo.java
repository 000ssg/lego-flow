package ssg.legoflow.ssh.demo;

import ssg.legoflow.ssh.auth.PasswordAuth;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.connection.SessionChannel;
import ssg.legoflow.ssh.scp.ScpClient;

import java.nio.file.Path;

/**
 * Demo: File upload and download via SCP.
 *
 * @since 0.1.0
 */
public final class ScpTransferDemo {

    /**
     * Uploads a file via SCP.
     *
     * @param host       the SSH server host
     * @param port       the SSH server port
     * @param username   the username
     * @param password   the password
     * @param localPath  the local file path
     * @param remotePath the remote destination path
     * @throws Exception if an error occurs
     */
    public static void upload(String host, int port, String username, String password,
                              Path localPath, String remotePath) throws Exception {
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(username, new PasswordAuth(password));

            SessionChannel session = client.openSession();
            ScpClient scp = new ScpClient(session);
            scp.upload(localPath, remotePath);
            session.close();
        }
    }

    /**
     * Downloads a file via SCP.
     *
     * @param host       the SSH server host
     * @param port       the SSH server port
     * @param username   the username
     * @param password   the password
     * @param remotePath the remote file path
     * @param localPath  the local destination path
     * @throws Exception if an error occurs
     */
    public static void download(String host, int port, String username, String password,
                                String remotePath, Path localPath) throws Exception {
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(username, new PasswordAuth(password));

            SessionChannel session = client.openSession();
            ScpClient scp = new ScpClient(session);
            scp.download(remotePath, localPath);
            session.close();
        }
    }
}
