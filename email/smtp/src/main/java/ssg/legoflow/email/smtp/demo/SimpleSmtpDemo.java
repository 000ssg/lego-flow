package ssg.legoflow.email.smtp.demo;

import ssg.legoflow.email.smtp.client.SmtpClient;
import ssg.legoflow.email.smtp.client.SmtpClientConfig;
import ssg.legoflow.email.smtp.server.InMemoryMessageStore;
import ssg.legoflow.email.smtp.server.SmtpHandler;
import ssg.legoflow.email.smtp.server.SmtpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simple SMTP demo: local server and client exchanging a single message.
 *
 * <p>Demonstrates the basic SMTP workflow:
 * <ol>
 *   <li>Start an SMTP server on an ephemeral port</li>
 *   <li>Connect a client and send a plain text message</li>
 *   <li>Verify the message was stored</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class SimpleSmtpDemo {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleSmtpDemo.class);

    private SimpleSmtpDemo() {
    }

    /**
     * Runs the demo: sends an email from alice to bob via a local SMTP server.
     *
     * @return the stored message data as a string
     * @throws Exception if something fails
     */
    public static String run() throws Exception {
        var store = new InMemoryMessageStore();

        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            int port = server.getPort();
            LOG.info("SMTP server started on port {}", port);

            var config = SmtpClientConfig.builder("localhost", port)
                    .localHostname("client.example.com")
                    .build();

            try (var client = new SmtpClient(config)) {
                client.connect();
                String message = "From: alice@example.com\r\n"
                        + "To: bob@example.com\r\n"
                        + "Subject: Hello from Lego Flow SMTP\r\n"
                        + "Date: Thu, 5 Jun 2025 10:00:00 +0000\r\n"
                        + "MIME-Version: 1.0\r\n"
                        + "Content-Type: text/plain; charset=utf-8\r\n"
                        + "\r\n"
                        + "Hello Bob!\r\n"
                        + "\r\n"
                        + "This is a test message from the Lego Flow SMTP module.\r\n";

                client.send("alice@example.com", List.of("bob@example.com"), message);
                LOG.info("Message sent successfully");
            }
        }

        var stored = store.getLastMessage();
        if (stored != null) {
            LOG.info("Stored message: from={}, to={}, size={}",
                    stored.sender(), stored.recipients(), stored.size());
            return stored.dataAsString();
        }
        return null;
    }
}
