package ssg.legoflow.ssh.demo;

import ssg.legoflow.ssh.auth.AuthContext;
import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import ssg.legoflow.ssh.server.*;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Demo: Embedded SSH server with shell and SFTP.
 *
 * @since 0.1.0
 */
public final class SshServerDemo {

    /**
     * Starts an embedded SSH server on the given port.
     *
     * @param port the port to listen on
     * @return the running server
     * @throws Exception if an error occurs
     */
    public static SshServer startServer(int port) throws Exception {
        SshKeyPair hostKey = SshKeyPair.generate(new Ed25519());

        AuthContext auth = new AuthContext()
                .setPasswordValidator((user, pass) -> "admin".equals(user) && "secret".equals(pass))
                .setBanner("Welcome to LegoFlow SSH Server\n")
                .setMaxFailures(3);

        SshServer server = new SshServer(SshServerConfig.builder()
                .port(port)
                .bindAddress("127.0.0.1")
                .build());

        server.setHostKey(hostKey)
              .setAuthenticator(auth)
              .setCommandFactory((command, out, err) -> {
                  try {
                      String result = "Executed: " + command + "\n";
                      out.write(result.getBytes(StandardCharsets.UTF_8));
                      out.flush();
                      return 0;
                  } catch (Exception e) {
                      return 1;
                  }
              })
              .setForwardingFilter(ForwardingFilter.denyAll());

        server.bind(port);
        return server;
    }
}
