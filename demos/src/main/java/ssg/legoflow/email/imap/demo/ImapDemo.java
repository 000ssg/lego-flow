package ssg.legoflow.email.imap.demo;

import ssg.legoflow.email.imap.client.FetchResult;
import ssg.legoflow.email.imap.client.FolderView;
import ssg.legoflow.email.imap.client.ImapClient;
import ssg.legoflow.email.imap.client.ImapClientConfig;
import ssg.legoflow.email.imap.server.ImapServer;
import ssg.legoflow.email.imap.server.InMemoryMailStore;
import ssg.legoflow.email.imap.server.Mailbox;
import ssg.legoflow.email.imap.server.StoredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Demonstrates a complete IMAP workflow: login, list, select, fetch, flag, delete.
 *
 * <p>Starts an in-memory IMAP server, seeds it with test messages, then
 * connects a client to perform mailbox operations.
 *
 * @since 0.1.0
 */
public final class ImapDemo {

    private static final Logger LOG = LoggerFactory.getLogger(ImapDemo.class);

    private ImapDemo() {
    }

    /**
     * Runs the demo.
     *
     * @return true if the demo completed successfully
     * @throws Exception if an error occurs
     */
    public static boolean run() throws Exception {
        // Setup server
        InMemoryMailStore store = new InMemoryMailStore();
        store.addUser("user", "pass");

        // Seed INBOX with messages
        Mailbox inbox = store.getMailbox("INBOX");
        inbox.append(buildMessage("alice@example.com", "user@example.com",
                "Hello!", "Hi there, how are you?").getBytes(StandardCharsets.UTF_8),
                Set.of(), Instant.now().minusSeconds(3600));
        inbox.append(buildMessage("bob@example.com", "user@example.com",
                "Meeting tomorrow", "Let's meet at 10am.").getBytes(StandardCharsets.UTF_8),
                Set.of("\\Seen"), Instant.now().minusSeconds(1800));
        inbox.append(buildMessage("charlie@example.com", "user@example.com",
                "Re: Project update", "Here is the latest update.").getBytes(StandardCharsets.UTF_8),
                Set.of(), Instant.now());

        // Create additional folders
        store.createMailbox("Sent");
        store.createMailbox("Drafts");
        store.createMailbox("Trash");

        ImapServer server = new ImapServer("127.0.0.1", 0, store);
        server.start();
        int port = server.port();

        try {
            // Connect client
            ImapClientConfig config = ImapClientConfig.builder("127.0.0.1", port)
                    .credentials("user", "pass")
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(5))
                    .build();

            try (ImapClient client = new ImapClient(config)) {
                client.connect();

                // Login
                boolean loggedIn = client.login();
                LOG.info("Login: {}", loggedIn);
                if (!loggedIn) return false;

                // List mailboxes
                List<String> mailboxes = client.list("", "*");
                LOG.info("Mailboxes: {}", mailboxes);

                // Select INBOX
                FolderView folder = client.select("INBOX");
                LOG.info("Selected INBOX: {}", folder);
                if (folder == null) return false;

                // Fetch all messages
                List<FetchResult> messages = client.fetch("1:*", "(FLAGS ENVELOPE)");
                LOG.info("Fetched {} messages", messages.size());

                // Search for unseen messages
                List<Integer> unseen = client.search("UNSEEN");
                LOG.info("Unseen messages: {}", unseen);

                // Flag a message as \Seen
                client.store("1", "+FLAGS", "(\\Seen)");
                LOG.info("Marked message 1 as seen");

                // Copy message to Sent
                client.copy("2", "Sent");
                LOG.info("Copied message 2 to Sent");

                // Delete a message
                client.store("3", "+FLAGS", "(\\Deleted)");
                List<Integer> expunged = client.expunge();
                LOG.info("Expunged {} messages", expunged.size());

                // Logout
                client.logout();
                LOG.info("Logged out");

                return true;
            }
        } finally {
            server.close();
        }
    }

    /**
     * Builds a simple RFC 5322 email message.
     *
     * @param from    the From address
     * @param to      the To address
     * @param subject the Subject
     * @param body    the body text
     * @return the formatted message
     */
    public static String buildMessage(String from, String to, String subject, String body) {
        return "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Date: Mon, 1 Jan 2024 12:00:00 +0000\r\n"
                + "Message-ID: <" + System.nanoTime() + "@example.com>\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + body + "\r\n";
    }

    /**
     * Entry point for running the demo standalone.
     *
     * @param args command-line arguments (unused)
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        boolean success = run();
        System.out.println("Demo " + (success ? "succeeded" : "failed"));
    }
}
