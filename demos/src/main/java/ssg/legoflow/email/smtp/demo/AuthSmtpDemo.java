package ssg.legoflow.email.smtp.demo;

import ssg.legoflow.email.smtp.client.SmtpClient;
import ssg.legoflow.email.smtp.client.SmtpClientConfig;
import ssg.legoflow.email.smtp.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
/**
 * Authenticated SMTP demo: server requires AUTH before sending.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Server with authentication requirement</li>
 *   <li>Client PLAIN authentication</li>
 *   <li>Relay restrictions by domain</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class AuthSmtpDemo {

    private static final Logger LOG = LoggerFactory.getLogger(AuthSmtpDemo.class);

    private AuthSmtpDemo() {
    }

    /**
     * Runs the authenticated SMTP demo.
     *
     * @return the stored message data as a string
     * @throws Exception if something fails
     */
    public static String run() throws Exception {
        var store = new InMemoryMessageStore();

        var handler = new SmtpHandler() {
            @Override
            public boolean authenticate(String username, String password) {
                return "testuser".equals(username) && "testpass".equals(password);
            }

            @Override
            public boolean acceptRecipient(String recipient, String sender) {
                return recipient != null && recipient.endsWith("@example.com");
            }
        };

        var relayConfig = RelayConfig.builder()
                .allowDomain("example.com")
                .requireAuth(true)
                .maxMessageSize(5 * 1024 * 1024)
                .build();

        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(handler);
            server.setRelayConfig(relayConfig);
            server.start();

            var config = SmtpClientConfig.builder("localhost", server.getPort())
                    .auth("testuser", "testpass")
                    .authMechanism("PLAIN")
                    .localHostname("client.example.com")
                    .build();

            try (var client = new SmtpClient(config)) {
                client.connect();

                String message = "From: testuser@example.com\r\n"
                        + "To: recipient@example.com\r\n"
                        + "Subject: Authenticated Message\r\n"
                        + "\r\n"
                        + "This message was sent with SMTP authentication.\r\n";

                client.send("testuser@example.com",
                        List.of("recipient@example.com"), message);
                LOG.info("Authenticated message sent");
            }
        }

        var stored = store.getLastMessage();
        return stored != null ? stored.dataAsString() : null;
    }
}
