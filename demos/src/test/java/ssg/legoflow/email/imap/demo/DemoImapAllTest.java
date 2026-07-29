package ssg.legoflow.email.imap.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive IMAP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code ImapServer}. To test against
 * an external Dovecot, Gmail IMAP, or Microsoft Exchange, set
 * {@code DemoImapAll.USE_EXTERNAL = true} and configure host/port
 * before running.</p>
 */
class DemoImapAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoImapAll.runAll();

        assertThat(results.loginCapability())
                .as("Login and capability retrieval")
                .isTrue();

        assertThat(results.mailboxManagement())
                .as("Mailbox management (create/rename/delete/list)")
                .isGreaterThanOrEqualTo(3);

        assertThat(results.selectFetch())
                .as("Select INBOX and fetch messages")
                .isEqualTo(4);

        assertThat(results.searchResults())
                .as("Search for unseen messages")
                .isGreaterThanOrEqualTo(1);

        assertThat(results.storeFlags())
                .as("Store flag operations")
                .isTrue();

        assertThat(results.copyMessages())
                .as("Copy messages between mailboxes")
                .isTrue();

        assertThat(results.expungeCount())
                .as("Expunge deleted messages")
                .isEqualTo(1);

        assertThat(results.namespaceResult())
                .as("NAMESPACE query returns response")
                .isTrue();

        assertThat(results.appendedMessages())
                .as("Messages seeded via append")
                .isEqualTo(4);
    }
}
