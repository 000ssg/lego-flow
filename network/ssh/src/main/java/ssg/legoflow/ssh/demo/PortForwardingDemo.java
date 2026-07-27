package ssg.legoflow.ssh.demo;

import ssg.legoflow.ssh.auth.PasswordAuth;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.connection.DirectTcpIpChannel;

/**
 * Demo: Local and remote port forwarding through SSH tunnel.
 *
 * @since 1.0.0
 */
public final class PortForwardingDemo {

    /**
     * Creates a local port forward.
     *
     * @param sshHost    the SSH server host
     * @param sshPort    the SSH server port
     * @param username   the username
     * @param password   the password
     * @param localPort  the local port
     * @param remoteHost the remote target host
     * @param remotePort the remote target port
     * @return the forwarding channel
     * @throws Exception if an error occurs
     */
    public static DirectTcpIpChannel localForward(
            String sshHost, int sshPort, String username, String password,
            int localPort, String remoteHost, int remotePort) throws Exception {
        SshClient client = new SshClient();
        client.connect(sshHost, sshPort);
        client.authenticate(username, new PasswordAuth(password));
        return client.createLocalForward(localPort, remoteHost, remotePort);
    }

    /**
     * Creates a remote port forward.
     *
     * @param sshHost    the SSH server host
     * @param sshPort    the SSH server port
     * @param username   the username
     * @param password   the password
     * @param remotePort the remote port to bind
     * @param localHost  the local target host
     * @param localPort  the local target port
     * @throws Exception if an error occurs
     */
    public static void remoteForward(
            String sshHost, int sshPort, String username, String password,
            int remotePort, String localHost, int localPort) throws Exception {
        try (SshClient client = new SshClient()) {
            client.connect(sshHost, sshPort);
            client.authenticate(username, new PasswordAuth(password));
            client.createRemoteForward(remotePort, localHost, localPort);
        }
    }
}
