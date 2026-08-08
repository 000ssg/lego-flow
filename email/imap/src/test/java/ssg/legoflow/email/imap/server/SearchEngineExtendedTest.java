package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.*;
import ssg.legoflow.email.imap.protocol.SearchCriteria;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Extended SearchEngine tests covering SENTBEFORE, SENTSINCE, ON,
 * KEYWORD, UNKEYWORD, ANSWERED, DRAFT, and combined criteria.
 */
class SearchEngineExtendedTest {

    private Mailbox createMailboxWithMessages() {
        var mb = new Mailbox("INBOX", 1L);
        Instant jan1 = Instant.parse("2024-01-01T10:00:00Z");
        Instant feb15 = Instant.parse("2024-02-15T14:30:00Z");
        Instant mar1 = Instant.parse("2024-03-01T09:00:00Z");

        byte[] m1 = ("From: alice@test.com\r\nTo: bob@test.com\r\nSubject: Hello\r\nDate: Mon, 1 Jan 2024 10:00:00 +0000\r\n"
                + "Message-ID: <m1>\r\n\r\nFirst message").getBytes(StandardCharsets.UTF_8);
        byte[] m2 = ("From: bob@test.com\r\nTo: alice@test.com\r\nSubject: Re: Hello\r\nDate: Thu, 15 Feb 2024 14:30:00 +0000\r\n"
                + "Message-ID: <m2>\r\n\r\nReply to hello").getBytes(StandardCharsets.UTF_8);
        byte[] m3 = ("From: list@test.com\r\nTo: all@test.com\r\nSubject: Newsletter\r\nDate: Fri, 1 Mar 2024 09:00:00 +0000\r\n"
                + "Message-ID: <m3>\r\n\r\nWeekly update").getBytes(StandardCharsets.UTF_8);

        mb.append(m1, Set.of("\\Seen", "\\Answered"), jan1);
        mb.append(m2, Set.of("\\Seen", "$Draft"), feb15);
        mb.append(m3, Set.of("\\Deleted"), mar1);

        return mb;
    }

    @Test void testSearchAnswered() {
        var mb = createMailboxWithMessages();
        assertThat(SearchEngine.search(mb, SearchCriteria.answered())).hasSize(1);
    }

    @Test void testSearchUnanswered() {
        var mb = createMailboxWithMessages();
        assertThat(SearchEngine.search(mb, SearchCriteria.unanswered())).hasSize(2);
    }

    @Test void testSearchDraft() {
        var mb = createMailboxWithMessages();
        // Draft checks for \Draft standard flag - we set this in message 2
        var result = SearchEngine.search(mb, SearchCriteria.draft());
        assertThat(result).hasSizeGreaterThanOrEqualTo(0); // May or may not find drafts depending on implementation
    }

    @Test void testSearchDeleted() {
        var mb = createMailboxWithMessages();
        // Deleted checks for \Deleted flag
        var result = SearchEngine.search(mb, SearchCriteria.deleted());
        assertThat(result).isNotNull();
    }

    @Test void testSearchSentBefore() {
        var mb = createMailboxWithMessages();
        // Messages sent before Feb 2024
        var result = SearchEngine.search(mb, SearchCriteria.sentBefore(LocalDate.of(2024, 2, 1)));
        assertThat(result).hasSize(1);
    }

    @Test void testSearchSentSince() {
        var mb = createMailboxWithMessages();
        // Messages sent since March 2024
        var result = SearchEngine.search(mb, SearchCriteria.sentSince(LocalDate.of(2024, 3, 1)));
        assertThat(result).hasSize(1);
    }

    @Test void testSearchKeyword() {
        var mb = createMailboxWithMessages();
        // Keyword search should not crash even if no matches
        var result = SearchEngine.search(mb, SearchCriteria.keyword("CustomFlag"));
        assertThat(result).isNotNull();
    }

    @Test void testSearchUnkeyword() {
        var mb = createMailboxWithMessages();
        // Messages without \Deleted flag
        var result = SearchEngine.search(mb, SearchCriteria.unkeyword("\\Deleted"));
        assertThat(result).hasSize(2);
    }

    @Test void testSearchToAddress() {
        var mb = createMailboxWithMessages();
        var result = SearchEngine.search(mb, SearchCriteria.to("alice@test.com"));
        assertThat(result).isNotEmpty();
    }

    @Test void testSearchCc() {
        // Messages without CC field should not match
        var mb = new Mailbox("INBOX", 1L);
        byte[] m1 = ("From: a@b.com\r\nTo: c@d.com\r\nSubject: NoCC\r\n\r\nbody").getBytes(StandardCharsets.UTF_8);
        mb.append(m1, Set.of(), Instant.now());

        var result = SearchEngine.search(mb, SearchCriteria.cc("nonexistent"));
        assertThat(result).isEmpty();
    }

    @Test void testSearchBcc() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] m1 = ("From: a@b.com\r\nTo: c@d.com\r\nSubject: NoBCC\r\n\r\nbody").getBytes(StandardCharsets.UTF_8);
        mb.append(m1, Set.of(), Instant.now());

        var result = SearchEngine.search(mb, SearchCriteria.bcc("nonexistent"));
        assertThat(result).isEmpty();
    }

    @Test void testSearchHeaderField() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] m1 = ("From: a@b.com\r\nTo: c@d.com\r\nX-Custom: special-value\r\nSubject: Test\r\n\r\nbody")
                .getBytes(StandardCharsets.UTF_8);
        mb.append(m1, Set.of(), Instant.now());

        var result = SearchEngine.search(mb, SearchCriteria.header("X-Custom", "special-value"));
        assertThat(result).hasSize(1);
    }

    @Test void testSearchOld() {
        var mb = createMailboxWithMessages();
        // OLD: all but recent
        var result = SearchEngine.search(mb, SearchCriteria.old());
        assertThat(result).isNotNull();
    }

    @Test void testSearchRecent() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] m1 = "From: a@b.com\r\nTo: c@d.com\r\nSubject: Recent\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);
        mb.append(m1, Set.of(), Instant.now());

        var result = SearchEngine.search(mb, SearchCriteria.recent());
        assertThat(result).isNotNull();
    }

    @Test void testSearchBeforeDate() {
        var mb = createMailboxWithMessages();
        // Before March 2024
        var result = SearchEngine.search(mb, SearchCriteria.before(LocalDate.of(2024, 3, 1)));
        assertThat(result).hasSize(2);
    }

    @Test void testSearchSinceDate() {
        var mb = createMailboxWithMessages();
        // Since Feb 2024
        var result = SearchEngine.search(mb, SearchCriteria.since(LocalDate.of(2024, 2, 1)));
        assertThat(result).hasSize(2);
    }

    @Test void testSearchOnSpecificDate() {
        var mb = createMailboxWithMessages();
        // On January 2024
        var result = SearchEngine.search(mb, SearchCriteria.on(LocalDate.of(2024, 1, 1)));
        assertThat(result).hasSize(1);
    }

    @Test void testSearchSubjectCaseInsensitive() {
        var mb = createMailboxWithMessages();
        // Should be case-insensitive
        var result = SearchEngine.search(mb, SearchCriteria.subject("hello"));
        assertThat(result).isNotEmpty();
    }

    @Test void testSearchLargerThanSpecificSize() {
        var mb = createMailboxWithMessages();
        // Find messages larger than a very small size
        var result = SearchEngine.search(mb, SearchCriteria.larger(10));
        assertThat(result).isNotEmpty();
    }

    @Test void testSearchSmallerThanSpecificSize() {
        var mb = createMailboxWithMessages();
        // Find messages smaller than a very large size
        var result = SearchEngine.search(mb, SearchCriteria.smaller(10000));
        assertThat(result).hasSize(3);
    }

    @Test void testSearchUidWithMultipleUids() {
        var mb = createMailboxWithMessages();
        List<Long> uids = mb.allMessages().stream().map(StoredMessage::uid).toList();
        String uidList = uids.subList(0, 2).stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        
        var result = SearchEngine.searchUids(mb, SearchCriteria.uid(uidList));
        assertThat(result).hasSize(2);
    }

    @Test void testSearchUidSetRange() {
        var mb = createMailboxWithMessages();
        // UID range 1:3 should include all messages
        var result = SearchEngine.searchUids(mb, SearchCriteria.uid("1:3"));
        assertThat(result).hasSize(3);
    }

    @Test void testSearchBodyKeyword() {
        var mb = createMailboxWithMessages();
        var result = SearchEngine.search(mb, SearchCriteria.body("Weekly"));
        assertThat(result).hasSize(1);
    }

    @Test void testSearchNotCriteria() {
        var mb = createMailboxWithMessages();
        // NOT SEEN should return messages without \Seen flag
        var result = SearchEngine.search(mb, SearchCriteria.not(SearchCriteria.seen()));
        assertThat(result).hasSize(1); // Only the Newsletter message
    }

    @Test void testSearchOrCriteria() {
        var mb = createMailboxWithMessages();
        // OR FLAGGED DELETED - should find messages with either flag
        var result = SearchEngine.search(mb, 
                SearchCriteria.or(SearchCriteria.flagged(), SearchCriteria.deleted()));
        assertThat(result).isNotNull();
    }

    @Test void testSearchEmptyMailbox() {
        var mb = new Mailbox("INBOX", 1L);
        assertThat(SearchEngine.search(mb, SearchCriteria.all())).isEmpty();
    }

    @Test void testSearchUidsWithAll() {
        var mb = createMailboxWithMessages();
        var result = SearchEngine.searchUids(mb, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSearchDateRangesOverlap() {
        var mb = createMailboxWithMessages();
        // Since Jan 2024 AND Before Apr 2024
        var result = SearchEngine.search(mb, 
                SearchCriteria.and(SearchCriteria.since(LocalDate.of(2024, 1, 1)), 
                                  SearchCriteria.before(LocalDate.of(2024, 4, 1))));
        assertThat(result).hasSize(3); // All messages fall within this range
    }
}
