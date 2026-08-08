package ssg.legoflow.email.smtp.demo;

import ssg.legoflow.email.smtp.client.SmtpClient;
import ssg.legoflow.email.smtp.client.SmtpClientConfig;
import ssg.legoflow.email.smtp.protocol.SmtpExtension;
import ssg.legoflow.email.smtp.protocol.SmtpReply;
import ssg.legoflow.email.smtp.server.InMemoryMessageStore;
import ssg.legoflow.email.smtp.server.RelayConfig;
import ssg.legoflow.email.smtp.server.SmtpHandler;
import ssg.legoflow.email.smtp.server.SmtpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Comprehensive demo of all SMTP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link SmtpServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports EHLO/HELO, MAIL FROM, RCPT TO, DATA,
 * RSET, NOOP, VRFY, QUIT, SASL authentication (PLAIN, LOGIN, CRAM-MD5, XOAUTH2),
 * STARTTLS, pipelining, chunked transfer (BDAT), DSN, SIZE, 8BITMIME, and relay
 * restrictions. Ideal for development, testing, CI/CD, and learning the SMTP protocol.</p>
 *
 * <p><b>Alternative: External Postfix, Gmail SMTP, Microsoft Exchange, or Amazon SES</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production email delivery with real MX resolution and DKIM/SPF/DMARC</li>
 *   <li>TLS certificate validation with trusted CA chains</li>
 *   <li>Rate limiting, reputation management, and bounce handling</li>
 *   <li>Integration testing against real mail infrastructure</li>
 *   <li>OAuth2/XOAUTH2 authentication with Gmail or Microsoft 365</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (send, authenticate, extensions) uses the same API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo skips server creation and connects directly
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Basic send — EHLO, MAIL FROM, RCPT TO, DATA, single message</li>
 *   <li>Multi-recipient — multiple RCPT TO commands in one transaction</li>
 *   <li>MIME messages — structured headers, Content-Type, multipart-ready format</li>
 *   <li>Authentication — SASL PLAIN mechanism with relay restrictions</li>
 *   <li>Session management — RSET, NOOP, multiple transactions on one connection</li>
 *   <li>Extension negotiation — EHLO capabilities: SIZE, 8BITMIME, PIPELINING, AUTH</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoSmtpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSmtpAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house SmtpServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Postfix/Gmail/Exchange/SES
    // =========================================================================

    /** Set to {@code true} to connect to an external SMTP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external SMTP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external SMTP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 25;

    private DemoSmtpAll() {}

    /**
     * Results from running the full demo.
     *
     * @param basicSend         true if single message send succeeded
     * @param multiRecipient    number of recipients in multi-recipient send
     * @param mimeMessage       true if MIME-formatted message was accepted
     * @param authentication    true if authenticated send with relay restrictions succeeded
     * @param sessionManagement true if RSET/NOOP and multiple transactions succeeded
     * @param extensionCount    number of ESMTP extensions negotiated via EHLO
     */
    public record Results(
            boolean basicSend,
            int multiRecipient,
            boolean mimeMessage,
            boolean authentication,
            boolean sessionManagement,
            int extensionCount
    ) {}

    /**
     * Runs the comprehensive demo covering all SMTP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT);
        }
        var store = new InMemoryMessageStore();

        try (var server = new SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            int port = server.getPort();
            LOG.info("In-house SmtpServer started on port {}", port);

            Results common = runWithExternalServer("localhost", port);

            // Features requiring server control
            boolean auth = demoAuthentication(store, port);

            return new Results(
                    common.basicSend(),
                    common.multiRecipient(),
                    common.mimeMessage(),
                    auth,
                    common.sessionManagement(),
                    common.extensionCount()
            );
        }
    }

    private static Results runWithExternalServer(String host, int port) throws Exception {
        boolean basicSend = demoBasicSend(host, port);
        int multiRecipient = demoMultiRecipient(host, port);
        boolean mime = demoMimeMessage(host, port);
        boolean session = demoSessionManagement(host, port);
        int extensions = demoExtensionNegotiation(host, port);

        return new Results(basicSend, multiRecipient, mime,
                false /* auth filled later for in-house */, session, extensions);
    }

    // ======================== 1. BASIC SEND ==================================

    /**
     * Demonstrates the basic SMTP workflow: EHLO, MAIL FROM, RCPT TO, DATA.
     */
    static boolean demoBasicSend(String host, int port) throws Exception {
        LOG.info("=== 1. Basic Send ===");
        var config = SmtpClientConfig.builder(host, port)
                .localHostname("demo.example.com")
                .build();

        try (var client = new SmtpClient(config)) {
            client.connect();

            String message = "From: alice@example.com\r\n"
                    + "To: bob@example.com\r\n"
                    + "Subject: Hello from Lego Flow SMTP Demo\r\n"
                    + "Date: Sun, 8 Jun 2025 12:00:00 +0000\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "\r\n"
                    + "Hello Bob, this is a test message.\r\n";

            SmtpReply reply = client.send("alice@example.com",
                    List.of("bob@example.com"), message);
            LOG.info("Basic send reply: {} {}", reply.code(), reply.text());
            return reply.isSuccess();
        }
    }

    // ======================== 2. MULTI-RECIPIENT =============================

    /**
     * Demonstrates sending to multiple recipients with multiple RCPT TO commands.
     */
    static int demoMultiRecipient(String host, int port) throws Exception {
        LOG.info("=== 2. Multi-Recipient ===");
        var config = SmtpClientConfig.builder(host, port)
                .localHostname("demo.example.com")
                .build();

        var recipients = List.of(
                "alice@example.com",
                "bob@example.com",
                "charlie@example.com",
                "diana@example.com"
        );

        try (var client = new SmtpClient(config)) {
            client.connect();

            String message = "From: sender@example.com\r\n"
                    + "To: alice@example.com, bob@example.com, charlie@example.com, diana@example.com\r\n"
                    + "Subject: Team Announcement\r\n"
                    + "\r\n"
                    + "This message is sent to all team members.\r\n";

            SmtpReply reply = client.send("sender@example.com", recipients, message);
            LOG.info("Multi-recipient send: {} recipients, reply={}", recipients.size(), reply.code());
            return reply.isSuccess() ? recipients.size() : 0;
        }
    }

    // ======================== 3. MIME MESSAGE =================================

    /**
     * Demonstrates sending a properly formatted MIME message with structured headers.
     * <p>
     * <b>Preferred: text/plain</b> — simple, universally supported, fast parsing.
     * <p>
     * <b>Alternative: multipart/mixed</b> — for attachments and HTML bodies.
     * Requires proper boundary generation and Content-Transfer-Encoding.
     */
    static boolean demoMimeMessage(String host, int port) throws Exception {
        LOG.info("=== 3. MIME Message ===");
        var config = SmtpClientConfig.builder(host, port)
                .localHostname("demo.example.com")
                .build();

        try (var client = new SmtpClient(config)) {
            client.connect();

            String message = "From: sender@example.com\r\n"
                    + "To: recipient@example.com\r\n"
                    + "Subject: MIME Test Message\r\n"
                    + "Date: Sun, 8 Jun 2025 12:00:00 +0000\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Transfer-Encoding: 7bit\r\n"
                    + "Message-ID: <demo-" + System.nanoTime() + "@example.com>\r\n"
                    + "X-Mailer: Lego-Flow-SMTP/1.0\r\n"
                    + "\r\n"
                    + "This is a MIME-compliant message with full headers.\r\n"
                    + "It demonstrates RFC 2045 Content-Type and encoding headers.\r\n";

            SmtpReply reply = client.send("sender@example.com",
                    List.of("recipient@example.com"), message);
            LOG.info("MIME message reply: {}", reply.code());
            return reply.isSuccess();
        }
    }

    // ======================== 4. AUTHENTICATION ==============================

    /**
     * Demonstrates SASL PLAIN authentication with relay restrictions.
     * <p>
     * <b>Preferred: PLAIN</b> — simple, widely supported. Always use with TLS
     * in production to protect credentials on the wire.
     * <p>
     * <b>Alternative: CRAM-MD5</b> — challenge-response, password not sent
     * over wire. Preferred when TLS is not available.
     * <p>
     * <b>Alternative: LOGIN</b> — legacy mechanism, base64-encoded credentials.
     * Supported for compatibility with older clients.
     */
    static boolean demoAuthentication(InMemoryMessageStore store, int port) throws Exception {
        LOG.info("=== 4. Authentication ===");

        // Create a new server with auth requirement
        var handler = new SmtpHandler() {
            @Override
            public boolean authenticate(String username, String password) {
                return "demouser".equals(username) && "demopass".equals(password);
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

        try (var authServer = new SmtpServer("localhost", 0)) {
            authServer.setMessageStore(store);
            authServer.setHandler(handler);
            authServer.setRelayConfig(relayConfig);
            authServer.start();

            var config = SmtpClientConfig.builder("localhost", authServer.getPort())
                    .auth("demouser", "demopass")
                    .authMechanism("PLAIN")
                    .localHostname("demo.example.com")
                    .build();

            try (var client = new SmtpClient(config)) {
                client.connect();

                String message = "From: demouser@example.com\r\n"
                        + "To: recipient@example.com\r\n"
                        + "Subject: Authenticated Message\r\n"
                        + "\r\n"
                        + "This message was sent with SMTP PLAIN authentication.\r\n";

                SmtpReply reply = client.send("demouser@example.com",
                        List.of("recipient@example.com"), message);
                boolean authenticated = client.isAuthenticated();
                LOG.info("Authenticated send: auth={}, reply={}", authenticated, reply.code());
                return authenticated && reply.isSuccess();
            }
        }
    }

    // ======================== 5. SESSION MANAGEMENT ==========================

    /**
     * Demonstrates RSET, NOOP, and multiple transactions on one connection.
     * <p>
     * RSET resets the current transaction (MAIL FROM + RCPT TO) without
     * disconnecting, allowing a fresh send on the same connection.
     */
    static boolean demoSessionManagement(String host, int port) throws Exception {
        LOG.info("=== 5. Session Management ===");
        var config = SmtpClientConfig.builder(host, port)
                .localHostname("demo.example.com")
                .build();

        try (var client = new SmtpClient(config)) {
            client.connect();

            // NOOP — keep-alive
            SmtpReply noopReply = client.noop();
            LOG.info("NOOP: {}", noopReply.code());

            // First transaction
            String msg1 = "From: a@example.com\r\n"
                    + "To: b@example.com\r\n"
                    + "Subject: Message 1\r\n"
                    + "\r\n"
                    + "First message on this connection.\r\n";
            SmtpReply reply1 = client.send("a@example.com", List.of("b@example.com"), msg1);
            LOG.info("Transaction 1: {}", reply1.code());

            // RSET — reset for new transaction
            SmtpReply rsetReply = client.reset();
            LOG.info("RSET: {}", rsetReply.code());

            // Second transaction on same connection
            String msg2 = "From: c@example.com\r\n"
                    + "To: d@example.com\r\n"
                    + "Subject: Message 2\r\n"
                    + "\r\n"
                    + "Second message on the same connection.\r\n";
            SmtpReply reply2 = client.send("c@example.com", List.of("d@example.com"), msg2);
            LOG.info("Transaction 2: {}", reply2.code());

            return noopReply.isSuccess() && reply1.isSuccess()
                    && rsetReply.isSuccess() && reply2.isSuccess();
        }
    }

    // ======================== 6. EXTENSION NEGOTIATION ========================

    /**
     * Demonstrates EHLO extension negotiation and available capabilities.
     */
    static int demoExtensionNegotiation(String host, int port) throws Exception {
        LOG.info("=== 6. Extension Negotiation ===");
        var config = SmtpClientConfig.builder(host, port)
                .localHostname("demo.example.com")
                .build();

        try (var client = new SmtpClient(config)) {
            client.connect();

            var extensions = client.extensions();
            LOG.info("Negotiated extensions: {}", extensions.keySet());

            for (var entry : extensions.entrySet()) {
                LOG.info("  {} = {}", entry.getKey(), entry.getValue());
            }

            int count = extensions.size();
            LOG.info("Total extensions: {}", count);
            return count;
        }
    }
}
