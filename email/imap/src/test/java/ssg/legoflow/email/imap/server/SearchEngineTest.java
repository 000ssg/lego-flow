package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.imap.protocol.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SearchEngineTest {
    private Mailbox createMailboxWithMessages() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c1 = "From: alice@example.com\r\nTo: bob@example.com\r\nSubject: Meeting\r\n\r\nBody 1".getBytes(StandardCharsets.UTF_8);
        byte[] c2 = "From: bob@example.com\r\nTo: alice@example.com\r\nSubject: Urgent Report\r\n\r\nImportant body text".getBytes(StandardCharsets.UTF_8);
        byte[] c3 = "From: list@example.com\r\nTo: all@example.com\r\nSubject: Newsletter\r\n\r\nWeekly update".getBytes(StandardCharsets.UTF_8);
        mb.append(c1, Set.of("\\Seen", "\\Flagged"), Instant.now());
        mb.append(c2, Set.of("\\Seen"), Instant.now());
        mb.append(c3, Set.of(), Instant.now());
        return mb;
    }

    @Test void testSearchAll() {
        assertThat(SearchEngine.search(createMailboxWithMessages(), SearchCriteria.all())).hasSize(3);
    }

    @Test void testSearchSeen() {
        assertThat(SearchEngine.search(createMailboxWithMessages(), SearchCriteria.seen())).hasSize(2);
    }

    @Test void testSearchUnseen() {
        assertThat(SearchEngine.search(createMailboxWithMessages(), SearchCriteria.unseen())).hasSize(1);
    }

    @Test void testSearchFlagged() {
        assertThat(SearchEngine.search(createMailboxWithMessages(), SearchCriteria.flagged())).hasSize(1);
    }

    @Test void testSearchSubject() {
        assertThat(SearchEngine.search(createMailboxWithMessages(), SearchCriteria.subject("Meeting"))).hasSize(1);
    }

    @Test void testSearchFrom() {
        assertThat(SearchEngine.search(createMailboxWithMessages(), SearchCriteria.from("alice@example.com"))).hasSize(1);
    }

    @Test void testSearchBodyText() {
        assertThat(SearchEngine.search(createMailboxWithMessages(), SearchCriteria.body("Important"))).hasSize(1);
    }

    @Test void testSearchLargerSmaller() {
        var mb = createMailboxWithMessages();
        long minSize = mb.allMessages().stream().mapToLong(StoredMessage::size).min().orElse(0);
        assertThat(SearchEngine.search(mb, SearchCriteria.larger(minSize))).hasSizeLessThan(3);
    }

    @Test void testSearchUidSet() {
        var mb = createMailboxWithMessages();
        long uid1 = mb.allMessages().get(0).uid();
        assertThat(SearchEngine.searchUids(mb, SearchCriteria.uid(String.valueOf(uid1)))).containsExactly(uid1);
    }

    @Test void testSearchAnd() {
        var mb = createMailboxWithMessages();
        assertThat(SearchEngine.search(mb, SearchCriteria.and(SearchCriteria.seen(), SearchCriteria.flagged()))).hasSize(1);
    }

    @Test void testSearchOr() {
        var mb = createMailboxWithMessages();
        assertThat(SearchEngine.search(mb, SearchCriteria.or(SearchCriteria.subject("Meeting"), SearchCriteria.subject("Newsletter")))).hasSize(2);
    }

    @Test void testSearchNot() {
        var mb = createMailboxWithMessages();
        assertThat(SearchEngine.search(mb, SearchCriteria.not(SearchCriteria.seen()))).hasSize(1);
    }

    @Test void testMatchesIndividualMessage() {
        byte[] c = "From: a@b.com\r\nSubject: Hello\r\n\r\nworld".getBytes(StandardCharsets.UTF_8);
        var msg = new StoredMessage(1L, Instant.now(), c, Set.of("\\Seen"));
        assertThat(SearchEngine.matches(msg, SearchCriteria.seen())).isTrue();
        assertThat(SearchEngine.matches(msg, SearchCriteria.subject("Hello"))).isTrue();
        assertThat(SearchEngine.matches(msg, SearchCriteria.subject("Goodbye"))).isFalse();
    }

    @Test void testSearchEmptyMailbox() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(SearchEngine.search(mb, SearchCriteria.all())).isEmpty();
    }

    @Test void testSearchHeaderMatch() {
        var mb = createMailboxWithMessages();
        assertThat(SearchEngine.search(mb, SearchCriteria.header("From", "bob"))).hasSize(1);
    }
}
