package ssg.legoflow.interop.ftp;

import org.junit.jupiter.api.*;
import ssg.legoflow.ftp.client.FtpClient;
import ssg.legoflow.ftp.client.FtpClientConfig;
import ssg.legoflow.ftp.client.FtpFileEntry;
import ssg.legoflow.ftp.protocol.FtpReply;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow FTP client ↔ real FTP server.
 *
 * <p>Uses an external FTP server (default: anonymous FTP on ftp.testMerit.net)
 * to verify that the Lego Flow FTP client can connect, authenticate, and
 * perform basic file operations.
 *
 * <p>Configuration via system properties:
 *   interop.ftp.host (default: ftp.testMerit.net)
 *   interop.ftp.port (default: 21)
 *   interop.ftp.username (default: anonymous)
 *   interop.ftp.password (default: guest@)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FtpInteropTest {

    private final String host = System.getProperty("interop.ftp.host", "ftp.testMerit.net");
    private final int port = Integer.parseInt(System.getProperty("interop.ftp.port", "21"));
    private final String username = System.getProperty("interop.ftp.username", "anonymous");
    private final String password = System.getProperty("interop.ftp.password", "guest@");

    private FtpClient client;

    @BeforeAll
    void connect() throws Exception {
        FtpClientConfig config = FtpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(30))
                .soTimeout(Duration.ofSeconds(30))
                .build();
        this.client = new FtpClient(config);
        FtpReply reply = client.connect(host, port);
        assertThat(reply.code()).isEqualTo(220);
    }

    @AfterAll
    void disconnect() throws Exception {
        if (client != null) {
            client.disconnect();
        }
    }

    @Test
    void testConnect() {
        assertThat(client).isNotNull();
    }

    @Test
    void testLogin() throws Exception {
        FtpReply reply = client.login(username, password);
        assertThat(reply.code()).isEqualTo(230);
    }

    @Test
    void testPwd() throws Exception {
        client.login(username, password);
        String cwd = client.pwd();
        assertThat(cwd).isNotNull();
    }

    @Test
    void testListRoot() throws Exception {
        client.login(username, password);
        List<FtpFileEntry> entries = client.list("/");
        assertThat(entries).isNotEmpty();
    }

    @Test
    void testMlstRoot() throws Exception {
        client.login(username, password);
        List<FtpFileEntry> entries = client.mlsd("/");
        assertThat(entries).isNotEmpty();
    }

    @Test
    void testCdup() throws Exception {
        client.login(username, password);
        client.cd("/pub");
        FtpReply reply = client.cdup();
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testNlstRoot() throws Exception {
        client.login(username, password);
        List<String> names = client.nlst("/pub");
        assertThat(names).isNotEmpty();
    }

    @Test
    void testRenameDir() throws Exception {
        client.login(username, password);
        FtpReply mkdir = client.mkdir("/test-interop");
        assertThat(mkdir.code()).isIn(250, 550); // 250 = created, 550 = already exists
        if (mkdir.code() == 250) {
            try {
                FtpReply reply = client.rename("/test-interop", "/test-interop-renamed");
                assertThat(reply.code()).isEqualTo(250);
                client.rmdir("/test-interop-renamed");
            } catch (Exception e) {
                // Rename may not be allowed for anonymous users
            }
        }
    }

    @Test
    void testRmdir() throws Exception {
        client.login(username, password);
        try {
            client.rmdir("/test-interop-delete");
        } catch (Exception e) {
            // Expected - directory doesn't exist
            assertThat(e.getMessage()).contains("550");
        }
    }
}
