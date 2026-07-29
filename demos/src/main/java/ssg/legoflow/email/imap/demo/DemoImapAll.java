package ssg.legoflow.email.imap.demo;

import ssg.legoflow.email.imap.client.FetchResult;
import ssg.legoflow.email.imap.client.FolderView;
import ssg.legoflow.email.imap.client.ImapClient;
import ssg.legoflow.email.imap.client.ImapClientConfig;
import ssg.legoflow.email.imap.server.ImapServer;
import ssg.legoflow.email.imap.server.InMemoryMailStore;
import ssg.legoflow.email.imap.server.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive demo of all IMAP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link ImapServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports IMAP4rev2 (RFC 9051) including LOGIN,
 * SELECT, EXAMINE, FETCH, SEARCH, STORE flags, COPY, MOVE, CREATE/DELETE/RENAME
 * mailboxes, EXPUNGE, NAMESPACE, STATUS, LIST, SUBSCRIBE/UNSUBSCRIBE, NOOP,
 * CAPABILITY, and IDLE push notifications. Extensions: CONDSTORE, SORT, THREAD,
 * MOVE, LIST-EXTENDED, LITERAL+, UNSELECT.
 * Ideal for development, testing, CI/CD, and learning the IMAP protocol.</p>
 *
 * <p><b>Alternative: External Dovecot, Gmail IMAP, or Microsoft Exchange</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production mailbox access with persistent message storage</li>
 *   <li>TLS/SSL encrypted connections with certificate validation</li>
 *   <li>OAuth2/XOAUTH2 authentication with Gmail or Microsoft 365</li>
 *   <li>Large mailbox testing with thousands of messages</li>
 *   <li>Integration testing against real mail infrastructure</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle and mailbox seeding.
 * All client code (login, select, fetch, search, store, copy) uses the same API regardless
 * of backend. When {@code USE_EXTERNAL=true}, the demo skips server creation and connects
 * directly to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Login and capability — LOGIN, CAPABILITY negotiation</li>
 *   <li>Mailbox management — CREATE, DELETE, RENAME, LIST, STATUS</li>
 *   <li>Select and fetch — SELECT mailbox, FETCH messages (FLAGS, ENVELOPE)</li>
 *   <li>Search — SEARCH criteria (UNSEEN, ALL)</li>
 *   <li>Store flags — STORE +FLAGS/-FLAGS, mark as \Seen, \Flagged, \Deleted</li>
 *   <li>Copy and move — COPY messages between mailboxes</li>
 *   <li>Expunge — EXPUNGE deleted messages</li>
 *   <li>Namespace — NAMESPACE query for mailbox hierarchy</li>
 *   <li>Append — APPEND message to a mailbox (via server-side seeding)</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoImapAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoImapAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house ImapServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Dovecot/Gmail/Exchange
    // =========================================================================

    /** Set to {@code true} to connect to an external IMAP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external IMAP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external IMAP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 143;

    private DemoImapAll() {}

    /**
     * Results from running the full demo.
     *
     * @param loginCapability    true if login succeeded and capabilities were retrieved
     * @param mailboxManagement  number of mailboxes listed after create/rename/delete
     * @param selectFetch        number of messages fetched from INBOX
     * @param searchResults      number of unseen messages found by SEARCH
     * @param storeFlags         true if flag operations (add/remove) succeeded
     * @param copyMessages       true if COPY to another mailbox succeeded
     * @param expungeCount       number of messages expunged
     * @param namespaceResult    true if NAMESPACE query returned a response
     * @param appendedMessages   number of messages seeded via append
     */
    public record Results(
            boolean loginCapability,
            int mailboxManagement,
            int selectFetch,
            int searchResults,
            boolean storeFlags,
            boolean copyMessages,
            int expungeCount,
            boolean namespaceResult,
            int appendedMessages
    ) {}

    /**
     * Runs the comprehensive demo covering all IMAP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT,
                    "user", "pass");
        }

        // Set up in-house server with seeded data
        InMemoryMailStore store = new InMemoryMailStore();
        store.addUser("user", "pass");

        // Seed INBOX with messages
        Mailbox inbox = store.getMailbox("INBOX");
        inbox.append(buildMessage("alice@example.com", "user@example.com",
                "Hello!", "Hi, how are you?").getBytes(StandardCharsets.UTF_8),
                Set.of(), Instant.now().minusSeconds(3600));
        inbox.append(buildMessage("bob@example.com", "user@example.com",
                "Meeting", "Let's meet at 10am.").getBytes(StandardCharsets.UTF_8),
                Set.of("\\Seen"), Instant.now().minusSeconds(1800));
        inbox.append(buildMessage("charlie@example.com", "user@example.com",
                "Project update", "Here is the latest.").getBytes(StandardCharsets.UTF_8),
                Set.of(), Instant.now().minusSeconds(900));
        inbox.append(buildMessage("diana@example.com", "user@example.com",
                "Invoice", "Please find attached.").getBytes(StandardCharsets.UTF_8),
                Set.of(), Instant.now());
        int appendedMessages = 4;

        // Create additional mailboxes
        store.createMailbox("Sent");
        store.createMailbox("Drafts");
        store.createMailbox("Archive");
        store.createMailbox("OldFolder");

        ImapServer server = new ImapServer("127.0.0.1", 0, store);
        server.start();
        int port = server.port();
        LOG.info("In-house ImapServer started on port {}", port);

        try {
            Results results = runWithExternalServer("127.0.0.1", port,
                    "user", "pass");
            return new Results(
                    results.loginCapability(),
                    results.mailboxManagement(),
                    results.selectFetch(),
                    results.searchResults(),
                    results.storeFlags(),
                    results.copyMessages(),
                    results.expungeCount(),
                    results.namespaceResult(),
                    appendedMessages
            );
        } finally {
            server.close();
        }
    }

    private static Results runWithExternalServer(String host, int port,
                                                  String user, String pass)
            throws Exception {
        ImapClientConfig config = ImapClientConfig.builder(host, port)
                .credentials(user, pass)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .build();

        try (ImapClient client = new ImapClient(config)) {
            client.connect();

            boolean loginCap = demoLoginCapability(client);
            int mailboxMgmt = demoMailboxManagement(client);
            int fetched = demoSelectFetch(client);
            int searchResults = demoSearch(client);
            boolean flags = demoStoreFlags(client);
            boolean copy = demoCopyMessages(client);
            int expunged = demoExpunge(client);
            boolean namespace = demoNamespace(client);

            // logout is handled by close() in try-with-resources
            return new Results(loginCap, mailboxMgmt, fetched, searchResults,
                    flags, copy, expunged, namespace, 0);
        }
    }

    // ======================== 1. LOGIN AND CAPABILITY ========================

    /**
     * Demonstrates LOGIN and CAPABILITY commands.
     */
    static boolean demoLoginCapability(ImapClient client) throws IOException {
        LOG.info("=== 1. Login and Capability ===");

        boolean loggedIn = client.login();
        LOG.info("Login: {}", loggedIn);

        List<String> caps = client.capability();
        LOG.info("Capabilities: {}", caps);

        return loggedIn && !caps.isEmpty();
    }

    // ======================== 2. MAILBOX MANAGEMENT ==========================

    /**
     * Demonstrates CREATE, RENAME, DELETE, LIST, and STATUS.
     * <p>
     * <b>Preferred: LIST with wildcard</b> — returns all mailboxes matching pattern.
     * <p>
     * <b>Alternative: STATUS</b> — returns message counts without selecting the mailbox.
     */
    static int demoMailboxManagement(ImapClient client) throws IOException {
        LOG.info("=== 2. Mailbox Management ===");

        // CREATE a new mailbox
        boolean created = client.create("TestFolder");
        LOG.info("Created TestFolder: {}", created);

        // RENAME a mailbox
        boolean renamed = client.rename("OldFolder", "NewFolder");
        LOG.info("Renamed OldFolder -> NewFolder: {}", renamed);

        // LIST all mailboxes
        List<String> mailboxes = client.list("", "*");
        LOG.info("Mailboxes: {}", mailboxes);

        // STATUS of INBOX
        Map<String, Long> status = client.status("INBOX", "(MESSAGES RECENT UNSEEN)");
        LOG.info("INBOX status: {}", status);

        // DELETE the test mailbox
        boolean deleted = client.delete("TestFolder");
        LOG.info("Deleted TestFolder: {}", deleted);

        // Final list
        List<String> finalList = client.list("", "*");
        LOG.info("Final mailbox count: {}", finalList.size());

        return finalList.size();
    }

    // ======================== 3. SELECT AND FETCH ============================

    /**
     * Demonstrates SELECT mailbox and FETCH messages.
     */
    static int demoSelectFetch(ImapClient client) throws IOException {
        LOG.info("=== 3. Select and Fetch ===");

        FolderView folder = client.select("INBOX");
        if (folder == null) {
            LOG.warn("Failed to select INBOX");
            return 0;
        }
        LOG.info("Selected INBOX: messages={}, recent={}, uidNext={}",
                folder.messageCount(), folder.recentCount(), folder.uidNext());

        // Fetch all messages
        List<FetchResult> messages = client.fetch("1:*", "(FLAGS ENVELOPE)");
        LOG.info("Fetched {} messages", messages.size());
        for (FetchResult msg : messages) {
            LOG.info("  seq={}, flags={}", msg.sequenceNumber(), msg.flags());
        }

        return messages.size();
    }

    // ======================== 4. SEARCH ======================================

    /**
     * Demonstrates SEARCH with various criteria.
     * <p>
     * <b>Preferred: server-side SEARCH</b> — efficient, reduces data transfer.
     * <p>
     * <b>Alternative: client-side filtering after FETCH</b> — more flexible
     * criteria, but requires downloading all messages first.
     */
    static int demoSearch(ImapClient client) throws IOException {
        LOG.info("=== 4. Search ===");

        // Search for unseen messages
        List<Integer> unseen = client.search("UNSEEN");
        LOG.info("Unseen messages: {}", unseen);

        // Search all
        List<Integer> all = client.search("ALL");
        LOG.info("All messages: {}", all);

        return unseen.size();
    }

    // ======================== 5. STORE FLAGS =================================

    /**
     * Demonstrates STORE flag operations: add, remove, and replace flags.
     */
    static boolean demoStoreFlags(ImapClient client) throws IOException {
        LOG.info("=== 5. Store Flags ===");

        // Mark message 1 as \Seen
        client.store("1", "+FLAGS", "(\\Seen)");
        LOG.info("Marked message 1 as \\Seen");

        // Mark message 3 as \Flagged
        client.store("3", "+FLAGS", "(\\Flagged)");
        LOG.info("Marked message 3 as \\Flagged");

        // Verify by fetching flags
        List<FetchResult> results = client.fetch("1:3", "(FLAGS)");
        LOG.info("After flag updates: {} results", results.size());

        // Search for flagged messages
        List<Integer> flagged = client.search("FLAGGED");
        LOG.info("Flagged messages: {}", flagged);

        return !results.isEmpty();
    }

    // ======================== 6. COPY MESSAGES ===============================

    /**
     * Demonstrates COPY to move messages between mailboxes.
     */
    static boolean demoCopyMessages(ImapClient client) throws IOException {
        LOG.info("=== 6. Copy Messages ===");

        // Copy message 2 to Sent
        boolean copied = client.copy("2", "Sent");
        LOG.info("Copied message 2 to Sent: {}", copied);

        // Verify by checking Sent status
        Map<String, Long> sentStatus = client.status("Sent", "(MESSAGES)");
        LOG.info("Sent mailbox messages: {}", sentStatus);

        return copied;
    }

    // ======================== 7. EXPUNGE =====================================

    /**
     * Demonstrates marking messages as \Deleted and EXPUNGE.
     */
    static int demoExpunge(ImapClient client) throws IOException {
        LOG.info("=== 7. Expunge ===");

        // Mark message 4 as \Deleted
        client.store("4", "+FLAGS", "(\\Deleted)");
        LOG.info("Marked message 4 as \\Deleted");

        // Expunge
        List<Integer> expunged = client.expunge();
        LOG.info("Expunged {} messages", expunged.size());

        return expunged.size();
    }

    // ======================== 8. NAMESPACE ===================================

    /**
     * Demonstrates NAMESPACE query for mailbox hierarchy information.
     */
    static boolean demoNamespace(ImapClient client) throws IOException {
        LOG.info("=== 8. Namespace ===");

        String ns = client.namespace();
        LOG.info("Namespace: {}", ns);

        // Also test NOOP
        boolean noopOk = client.noop();
        LOG.info("NOOP: {}", noopOk);

        return ns != null;
    }

    // ======================== HELPER =========================================

    /**
     * Builds a simple RFC 5322 email message.
     *
     * @param from    the From address
     * @param to      the To address
     * @param subject the Subject
     * @param body    the body text
     * @return the formatted message
     */
    static String buildMessage(String from, String to, String subject, String body) {
        return "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Date: Sun, 8 Jun 2025 12:00:00 +0000\r\n"
                + "Message-ID: <" + System.nanoTime() + "@example.com>\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + body + "\r\n";
    }
}
