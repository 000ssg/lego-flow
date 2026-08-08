package ssg.legoflow.ssh.demo;

import ssg.legoflow.ssh.auth.PasswordAuth;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.connection.SessionChannel;
import ssg.legoflow.ssh.sftp.SftpClient;
import ssg.legoflow.ssh.sftp.SftpPacket;

import java.util.List;

/**
 * Demo: SFTP directory listing and file operations.
 *
 * @since 0.1.0
 */
public final class SftpBrowseDemo {

    /**
     * Lists a remote directory via SFTP.
     *
     * @param host     the SSH server host
     * @param port     the SSH server port
     * @param username the username
     * @param password the password
     * @param path     the remote directory path
     * @return list of file names
     * @throws Exception if an error occurs
     */
    public static List<String> listDirectory(String host, int port, String username,
                                              String password, String path) throws Exception {
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(username, new PasswordAuth(password));

            SessionChannel sftpChannel = client.openSftpChannel();
            SftpClient sftp = new SftpClient(sftpChannel);
            sftp.init();

            byte[] dirHandle = sftp.opendir(path);
            List<SftpPacket.NameEntry> entries = sftp.readdir(dirHandle);
            sftp.close(dirHandle);
            sftp.close();

            return entries.stream()
                    .map(SftpPacket.NameEntry::filename)
                    .toList();
        }
    }
}
