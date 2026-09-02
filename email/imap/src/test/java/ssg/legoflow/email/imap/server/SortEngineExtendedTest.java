package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.email.imap.protocol.SortCriteria;
import ssg.legoflow.email.imap.protocol.SortCriteria.SortKey;
import ssg.legoflow.email.imap.protocol.SortCriteria.ThreadAlgorithm;
import ssg.legoflow.email.imap.protocol.SearchCriteria;
/**
 * Extended SortEngine tests covering reverse sorting, all sort keys, 
 * threading algorithms, parse methods, and edge cases.
 */
class SortEngineExtendedTest {

    private Mailbox createMailboxWithMessages() {
        var mb = new Mailbox("INBOX", 1L);
        Instant early = Instant.parse("2024-01-01T10:00:00Z");
        Instant late = Instant.parse("2024-06-01T10:00:00Z");
        
        byte[] c1 = ("From: alice@example.com\r\nTo: bob@test.com\r\nCC: cc-user@test.com\r\n" +
                "Subject: Re: Meeting\r\nDate: Mon, 1 Jan 2024 10:00:00 +0000\r\n\r\nFirst msg").getBytes(StandardCharsets.UTF_8);
        byte[] c2 = ("From: bob@example.com\r\nTo: carol@test.com\r\nCC: another@test.com\r\n" + 
                "Subject: ZZZ Topic\r\nDate: Mon, 1 Jun 2024 10:00:00 +0000\r\n\r\nSecond msg").getBytes(StandardCharsets.UTF_8);
        byte[] c3 = ("From: carol@example.com\r\nTo: dave@test.com\r\nCC: third@test.com\r\n" +
                "Subject: AAA Topic\r\nDate: Mon, 1 Jan 2024 10:00:00 +0000\r\n\r\nThird msg").getBytes(StandardCharsets.UTF_8);
        
        mb.append(c1, Set.of("\\Seen"), early);
        mb.append(c2, Set.of(), late);
        mb.append(c3, Set.of(), early);
        return mb;
    }

    @Test void testSortByTo() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.TO, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortByCc() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.CC, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortReverseBySubject() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SUBJECT, true)); // reverse=true
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortReverseByFrom() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.FROM, true)); // reverse=true
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortReverseByArrival() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.ARRIVAL, true)); // reverse=true
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortReverseByDate() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.DATE, true)); // reverse=true
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortReverseBySize() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SIZE, true)); // reverse=true
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortReverseMixed() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(
            new SortCriteria(SortKey.DATE, true),   // reverse date
            new SortCriteria(SortKey.SUBJECT, false)); // normal subject
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortEmptyMailbox() {
        var mb = new Mailbox("INBOX", 1L);
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SUBJECT, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).isEmpty();
    }

    @Test void testSortWithNullSearchCriteria() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SUBJECT, false));
        var result = SortEngine.sort(mb, crits, null); // null = match all
        assertThat(result).hasSize(3);
    }

    @Test void testThreadByOrderedSubject() {
        var mb = createMailboxWithMessages();
        var result = SortEngine.thread(mb, ThreadAlgorithm.ORDEREDSUBJECT, SearchCriteria.all());
        assertThat(result).isNotEmpty();
    }

    @Test void testThreadByReferences() {
        var mb = createMailboxWithMessages();
        var result = SortEngine.thread(mb, ThreadAlgorithm.REFERENCES, SearchCriteria.all());
        assertThat(result).isNotEmpty();
    }

    @Test void testThreadWithNullSearchCriteria() {
        var mb = createMailboxWithMessages();
        var result = SortEngine.thread(mb, ThreadAlgorithm.ORDEREDSUBJECT, null);
        assertThat(result).isNotEmpty();
    }

    @Test void testThreadEmptyMailbox() {
        var mb = new Mailbox("INBOX", 1L);
        var result = SortEngine.thread(mb, ThreadAlgorithm.ORDEREDSUBJECT, SearchCriteria.all());
        assertThat(result).isEmpty();
    }

    @Test void testSortKeyParseAscending() {
        assertThat(SortCriteria.ascending(SortKey.SUBJECT).key()).isEqualTo(SortKey.SUBJECT);
        assertThat(SortCriteria.ascending(SortKey.SUBJECT).reverse()).isFalse();
    }

    @Test void testSortKeyParseDescending() {
        assertThat(SortCriteria.descending(SortKey.SUBJECT).key()).isEqualTo(SortKey.SUBJECT);
        assertThat(SortCriteria.descending(SortKey.SUBJECT).reverse()).isTrue();
    }

    @Test void testSortKeyParseFromText() {
        assertThat(SortKey.parse("SUBJECT")).isEqualTo(SortKey.SUBJECT);
        assertThat(SortKey.parse("subject")).isEqualTo(SortKey.SUBJECT);
        assertThat(SortKey.parse("FROM")).isEqualTo(SortKey.FROM);
        assertThat(SortKey.parse("ARRIVAL")).isEqualTo(SortKey.ARRIVAL);
        assertThat(SortKey.parse("SIZE")).isEqualTo(SortKey.SIZE);
        assertThat(SortKey.parse("DATE")).isEqualTo(SortKey.DATE);
        assertThat(SortKey.parse("TO")).isEqualTo(SortKey.TO);
        assertThat(SortKey.parse("CC")).isEqualTo(SortKey.CC);
    }

    @Test void testThreadAlgorithmParseFromText() {
        assertThat(ThreadAlgorithm.parse("ORDEREDSUBJECT")).isEqualTo(ThreadAlgorithm.ORDEREDSUBJECT);
        assertThat(ThreadAlgorithm.parse("REFERENCES")).isEqualTo(ThreadAlgorithm.REFERENCES);
        assertThat(ThreadAlgorithm.parse("orderedsubject")).isEqualTo(ThreadAlgorithm.ORDEREDSUBJECT);
    }

    @Test void testSortBySubjectReverseOrdering() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SUBJECT, false));
        var ascending = SortEngine.sort(mb, crits, SearchCriteria.all());

        crits = List.of(new SortCriteria(SortKey.SUBJECT, true));
        var descending = SortEngine.sort(mb, crits, SearchCriteria.all());
        
        // Ascending and descending should be different
        assertThat(ascending).isNotEqualTo(descending);
    }

    @Test void testSortByArrivalReverseOrdering() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.ARRIVAL, false));
        var ascending = SortEngine.sort(mb, crits, SearchCriteria.all());

        crits = List.of(new SortCriteria(SortKey.ARRIVAL, true));
        var descending = SortEngine.sort(mb, crits, SearchCriteria.all());
        
        assertThat(ascending).isNotEqualTo(descending);
    }

    @Test void testThreadWithSearchFilter() {
        var mb = createMailboxWithMessages();
        var result = SortEngine.thread(mb, ThreadAlgorithm.ORDEREDSUBJECT, SearchCriteria.seen());
        assertThat(result).isNotEmpty();
    }

    @Test void testSortAllSevenKeysWork() {
        var mb = createMailboxWithMessages();
        
        for (SortKey key : SortKey.values()) {
            List<SortCriteria> crits = List.of(new SortCriteria(key, false));
            var result = SortEngine.sort(mb, crits, SearchCriteria.all());
            assertThat(result).as("Sort by %s should work", key.name()).hasSize(3);
        }
    }

}
