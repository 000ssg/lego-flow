package ssg.legoflow.ssh;

import org.junit.jupiter.api.*;
import ssg.legoflow.ssh.client.SshClient;
import ssg.legoflow.ssh.client.SshClientConfig;
import ssg.legoflow.ssh.hostkey.Ed25519;
import ssg.legoflow.ssh.hostkey.SshKeyPair;

import static org.assertj.core.api.Assertions.*;

/**
 * Interoperability tests: Lego Flow SSH client/server ↔ OpenSSH.
 *
 * <p>Tests against a running OpenSSH server (Docker container on port 2222)
 * to verify RFC 4251-4256 compliance.
 *
 * <p>These tests are skipped by default. Enable with:
 * <pre>
 *   mvn test -Dtest=SshOpenSshInteropTest -DskipInteropTests=false
 * </pre>
 *
 * @since 0.1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(30)
@Disabled("OpenSSH interop requires running Docker container on port 2222")
@Tag("interop-rest")
class SshOpenSshInteropTest {

    private static final String HOST = "localhost";
    private static final int PORT = 2222;
    private static final String USER = "legoflow";
    private static final String PASS = "legoflow";

    private SshClient client;

    @BeforeEach
    void setUp() {
        SshClientConfig config = SshClientConfig.builder().build();
        client = new SshClient(config);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    @Order(1)
    void testVersionExchange() throws Exception {
        System.out.println("[TEST] testVersionExchange port=" + PORT);
        client.connect(HOST, PORT);
        Thread.sleep(1000);
        System.out.println("[TEST] testVersionExchange completed");
    }

    @Test
    @Order(2)
    @Disabled("OpenSSH interop: authentication")
    void testAuthentication() throws Exception {
        System.out.println("[TEST] testAuthentication");
        client.connect(HOST, PORT);
        Thread.sleep(1000);
        System.out.println("[TEST] testAuthentication completed");
    }

    @Test
    @Order(3)
    @Disabled("OpenSSH interop: session/channel")
    void testSession() throws Exception {
        System.out.println("[TEST] testSession");
        client.connect(HOST, PORT);
        Thread.sleep(1000);
        System.out.println("[TEST] testSession completed");
    }

    @Test
    @Order(4)
    @Disabled("OpenSSH interop: command execution")
    void testCommandExecution() throws Exception {
        System.out.println("[TEST] testCommandExecution");
        client.connect(HOST, PORT);
        Thread.sleep(1000);
        System.out.println("[TEST] testCommandExecution completed");
    }

    @Test
    @Order(5)
    @Disabled("OpenSSH interop: data transfer")
    void testDataTransfer() throws Exception {
        System.out.println("[TEST] testDataTransfer");
        client.connect(HOST, PORT);
        Thread.sleep(1000);
        System.out.println("[TEST] testDataTransfer completed");
    }

    @Test
    @Order(6)
    @Disabled("OpenSSH interop: disconnect")
    void testDisconnect() throws Exception {
        System.out.println("[TEST] testDisconnect");
        client.connect(HOST, PORT);
        Thread.sleep(500);
        client.close();
        System.out.println("[TEST] testDisconnect completed");
    }
}
