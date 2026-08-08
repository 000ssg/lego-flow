package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InMemoryMailStoreTest {
    @Test void testAddUserCreatesInbox() {
        var store = new InMemoryMailStore().addUser("alice", "secret");
        assertThat(store.authenticate("alice", "secret")).isTrue();
        assertThat(store.getMailbox("INBOX")).isNotNull();
    }

    @Test void testAuthenticateFailures() {
        var store = new InMemoryMailStore().addUser("bob", "pass");
        assertThat(store.authenticate("bob", "wrong")).isFalse();
        assertThat(store.authenticate("nobody", "x")).isFalse();
    }

    @Test void testCreateAndGetMailbox() {
        var store = new InMemoryMailStore();
        var mb = store.createMailbox("Drafts");
        assertThat(mb).isNotNull();
        assertThat(store.getMailbox("drafts")).isSameAs(mb);
        assertThat(store.getMailbox("Nonexistent")).isNull();
    }

    @Test void testCreateDuplicateThrows() {
        var store = new InMemoryMailStore();
        store.createMailbox("Archive");
        assertThatThrownBy(() -> store.createMailbox("archive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test void testDeleteMailbox() {
        var store = new InMemoryMailStore();
        store.createMailbox("Trash");
        assertThat(store.deleteMailbox("trash")).isTrue();
        assertThat(store.getMailbox("Trash")).isNull();
        assertThat(store.deleteMailbox("Nonexistent")).isFalse();
    }

    @Test void testDeleteInboxFails() {
        var store = new InMemoryMailStore().addUser("u", "p");
        assertThat(store.deleteMailbox("INBOX")).isFalse();
    }

    @Test void testRenameMailbox() {
        var store = new InMemoryMailStore();
        store.createMailbox("Old");
        assertThat(store.renameMailbox("old", "New")).isTrue();
        assertThat(store.getMailbox("Old")).isNull();
        assertThat(store.getMailbox("NEW")).isNotNull();
    }

    @Test void testRenameNonexistentFails() {
        var store = new InMemoryMailStore();
        assertThat(store.renameMailbox("Ghost", "X")).isFalse();
    }

    @Test void testListMailboxes() {
        var store = new InMemoryMailStore();
        store.createMailbox("A");
        store.createMailbox("B/C");
        var list = store.listMailboxes("", "*");
        assertThat(list).contains("A", "B/C");
    }

    @Test void testListMailboxesWildcard() {
        var store = new InMemoryMailStore();
        store.createMailbox("Work/Projects");
        store.createMailbox("Personal");
        var work = store.listMailboxes("", "Work/*");
        assertThat(work).contains("Work/Projects").doesNotContain("Personal");
    }

    @Test void testDelimiter() {
        var store = new InMemoryMailStore();
        assertThat(store.delimiter()).isEqualTo("/");
    }

    @Test void testMailboxNames() {
        var store = new InMemoryMailStore();
        store.createMailbox("A");
        store.createMailbox("B");
        var names = store.mailboxNames();
        assertThat(names).contains("A", "B");
    }

    @Test void testAddUserChaining() {
        var store = new InMemoryMailStore()
                .addUser("alice", "pass1")
                .addUser("bob", "pass2");
        assertThat(store.authenticate("alice", "pass1")).isTrue();
        assertThat(store.authenticate("bob", "pass2")).isTrue();
    }
}
