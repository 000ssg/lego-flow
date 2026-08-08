package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

// Import SortCriteria.SortKey and ThreadAlgorithm as inner types
import ssg.legoflow.email.imap.protocol.SortCriteria;
import ssg.legoflow.email.imap.protocol.SortCriteria.SortKey;
import ssg.legoflow.email.imap.protocol.SortCriteria.ThreadAlgorithm;
import ssg.legoflow.email.imap.protocol.SearchCriteria;

class SortEngineTest {
    private Mailbox createMailboxWithMessages() {
        var mb = new Mailbox("INBOX", 1L);
        byte[] c1 = "From: alice@example.com\r\nSubject: AAA\r\n\r\nFirst".getBytes(StandardCharsets.UTF_8);
        byte[] c2 = "From: bob@example.com\r\nSubject: ZZZ\r\n\r\nSecond".getBytes(StandardCharsets.UTF_8);
        byte[] c3 = "From: carol@example.com\r\nSubject: MMM\r\n\r\nThird".getBytes(StandardCharsets.UTF_8);
        mb.append(c1, Set.of("\\Seen"), Instant.now());
        mb.append(c2, Set.of(), Instant.now());
        mb.append(c3, Set.of("\\Seen"), Instant.now());
        return mb;
    }

    @Test void testSortBySubject() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SUBJECT, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortByDate() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.DATE, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortByFrom() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.FROM, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortByArrival() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.ARRIVAL, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortBySize() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SIZE, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortMultipleKeys() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(
            new SortCriteria(SortKey.DATE, false),
            new SortCriteria(SortKey.SUBJECT, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.all());
        assertThat(result).hasSize(3);
    }

    @Test void testSortWithSearchFilter() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SUBJECT, false));
        var result = SortEngine.sort(mb, crits, SearchCriteria.seen());
        assertThat(result).hasSize(2);
    }

    @Test void testSortWithNullSearch() {
        var mb = createMailboxWithMessages();
        List<SortCriteria> crits = List.of(new SortCriteria(SortKey.SUBJECT, false));
        var result = SortEngine.sort(mb, crits, null);
        assertThat(result).hasSize(3);
    }

    @Test void testThread() {
        var mb = createMailboxWithMessages();
        List<Long> allUids = mb.allMessages().stream().mapToLong(StoredMessage::uid).boxed().toList();
        var result = SortEngine.thread(mb, ThreadAlgorithm.ORDEREDSUBJECT, SearchCriteria.all());
        assertThat(result).isNotEmpty();
    }

    @Test void testSortKeyValues() {
        assertThat(SortKey.ARRIVAL.name()).isEqualTo("ARRIVAL");
        assertThat(SortKey.SUBJECT.name()).isEqualTo("SUBJECT");
        assertThat(SortKey.SIZE.name()).isEqualTo("SIZE");
    }

    @Test void testThreadAlgorithmValues() {
        assertThat(ThreadAlgorithm.ORDEREDSUBJECT.name()).isEqualTo("ORDEREDSUBJECT");
        assertThat(ThreadAlgorithm.REFERENCES.name()).isEqualTo("REFERENCES");
    }
}
