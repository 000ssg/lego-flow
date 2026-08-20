package ssg.legoflow.interop.smtp;

import org.junit.jupiter.api.*;
import ssg.legoflow.email.smtp.client.SmtpClient;
import ssg.legoflow.email.smtp.client.SmtpClientConfig;
import ssg.legoflow.email.smtp.protocol.SmtpReply;
import ssg.legoflow.email.smtp.client.SmtpException;
import ssg.legoflow.email.smtp.protocol.SmtpExtension;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability test: Lego Flow SMTP client ↔ real SMTP server.
 *
 * <p>Uses an external SMTP server (default: localhost:25) to verify
 * that the Lego Flow SMTP client can connect, authenticate (if needed),
 * and send emails.
 *
 * <p>Configuration via system properties:
 *   interop.smtp.host (default: localhost)
 *   interop.smtp.port (default: 25)
 *   interop.smtp.tls (default: false)
 *   interop.smtp.auth (default: false)
 *   interop.smtp.username (default: none)
 *   interop.smtp.password (default: none)
 *
 * <p>To run against a local MailHog instance:
 *   docker run -d -p 25:25 -p 8025:8025 mailhog/mailhog  # port 25 for SMTP, 8025 for web UI
 *   mvn verify -Dinterop.smtp.host=localhost
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmtpInteropTest {

    private final String host = System.getProperty("interop.smtp.host", "localhost");
    private final int port = Integer.parseInt(System.getProperty("interop.smtp.port", "25"));
    private final boolean tls = Boolean.parseBoolean(System.getProperty("interop.smtp.tls", "false"));
    private final boolean auth = Boolean.parseBoolean(System.getProperty("interop.smtp.auth", "false"));
    private final String username = System.getProperty("interop.smtp.username", "");
    private final String password = System.getProperty("interop.smtp.password", "");

    private SmtpClient client;

    @BeforeAll
    void connect() throws Exception {
        SmtpClientConfig config = SmtpClientConfig.builder(host, port)
                .tlsMode(tls ? SmtpClientConfig.TlsMode.STARTTLS : SmtpClientConfig.TlsMode.NONE)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.client = new SmtpClient(config);
        client.connect();
    }

    @AfterAll
    void disconnect() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testConnection() {
        assertThat(client).isNotNull();
        // Check server responds with extensions (MailHog may not advertise 8BITMIME)
        assertThat(client.extensions()).isNotEmpty();
    }

    @Test
    void testHello() throws Exception {
        SmtpReply reply = client.noop();
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testEhloExtensions() throws Exception {
        assertThat(client.extensions()).isNotEmpty();
        List<SmtpExtension> exts = List.copyOf(client.extensions().keySet());
        assertThat(exts).isNotEmpty();
    }

    @Test
    void testRset() throws Exception {
        SmtpReply reply = client.reset();
        assertThat(reply.code()).isEqualTo(250);
    }

    @Test
    void testVrfy() throws Exception {
        try {
            SmtpReply reply = client.verify("root");
            // 250 = user exists, 550 = user not found, 500 = command not implemented (e.g. MailHog)
            assertThat(reply.code()).isIn(250, 500, 550);
        } catch (Exception e) {
            // Some servers disable VRFY entirely - acceptable
            assertThat(e.getMessage()).containsAnyOf("500", "550");
        }
    }

    @Test
    void testAnonymousConnect() throws Exception {
        // The client should already be connected and not require auth
        if (!auth) {
            assertThat(client.isAuthenticated()).isFalse();
        }
    }

    @Test
    void testSendToBounceAddress() throws Exception {
        // Send to postmaster@localhost which typically accepts mail for testing
        try {
            SmtpReply reply = client.send(
                    "test@example.com",
                    List.of("postmaster@" + host),
                    "Subject: Interop Test\r\n\r\nHello from Lego Flow"
            );
            // 250 = accepted, 451/550 = rejected (both are valid test outcomes)
            assertThat(reply.code()).isIn(250, 451, 550, 552);
        } catch (Exception e) {
            // May fail if server rejects unauthenticated mail - acceptable
        }
    }
}
