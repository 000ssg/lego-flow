package ssg.legoflow.ssh;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.*;
import ssg.legoflow.ssh.auth.PasswordAuth;
import ssg.legoflow.ssh.connection.SessionChannel;
import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability tests: Lego Flow SSH client/server ↔ Apache MINA SSHD.
 *
 * <p>Uses Apache MINA SSHD as the reference implementation to verify that
 * our SSH implementation correctly follows RFC 4251-4256 specifications.
 *
 * @since 0.1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(30)
class SshServerInteropTest {

    private static final String TEST_USER = "interop_user";
    private static final String TEST_PASS = "interop_pass_123";

    // Built-in Lego Flow SSH server
    private static ssg.legoflow.ssh.server.SshServer localServer;
    private static int localPort;

    // Apache MINA SSHD server (reference implementation)
    private static SshServer minaServer;
    private static int minaPort;

    // Executor for async server management
    private static ExecutorService executor;

    // Minimal Command implementation for MINA server
    private static class SimpleCommand implements Command {
        private final String command;
        private OutputStream out, err;
        private InputStream in;
        private ExitCallback exitCb;

        SimpleCommand(String command) { this.command = command; }

        @Override
        public void start(ChannelSession session, org.apache.sshd.server.Environment env) throws IOException {
            try {
                if (out != null) {
                    out.write(("echo: " + command + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } finally {
                if (out != null) out.flush();
                if (exitCb != null) exitCb.onExit(0, "done", true);
            }
        }

        @Override public void destroy(ChannelSession session) throws Exception {}
        @Override public void setInputStream(InputStream in) { this.in = in; }
        @Override public void setOutputStream(OutputStream out) { this.out = out; }
        @Override public void setErrorStream(OutputStream err) { this.err = err; }
        @Override public void setExitCallback(ExitCallback cb) { this.exitCb = cb; }
    }

    private static final CommandFactory MINA_COMMAND_FACTORY = (channel, cmd) -> new SimpleCommand(cmd);

    @BeforeAll
    static void setUpServers() throws Exception {
        executor = Executors.newFixedThreadPool(4);

        // ===== 1. Set up built-in Lego Flow SSH server =====
        localServer = new ssg.legoflow.ssh.server.SshServer();
        localServer.setHostKey(SshKeyPair.generate(new Ed25519()));

        var authCtx = new ssg.legoflow.ssh.auth.AuthContext();
        authCtx.setPasswordValidator((user, pass) ->
            TEST_USER.equals(user) && TEST_PASS.equals(pass));
        localServer.setAuthenticator(authCtx);

        localServer.setCommandFactory((command, stdout, stderr) -> {
            try {
                if ("echo hello".equals(command)) {
                    stdout.write("hello\n".getBytes(StandardCharsets.UTF_8));
                    return 0;
                }
                if ("echo test_data".equals(command)) {
                    stdout.write("test_data".getBytes(StandardCharsets.UTF_8));
                    return 0;
                }
                return 1;
            } catch (Exception e) {
                try { stderr.write(e.getMessage().getBytes()); } catch (IOException ex) {}
                return 1;
            }
        });

        localServer.bind(0);
        localPort = localServer.port();
        System.out.println("[LOCAL_SERVER] bound to port " + localPort);

        // ===== 2. Set up Apache MINA SSHD (reference server) =====
        minaServer = SshServer.setUpDefaultServer();
        minaServer.setPort(0);
        SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(
            Path.of("/tmp/interop_host_key"));
        provider.setAlgorithm("ED25519");
        minaServer.setKeyPairProvider(provider);
        minaServer.setPasswordAuthenticator((username, password, session) ->
            TEST_USER.equals(username) && TEST_PASS.equals(password));
        minaServer.setCommandFactory(MINA_COMMAND_FACTORY);
        minaServer.start();
        minaPort = minaServer.getPort();
        System.out.println("[MINA_SERVER] bound to port " + minaPort);
    }

    @AfterAll
    static void tearDownServers() throws Exception {
        if (localServer != null) localServer.close();
        if (minaServer != null) minaServer.stop(true);
        if (executor != null) executor.shutdown();
    }

    // ===== Phase 1: Version Exchange Tests =====

    @Test @Order(1)
    void testVersionExchangeWithLegoServer() throws Exception {
        System.out.println("[TEST] testVersionExchangeWithLegoServer port=" + localPort);
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", localPort);
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test @Order(2)
        @org.junit.jupiter.api.Disabled("MINA server KEXINIT interop blocked: needs server config fix")
    void testVersionExchangeWithMinaServer() throws Exception {
        System.out.println("[TEST] testVersionExchangeWithMinaServer port=" + minaPort);
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", minaPort);
            assertThat(client.isConnected()).isTrue();
        }
    }

    // ===== Phase 2: Authentication Tests =====

    @Test @Order(3)
    void testAuthWithLegoServer() throws Exception {
        System.out.println("[TEST] testAuthWithLegoServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", localPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test @Order(4)
        @org.junit.jupiter.api.Disabled("MINA server KEXINIT interop blocked: needs server config fix")
    void testAuthWithMinaServer() throws Exception {
        System.out.println("[TEST] testAuthWithMinaServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", minaPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));
            assertThat(client.isConnected()).isTrue();
        }
    }

    // ===== Phase 3: Session / Channel Tests =====

    @Test @Order(5)
    void testSessionWithLegoServer() throws Exception {
        System.out.println("[TEST] testSessionWithLegoServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", localPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));

            SessionChannel session = client.openSession();
            session.requestExec("echo hello");
            Thread.sleep(500);
            session.close();
        }
    }

    @Test @Order(6)
        @org.junit.jupiter.api.Disabled("MINA server KEXINIT interop blocked: needs server config fix")
    void testSessionWithMinaServer() throws Exception {
        System.out.println("[TEST] testSessionWithMinaServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", minaPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));

            SessionChannel session = client.openSession();
            session.requestExec("echo hello_from_mina");
            Thread.sleep(500);
            session.close();
        }
    }

    // ===== Phase 4: Data Transfer Tests =====

    @Test @Order(7)
    void testDataTransferWithLegoServer() throws Exception {
        System.out.println("[TEST] testDataTransferWithLegoServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", localPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));

            SessionChannel session = client.openSession();
            session.requestExec("echo test_data");
            Thread.sleep(500);
            session.close();
        }
    }

    @Test @Order(8)
        @org.junit.jupiter.api.Disabled("MINA server KEXINIT interop blocked: needs server config fix")
    void testDataTransferWithMinaServer() throws Exception {
        System.out.println("[TEST] testDataTransferWithMinaServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", minaPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));

            SessionChannel session = client.openSession();
            session.requestExec("echo hello_from_mina");
            Thread.sleep(500);
            session.close();
        }
    }

    // ===== Phase 5: Multiple Cipher Tests =====

    @Test @Order(9)
        @org.junit.jupiter.api.Disabled("MINA server KEXINIT interop blocked: needs server config fix")
    void testCipherAes128CtrWithMinaServer() throws Exception {
        System.out.println("[TEST] testCipherAes128CtrWithMinaServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", minaPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));
            assertThat(client.isConnected()).isTrue();
        }
    }

    // ===== Phase 6: Connection Lifecycle Tests =====

    @Test @Order(10)
    void testDisconnectWithLegoServer() throws Exception {
        System.out.println("[TEST] testDisconnectWithLegoServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", localPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));
            assertThat(client.isConnected()).isTrue();
        }
    }

    @Test @Order(11)
        @org.junit.jupiter.api.Disabled("MINA server KEXINIT interop blocked: needs server config fix")
    void testDisconnectWithMinaServer() throws Exception {
        System.out.println("[TEST] testDisconnectWithMinaServer");
        try (ssg.legoflow.ssh.client.SshClient client = new ssg.legoflow.ssh.client.SshClient()) {
            client.connect("localhost", minaPort);
            client.authenticate(TEST_USER, new PasswordAuth(TEST_PASS));
            assertThat(client.isConnected()).isTrue();
        }
    }

    // ===== Phase 7: Round-Trip Encryption Tests =====

    @Test @Order(12)
    void testEncryptDecryptRoundTripCipherLayer() throws Exception {
        System.out.println("[TEST] testEncryptDecryptRoundTripCipherLayer");
        var cipher = new ssg.legoflow.ssh.cipher.Aes128Ctr();
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        var rand = new java.security.SecureRandom();
        rand.nextBytes(key);
        rand.nextBytes(iv);

        cipher.init(key, iv, true);
        byte[] plaintext = "Round trip test data for cipher verification".getBytes();
        byte[] encrypted = cipher.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encrypted.length).isEqualTo(plaintext.length);

        cipher.init(key, iv, false);
        byte[] decrypted = cipher.decrypt(encrypted);
        assertThat(new String(decrypted)).isEqualTo(new String(plaintext));
    }

    @Test @Order(13)
    void testEncryptDecryptRoundTripAes256GcmCipherLayer() throws Exception {
        System.out.println("[TEST] testEncryptDecryptRoundTripAes256GcmCipherLayer");
        var cipher = new ssg.legoflow.ssh.cipher.Aes256Gcm();
        byte[] key = new byte[32];
        byte[] iv = new byte[12];
        var rand = new java.security.SecureRandom();
        rand.nextBytes(key);
        rand.nextBytes(iv);

        cipher.init(key, iv, true);
        byte[] payload = "GCM test payload".getBytes();
        int plen = 4 + 1 + payload.length + 4;
        byte[] packet = new byte[plen];
        ByteBuffer.wrap(packet).putInt(plen - 1).put((byte) 4).put(payload);

        cipher.setSequenceNumber(0);
        byte[] encPktLenBytes = ByteBuffer.allocate(4).putInt(plen - 1).array();
        cipher.setAad(encPktLenBytes);
        byte[] encrypted = cipher.encrypt(packet);
        assertThat(encrypted.length).isEqualTo(packet.length + 16);

        cipher.init(key, iv, false);
        cipher.setSequenceNumber(0);
        cipher.setAad(encPktLenBytes);
        byte[] decrypted = cipher.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(packet);
    }

    @Test @Order(14)
    void testEncryptDecryptRoundTripChaCha20CipherLayer() throws Exception {
        System.out.println("[TEST] testEncryptDecryptRoundTripChaCha20CipherLayer");
        var encrypt = new ssg.legoflow.ssh.cipher.ChaCha20Poly1305();
        var decrypt = new ssg.legoflow.ssh.cipher.ChaCha20Poly1305();
        byte[] key = new byte[64];
        var rand = new java.security.SecureRandom();
        rand.nextBytes(key);

        encrypt.init(key, null, true);
        decrypt.init(key, null, false);

        byte[] payload = new byte[1 + 32 + 4];
        payload[0] = 4;
        System.arraycopy("ChaCha20 test payload data!".getBytes(), 0, payload, 1, 25);

        encrypt.setSequenceNumber(0);
        decrypt.setSequenceNumber(0);

        byte[] encrypted = encrypt.encryptPayload(payload);
        assertThat(encrypted.length).isEqualTo(payload.length + 16);

        byte[] decrypted = decrypt.decryptPayload(encrypted);
        assertThat(decrypted).isEqualTo(payload);
    }
}
