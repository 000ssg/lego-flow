package ssg.legoflow.email.imap.protocol;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class SearchCriteriaTest {

    // --- Leaf criteria ---

    @Test
    void testAll() {
        assertThat(SearchCriteria.all().toWire()).isEqualTo("ALL");
        assertThat(new SearchCriteria.All().toWire()).isEqualTo("ALL");
    }

    @Test
    void testFlagged() {
        assertThat(new SearchCriteria.Flagged("SEEN").toWire()).isEqualTo("SEEN");
        assertThat(new SearchCriteria.Flagged("seen").toWire()).isEqualTo("SEEN");
        assertThat(SearchCriteria.seen().toWire()).isEqualTo("SEEN");
        assertThat(SearchCriteria.unseen().toWire()).isEqualTo("UNSEEN");
        assertThat(SearchCriteria.flagged().toWire()).isEqualTo("FLAGGED");
        assertThat(SearchCriteria.unflagged().toWire()).isEqualTo("UNFLAGGED");
        assertThat(SearchCriteria.deleted().toWire()).isEqualTo("DELETED");
        assertThat(SearchCriteria.undeleted().toWire()).isEqualTo("UNDELETED");
        assertThat(SearchCriteria.draft().toWire()).isEqualTo("DRAFT");
        assertThat(SearchCriteria.undraft().toWire()).isEqualTo("UNDRAFT");
        assertThat(SearchCriteria.newMessages().toWire()).isEqualTo("NEW");
        assertThat(SearchCriteria.old().toWire()).isEqualTo("OLD");
        assertThat(SearchCriteria.recent().toWire()).isEqualTo("RECENT");
        assertThat(SearchCriteria.answered().toWire()).isEqualTo("ANSWERED");
        assertThat(SearchCriteria.unanswered().toWire()).isEqualTo("UNANSWERED");
    }

    @Test
    void testFlaggedNullThrows() {
        assertThatThrownBy(() -> new SearchCriteria.Flagged(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHeader() {
        var h = new SearchCriteria.Header("From", "user@example.com");
        assertThat(h.toWire()).isEqualTo("HEADER From \"user@example.com\"");
        assertThat(SearchCriteria.header("X-Custom", "val").toWire())
                .isEqualTo("HEADER X-Custom \"val\"");
    }

    @Test
    void testHeaderNullFieldThrows() {
        assertThatThrownBy(() -> new SearchCriteria.Header(null, "v"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAddressField() {
        var af = new SearchCriteria.AddressField("FROM", "user@example.com");
        assertThat(af.toWire()).isEqualTo("FROM \"user@example.com\"");
        assertThat(SearchCriteria.from("a@b.com").toWire()).isEqualTo("FROM \"a@b.com\"");
        assertThat(SearchCriteria.to("c@d.com").toWire()).isEqualTo("TO \"c@d.com\"");
        assertThat(SearchCriteria.cc("e@f.com").toWire()).isEqualTo("CC \"e@f.com\"");
        assertThat(SearchCriteria.bcc("g@h.com").toWire()).isEqualTo("BCC \"g@h.com\"");
    }

    @Test
    void testBodyAndText() {
        assertThat(SearchCriteria.body("hello world").toWire())
                .isEqualTo("BODY \"hello world\"");
        assertThat(SearchCriteria.text("urgent").toWire())
                .isEqualTo("TEXT \"urgent\"");
        assertThat(new SearchCriteria.Body("test").toWire()).isEqualTo("BODY \"test\"");
        assertThat(new SearchCriteria.Text("test").toWire()).isEqualTo("TEXT \"test\"");
    }

    @Test
    void testSubject() {
        assertThat(SearchCriteria.subject("meeting").toWire())
                .isEqualTo("SUBJECT \"meeting\"");
    }

    @Test
    void testDateCriteria() {
        LocalDate date = LocalDate.of(2024, 3, 15);
        assertThat(SearchCriteria.before(date).toWire()).isEqualTo("BEFORE 15-Mar-2024");
        assertThat(SearchCriteria.on(date).toWire()).isEqualTo("ON 15-Mar-2024");
        assertThat(SearchCriteria.since(date).toWire()).isEqualTo("SINCE 15-Mar-2024");
        assertThat(SearchCriteria.sentBefore(date).toWire()).isEqualTo("SENTBEFORE 15-Mar-2024");
        assertThat(SearchCriteria.sentOn(date).toWire()).isEqualTo("SENTON 15-Mar-2024");
        assertThat(SearchCriteria.sentSince(date).toWire()).isEqualTo("SENTSINCE 15-Mar-2024");
    }

    @Test
    void testSizeCriteria() {
        assertThat(SearchCriteria.larger(1024).toWire()).isEqualTo("LARGER 1024");
        assertThat(SearchCriteria.smaller(500).toWire()).isEqualTo("SMALLER 500");
        assertThat(new SearchCriteria.Larger(0).toWire()).isEqualTo("LARGER 0");
    }

    @Test
    void testKeywordCriteria() {
        assertThat(SearchCriteria.keyword("IMPORTANT").toWire())
                .isEqualTo("KEYWORD IMPORTANT");
        assertThat(SearchCriteria.unkeyword("SPAM").toWire())
                .isEqualTo("UNKEYWORD SPAM");
    }

    @Test
    void testUidAndSequenceSet() {
        assertThat(SearchCriteria.uid("1:100").toWire()).isEqualTo("UID 1:100");
        assertThat(SearchCriteria.sequenceSet("*").toWire()).isEqualTo("*");
    }

    @Test
    void testModSeq() {
        assertThat(SearchCriteria.modSeq(42).toWire()).isEqualTo("MODSEQ 42");
    }

    // --- Compound criteria ---

    @Test
    void testAndCompound() {
        var and = SearchCriteria.and(
                SearchCriteria.seen(),
                SearchCriteria.subject("urgent"),
                SearchCriteria.from("boss@example.com")
        );
        assertThat(and.toWire())
                .isEqualTo("SEEN SUBJECT \"urgent\" FROM \"boss@example.com\"");
    }

    @Test
    void testOrCompound() {
        var or = SearchCriteria.or(
                SearchCriteria.flagged(),
                SearchCriteria.subject("important")
        );
        assertThat(or.toWire())
                .isEqualTo("OR FLAGGED SUBJECT \"important\"");
    }

    @Test
    void testNotCompound() {
        var not = SearchCriteria.not(SearchCriteria.deleted());
        assertThat(not.toWire()).isEqualTo("NOT DELETED");
    }

    @Test
    void testComplexNestedExpression() {
        var complex = SearchCriteria.and(
                SearchCriteria.or(
                        SearchCriteria.seen(),
                        SearchCriteria.flagged()
                ),
                SearchCriteria.not(SearchCriteria.deleted()),
                SearchCriteria.since(LocalDate.of(2024, 1, 1))
        );
        assertThat(complex.toWire())
                .isEqualTo("OR SEEN FLAGGED NOT DELETED SINCE 1-Jan-2024");
    }

    @Test
    void testAndNullThrows() {
        var criteria = new SearchCriteria[]{SearchCriteria.all(), null};
        assertThatThrownBy(() -> new SearchCriteria.And(java.util.List.of(criteria)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testOrNullLeftThrows() {
        assertThatThrownBy(() -> new SearchCriteria.Or(null, SearchCriteria.all()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testOrNullRightThrows() {
        assertThatThrownBy(() -> new SearchCriteria.Or(SearchCriteria.all(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNotNullThrows() {
        assertThatThrownBy(() -> new SearchCriteria.Not(null))
                .isInstanceOf(NullPointerException.class);
    }
}
