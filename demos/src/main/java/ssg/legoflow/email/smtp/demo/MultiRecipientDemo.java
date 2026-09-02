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
 * Demo: sending a message to multiple recipients.
 *
 * <p>Demonstrates multiple RCPT TO commands in a single transaction
 * and verifying delivery to all recipients.
 *
 * @since 0.1.0
 */
public final class MultiRecipientDemo {

    private static final Logger LOG = LoggerFactory.getLogger(MultiRecipientDemo.class);

    private MultiRecipientDemo() {
    }

    /**
     * Runs the multi-recipient demo.
     *
     * @return the number of messages stored
     * @throws Exception if something fails
     */
    public static int run() throws Exception {
        var store = new InMemoryMessageStore();

        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();

            var config = SmtpClientConfig.builder("localhost", server.getPort())
                    .localHostname("client.example.com")
                    .build();

            try (var client = new SmtpClient(config)) {
                client.connect();

                var recipients = List.of(
                        "alice@example.com",
                        "bob@example.com",
                        "charlie@example.com"
                );

                String message = "From: sender@example.com\r\n"
                        + "To: alice@example.com, bob@example.com, charlie@example.com\r\n"
                        + "Subject: Team Update\r\n"
                        + "\r\n"
                        + "Hello team, this is an update for everyone.\r\n";

                client.send("sender@example.com", recipients, message);
                LOG.info("Message sent to {} recipients", recipients.size());
            }
        }

        LOG.info("Stored messages: {}", store.getMessageCount());
        return store.getMessageCount();
    }
}
