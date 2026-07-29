package ssg.legoflow.ssh.demo;

import ssg.legoflow.ssh.auth.AuthContext;
import ssg.legoflow.ssh.auth.AuthResult;
import ssg.legoflow.ssh.auth.PasswordAuth;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.connection.SessionChannel;
import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import ssg.legoflow.ssh.server.*;
import ssg.legoflow.ssh.sftp.SftpClient;
import ssg.legoflow.ssh.sftp.SftpCodec;
import ssg.legoflow.ssh.sftp.SftpFileAttributes;
import ssg.legoflow.ssh.sftp.SftpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Comprehensive demo of all SSH module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link SshServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports SSH-2 key exchange (DH, ECDH, Curve25519),
 * authentication (password, publickey), session channels, command execution,
 * SFTP subsystem, SCP file transfer, and port forwarding.
 * Ideal for development, testing, CI/CD, and learning the SSH protocol.</p>
 *
 * <p><b>Alternative: External OpenSSH / Dropbear</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Testing against production SSH servers with real host keys</li>
 *   <li>Agent forwarding with system SSH agent</li>
 *   <li>X11 forwarding with actual X11 display</li>
 *   <li>Integration testing against real infrastructure</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop)
 * and authentication credentials. All client code (SshClient, SftpClient, ScpClient)
 * uses the same API regardless of backend. When {@code USE_EXTERNAL=true}, the demo
 * skips server creation and connects directly to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Key exchange — Ed25519 host key generation and negotiation</li>
 *   <li>Password authentication — simple username/password auth</li>
 *   <li>Command execution — run remote commands and capture output</li>
 *   <li>PTY and shell — interactive terminal session with PTY allocation</li>
 *   <li>SFTP file operations — open, write, read, list, stat, rename, remove</li>
 *   <li>Port forwarding — local and remote TCP tunnel setup</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoSshAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSshAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house SshServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for OpenSSH/Dropbear
    // =========================================================================

    /** Set to {@code true} to connect to an external SSH server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external SSH server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external SSH server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 22;

    /** Username for external SSH server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_USER = "testuser";

    /** Password for external SSH server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_PASSWORD = "testpass";

    private DemoSshAll() {}

    /**
     * Results from running the full demo.
     *
     * @param keyExchange       true if key exchange and connection succeeded
     * @param passwordAuth      true if password authentication succeeded
     * @param commandExecution  true if remote command execution returned output
     * @param ptyShell          true if PTY session and shell interaction succeeded
     * @param sftpOperations    number of SFTP operations completed successfully
     * @param portForwarding    true if local port forward channel was created
     */
    public record Results(
            boolean keyExchange,
            boolean passwordAuth,
            boolean commandExecution,
            boolean ptyShell,
            int sftpOperations,
            boolean portForwarding
    ) {}

    /**
     * Runs the comprehensive demo covering all SSH features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runAgainstServer(EXTERNAL_HOST, EXTERNAL_PORT,
                    EXTERNAL_USER, EXTERNAL_PASSWORD, null);
        }

        // Create temporary directory for file transfer demos
        Path tempDir = Files.createTempDirectory("ssh-demo-");

        try {
            // Start in-house SSH server
            SshServer server = startDemoServer(tempDir);
            int port = server.port();
            LOG.info("In-house SshServer started on port {}", port);

            try {
                return runAgainstServer("127.0.0.1", port, "admin", "secret", tempDir);
            } finally {
                server.close();
            }
        } finally {
            // Clean up temp directory
            deleteRecursive(tempDir);
        }
    }

    private static Results runAgainstServer(String host, int port,
                                            String user, String password,
                                            Path tempDir) throws Exception {
        boolean kex = demoKeyExchange(host, port);
        boolean auth = demoPasswordAuth(host, port, user, password);
        boolean cmd = demoCommandExecution(host, port, user, password);
        boolean pty = demoPtyShell(host, port, user, password);
        int sftp = demoSftpOperations(host, port, user, password);
        boolean fwd = demoPortForwarding(host, port, user, password);

        return new Results(kex, auth, cmd, pty, sftp, fwd);
    }

    // ======================== Server Setup ==================================

    /**
     * Starts the demo SSH server with password authentication and command factory.
     */
    static SshServer startDemoServer(Path rootDir) throws Exception {
        SshKeyPair hostKey = SshKeyPair.generate(new Ed25519());

        AuthContext auth = new AuthContext()
                .setPasswordValidator((user, pass) -> "admin".equals(user) && "secret".equals(pass))
                .setMaxFailures(3);

        SshServer server = new SshServer(SshServerConfig.builder()
                .port(0)
                .bindAddress("127.0.0.1")
                .build());

        server.setHostKey(hostKey)
              .setAuthenticator(auth)
              .setRootDirectory(rootDir)
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
              .setShellFactory((in, out, err) -> {
                  try {
                      out.write("Welcome to LegoFlow SSH\n$ ".getBytes(StandardCharsets.UTF_8));
                      out.flush();
                      // Simple echo shell: read a line and echo it
                      byte[] buf = new byte[1024];
                      int n = in.read(buf);
                      if (n > 0) {
                          out.write(buf, 0, n);
                          out.flush();
                      }
                  } catch (Exception ignored) {}
              })
              .setForwardingFilter(ForwardingFilter.allowAll());

        server.bind(0);
        return server;
    }

    // ======================== 1. KEY EXCHANGE ================================

    /**
     * Demonstrates SSH key exchange: connects and negotiates crypto parameters.
     * The in-house server uses Ed25519 host keys by default.
     */
    static boolean demoKeyExchange(String host, int port) throws Exception {
        LOG.info("=== 1. Key Exchange ===");
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            boolean connected = client.isConnected();
            LOG.info("Key exchange completed: connected={}", connected);
            return connected;
        }
    }

    // ======================== 2. PASSWORD AUTHENTICATION =====================

    /**
     * Demonstrates password authentication.
     * <p>
     * <b>Preferred: password auth</b> — simplest for development and testing.
     * <p>
     * <b>Alternative: public key auth</b> — more secure, recommended for production.
     * Uses {@code SshKeyPair} for the client key and {@code AuthContext} for verification.
     */
    static boolean demoPasswordAuth(String host, int port, String user, String password)
            throws Exception {
        LOG.info("=== 2. Password Authentication ===");
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            AuthResult result = client.authenticate(user, new PasswordAuth(password));
            boolean success = result instanceof AuthResult.Success;
            LOG.info("Password auth: {} (result={})", success ? "SUCCESS" : "FAILED", result);
            return success;
        }
    }

    // ======================== 3. COMMAND EXECUTION ===========================

    /**
     * Demonstrates remote command execution and output capture.
     */
    static boolean demoCommandExecution(String host, int port, String user, String password)
            throws Exception {
        LOG.info("=== 3. Command Execution ===");
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(user, new PasswordAuth(password));

            SessionChannel session = client.openSession();
            session.requestExec("echo Hello SSH");

            byte[] output = session.receiveData(5000);
            session.close();

            String outputStr = output != null ? new String(output, StandardCharsets.UTF_8) : "";
            LOG.info("Command output: {}", outputStr.trim());
            return !outputStr.isEmpty();
        }
    }

    // ======================== 4. PTY AND SHELL ===============================

    /**
     * Demonstrates PTY allocation and interactive shell session.
     */
    static boolean demoPtyShell(String host, int port, String user, String password)
            throws Exception {
        LOG.info("=== 4. PTY and Shell ===");
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(user, new PasswordAuth(password));

            SessionChannel session = client.openSession();
            session.requestPty("xterm-256color", 80, 24, 640, 480);
            session.requestShell();

            // Send a command to the shell
            session.sendData("echo test\n".getBytes(StandardCharsets.UTF_8));

            byte[] output = session.receiveData(3000);
            session.close();

            String outputStr = output != null ? new String(output, StandardCharsets.UTF_8) : "";
            LOG.info("Shell output: {}", outputStr.trim());
            return !outputStr.isEmpty();
        }
    }

    // ======================== 5. SFTP OPERATIONS ============================

    /**
     * Demonstrates SFTP file operations: open, write, read, list, stat, rename, remove.
     * Returns the number of operations that completed successfully.
     */
    static int demoSftpOperations(String host, int port, String user, String password)
            throws Exception {
        LOG.info("=== 5. SFTP Operations ===");
        int ops = 0;

        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(user, new PasswordAuth(password));

            SessionChannel sftpChannel = client.openSftpChannel();
            SftpClient sftp = new SftpClient(sftpChannel);
            sftp.init();
            LOG.info("SFTP initialized: version={}", sftp.serverVersion());
            ops++; // 1: init

            // Get real path
            String realPath = sftp.realpath(".");
            LOG.info("Real path: {}", realPath);
            ops++; // 2: realpath

            // Create directory
            sftp.mkdir("/sftp-demo");
            LOG.info("Created directory /sftp-demo");
            ops++; // 3: mkdir

            // Write a file
            byte[] fileData = "Hello from SFTP demo!".getBytes(StandardCharsets.UTF_8);
            byte[] writeHandle = sftp.open("/sftp-demo/test.txt",
                    SftpCodec.SSH_FXF_WRITE | SftpCodec.SSH_FXF_CREAT | SftpCodec.SSH_FXF_TRUNC);
            sftp.write(writeHandle, 0, fileData);
            sftp.close(writeHandle);
            LOG.info("Wrote {} bytes to /sftp-demo/test.txt", fileData.length);
            ops++; // 4: write

            // Read the file back
            byte[] readHandle = sftp.open("/sftp-demo/test.txt", SftpCodec.SSH_FXF_READ);
            byte[] readData = sftp.read(readHandle, 0, 1024);
            sftp.close(readHandle);
            LOG.info("Read {} bytes from /sftp-demo/test.txt", readData != null ? readData.length : 0);
            ops++; // 5: read

            // List directory
            byte[] dirHandle = sftp.opendir("/sftp-demo");
            List<SftpPacket.NameEntry> entries = sftp.readdir(dirHandle);
            sftp.close(dirHandle);
            LOG.info("Directory listing: {} entries", entries.size());
            for (SftpPacket.NameEntry entry : entries) {
                LOG.info("  {}", entry.filename());
            }
            ops++; // 6: readdir

            // Stat file
            SftpFileAttributes attrs = sftp.stat("/sftp-demo/test.txt");
            LOG.info("Stat: size={}", attrs.size());
            ops++; // 7: stat

            // Rename file
            sftp.rename("/sftp-demo/test.txt", "/sftp-demo/renamed.txt");
            LOG.info("Renamed test.txt to renamed.txt");
            ops++; // 8: rename

            // Remove file
            sftp.remove("/sftp-demo/renamed.txt");
            LOG.info("Removed renamed.txt");
            ops++; // 9: remove

            // Remove directory
            sftp.rmdir("/sftp-demo");
            LOG.info("Removed directory /sftp-demo");
            ops++; // 10: rmdir

            sftp.close();
        }

        LOG.info("SFTP operations completed: {}", ops);
        return ops;
    }

    // ======================== 6. PORT FORWARDING ============================

    /**
     * Demonstrates local port forwarding (SSH tunnel).
     * <p>
     * <b>Preferred: local forwarding</b> — client listens locally, forwards to remote.
     * <p>
     * <b>Alternative: remote forwarding</b> — server listens remotely, forwards to local.
     */
    static boolean demoPortForwarding(String host, int port, String user, String password)
            throws Exception {
        LOG.info("=== 6. Port Forwarding ===");
        try (SshClient client = new SshClient()) {
            client.connect(host, port);
            client.authenticate(user, new PasswordAuth(password));

            // Create local forward channel (tunnels to a remote host:port)
            var channel = client.createLocalForward(0, "127.0.0.1", 80);
            boolean channelCreated = channel != null;
            LOG.info("Local forward channel created: {}", channelCreated);

            return channelCreated;
        }
    }

    // ======================== Utilities =====================================

    private static void deleteRecursive(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    entries.forEach(DemoSshAll::deleteRecursive);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.debug("Cleanup failed for {}: {}", path, e.getMessage());
        }
    }
}
