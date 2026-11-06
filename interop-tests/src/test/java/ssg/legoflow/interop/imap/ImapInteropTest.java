package ssg.legoflow.interop.imap;

import org.junit.jupiter.api.*;
import ssg.legoflow.email.imap.client.ImapClient;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Disabled;

/**
 * Interoperability test: Lego Flow IMAP client ↔ real IMAP server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("Requires external IMAP server: docker compose -f interop-tests/docker-compose.core.yml up -d imap")
class ImapInteropTest {

    private final String host = System.getProperty("interop.imap.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.imap.port", "143"));
    private final String user = System.getProperty("interop.imap.user", "test");
    private final String password = System.getProperty("interop.imap.password", "test");

    private ImapClient client;

    @BeforeAll
    void connect() throws IOException {
        this.client = ImapClient.connect(host, port, user, password);
    }

    @AfterAll
    void disconnect() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testListFolders() throws Exception {
        var result = client.listFolders();
        assertThat(result).isNotNull();
    }

    @Test
    void testSelectInbox() throws Exception {
        var result = client.selectFolder("INBOX");
        assertThat(result).isNotNull();
    }
}
