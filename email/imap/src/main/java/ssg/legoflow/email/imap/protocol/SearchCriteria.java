package ssg.legoflow.email.imap.protocol;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * IMAP SEARCH criteria tree supporting AND, OR, NOT, and leaf criteria.
 *
 * <p>Criteria can be combined into complex expressions:
 * <ul>
 *   <li>AND is implicit (juxtaposed criteria)</li>
 *   <li>OR combines two criteria with a disjunction</li>
 *   <li>NOT negates a single criterion</li>
 * </ul>
 *
 * <p>Leaf criteria match flags, headers, dates, sizes, and message content.
 *
 * @since 1.0.0
 */
public sealed interface SearchCriteria {

    /**
     * Formats this criteria for the IMAP wire protocol.
     *
     * @return the formatted string
     */
    String toWire();

    // --- Leaf criteria ---

    /** Matches all messages. */
    record All() implements SearchCriteria {
        @Override public String toWire() { return "ALL"; }
    }

    /** Matches messages with the given flag set. */
    record Flagged(String flag) implements SearchCriteria {
        public Flagged { Objects.requireNonNull(flag); }
        @Override public String toWire() { return flag.toUpperCase(); }
    }

    /** Matches messages with a header field containing the value. */
    record Header(String field, String value) implements SearchCriteria {
        public Header { Objects.requireNonNull(field); Objects.requireNonNull(value); }
        @Override public String toWire() { return "HEADER " + field + " \"" + value + "\""; }
    }

    /** Matches messages with a specific address header containing the value. */
    record AddressField(String field, String value) implements SearchCriteria {
        public AddressField { Objects.requireNonNull(field); Objects.requireNonNull(value); }
        @Override public String toWire() { return field.toUpperCase() + " \"" + value + "\""; }
    }

    /** Matches messages with body text containing the value. */
    record Body(String text) implements SearchCriteria {
        public Body { Objects.requireNonNull(text); }
        @Override public String toWire() { return "BODY \"" + text + "\""; }
    }

    /** Matches messages with body or headers containing the value. */
    record Text(String text) implements SearchCriteria {
        public Text { Objects.requireNonNull(text); }
        @Override public String toWire() { return "TEXT \"" + text + "\""; }
    }

    /** Matches messages with subject containing the value. */
    record Subject(String value) implements SearchCriteria {
        public Subject { Objects.requireNonNull(value); }
        @Override public String toWire() { return "SUBJECT \"" + value + "\""; }
    }

    /** Matches messages with internal date before the given date. */
    record Before(LocalDate date) implements SearchCriteria {
        public Before { Objects.requireNonNull(date); }
        @Override public String toWire() { return "BEFORE " + formatDate(date); }
    }

    /** Matches messages with internal date on the given date. */
    record On(LocalDate date) implements SearchCriteria {
        public On { Objects.requireNonNull(date); }
        @Override public String toWire() { return "ON " + formatDate(date); }
    }

    /** Matches messages with internal date on or after the given date. */
    record Since(LocalDate date) implements SearchCriteria {
        public Since { Objects.requireNonNull(date); }
        @Override public String toWire() { return "SINCE " + formatDate(date); }
    }

    /** Matches messages with Date header before the given date. */
    record SentBefore(LocalDate date) implements SearchCriteria {
        public SentBefore { Objects.requireNonNull(date); }
        @Override public String toWire() { return "SENTBEFORE " + formatDate(date); }
    }

    /** Matches messages with Date header on the given date. */
    record SentOn(LocalDate date) implements SearchCriteria {
        public SentOn { Objects.requireNonNull(date); }
        @Override public String toWire() { return "SENTON " + formatDate(date); }
    }

    /** Matches messages with Date header on or after the given date. */
    record SentSince(LocalDate date) implements SearchCriteria {
        public SentSince { Objects.requireNonNull(date); }
        @Override public String toWire() { return "SENTSINCE " + formatDate(date); }
    }

    /** Matches messages larger than the given size in octets. */
    record Larger(long size) implements SearchCriteria {
        @Override public String toWire() { return "LARGER " + size; }
    }

    /** Matches messages smaller than the given size in octets. */
    record Smaller(long size) implements SearchCriteria {
        @Override public String toWire() { return "SMALLER " + size; }
    }

    /** Matches messages with the given keyword set. */
    record Keyword(String keyword) implements SearchCriteria {
        public Keyword { Objects.requireNonNull(keyword); }
        @Override public String toWire() { return "KEYWORD " + keyword; }
    }

    /** Matches messages without the given keyword. */
    record Unkeyword(String keyword) implements SearchCriteria {
        public Unkeyword { Objects.requireNonNull(keyword); }
        @Override public String toWire() { return "UNKEYWORD " + keyword; }
    }

    /** Matches messages with the given UID. */
    record Uid(String uidSet) implements SearchCriteria {
        public Uid { Objects.requireNonNull(uidSet); }
        @Override public String toWire() { return "UID " + uidSet; }
    }

    /** Matches messages with the given sequence number set. */
    record SequenceSet(String sequenceSet) implements SearchCriteria {
        public SequenceSet { Objects.requireNonNull(sequenceSet); }
        @Override public String toWire() { return sequenceSet; }
    }

    /** Matches messages with modification sequence >= the given value (CONDSTORE). */
    record ModSeq(long modSeq) implements SearchCriteria {
        @Override public String toWire() { return "MODSEQ " + modSeq; }
    }

    // --- Composite criteria ---

    /** Logical AND of multiple criteria (implicit in IMAP). */
    record And(List<SearchCriteria> criteria) implements SearchCriteria {
        public And { criteria = List.copyOf(criteria); }
        @Override public String toWire() {
            return String.join(" ", criteria.stream().map(SearchCriteria::toWire).toList());
        }
    }

    /** Logical OR of two criteria. */
    record Or(SearchCriteria left, SearchCriteria right) implements SearchCriteria {
        public Or { Objects.requireNonNull(left); Objects.requireNonNull(right); }
        @Override public String toWire() {
            return "OR " + left.toWire() + " " + right.toWire();
        }
    }

    /** Logical NOT of a criterion. */
    record Not(SearchCriteria criterion) implements SearchCriteria {
        public Not { Objects.requireNonNull(criterion); }
        @Override public String toWire() {
            return "NOT " + criterion.toWire();
        }
    }

    // --- Factory methods ---

    /** All messages. */
    static SearchCriteria all() { return new All(); }

    /** Messages with \Seen flag. */
    static SearchCriteria seen() { return new Flagged("SEEN"); }
    /** Messages without \Seen flag. */
    static SearchCriteria unseen() { return new Flagged("UNSEEN"); }
    /** Messages with \Answered flag. */
    static SearchCriteria answered() { return new Flagged("ANSWERED"); }
    /** Messages without \Answered flag. */
    static SearchCriteria unanswered() { return new Flagged("UNANSWERED"); }
    /** Messages with \Flagged flag. */
    static SearchCriteria flagged() { return new Flagged("FLAGGED"); }
    /** Messages without \Flagged flag. */
    static SearchCriteria unflagged() { return new Flagged("UNFLAGGED"); }
    /** Messages with \Deleted flag. */
    static SearchCriteria deleted() { return new Flagged("DELETED"); }
    /** Messages without \Deleted flag. */
    static SearchCriteria undeleted() { return new Flagged("UNDELETED"); }
    /** Messages with \Draft flag. */
    static SearchCriteria draft() { return new Flagged("DRAFT"); }
    /** Messages without \Draft flag. */
    static SearchCriteria undraft() { return new Flagged("UNDRAFT"); }
    /** New messages (\Recent and not \Seen). */
    static SearchCriteria newMessages() { return new Flagged("NEW"); }
    /** Old messages (not \Recent). */
    static SearchCriteria old() { return new Flagged("OLD"); }
    /** Recent messages (\Recent flag). */
    static SearchCriteria recent() { return new Flagged("RECENT"); }

    /** Subject contains value. */
    static SearchCriteria subject(String value) { return new Subject(value); }
    /** From header contains value. */
    static SearchCriteria from(String value) { return new AddressField("FROM", value); }
    /** To header contains value. */
    static SearchCriteria to(String value) { return new AddressField("TO", value); }
    /** CC header contains value. */
    static SearchCriteria cc(String value) { return new AddressField("CC", value); }
    /** BCC header contains value. */
    static SearchCriteria bcc(String value) { return new AddressField("BCC", value); }
    /** Body contains text. */
    static SearchCriteria body(String text) { return new Body(text); }
    /** Headers or body contain text. */
    static SearchCriteria text(String text) { return new Text(text); }
    /** Header field contains value. */
    static SearchCriteria header(String field, String value) { return new Header(field, value); }

    /** Internal date before date. */
    static SearchCriteria before(LocalDate date) { return new Before(date); }
    /** Internal date on date. */
    static SearchCriteria on(LocalDate date) { return new On(date); }
    /** Internal date on or after date. */
    static SearchCriteria since(LocalDate date) { return new Since(date); }
    /** Sent date before date. */
    static SearchCriteria sentBefore(LocalDate date) { return new SentBefore(date); }
    /** Sent date on date. */
    static SearchCriteria sentOn(LocalDate date) { return new SentOn(date); }
    /** Sent date on or after date. */
    static SearchCriteria sentSince(LocalDate date) { return new SentSince(date); }

    /** Message size larger than N octets. */
    static SearchCriteria larger(long size) { return new Larger(size); }
    /** Message size smaller than N octets. */
    static SearchCriteria smaller(long size) { return new Smaller(size); }

    /** Keyword flag set. */
    static SearchCriteria keyword(String kw) { return new Keyword(kw); }
    /** Keyword flag not set. */
    static SearchCriteria unkeyword(String kw) { return new Unkeyword(kw); }

    /** UID set. */
    static SearchCriteria uid(String uidSet) { return new Uid(uidSet); }
    /** Sequence number set. */
    static SearchCriteria sequenceSet(String seqSet) { return new SequenceSet(seqSet); }

    /** Modification sequence (CONDSTORE). */
    static SearchCriteria modSeq(long value) { return new ModSeq(value); }

    /** AND of multiple criteria. */
    static SearchCriteria and(SearchCriteria... criteria) { return new And(List.of(criteria)); }
    /** OR of two criteria. */
    static SearchCriteria or(SearchCriteria left, SearchCriteria right) { return new Or(left, right); }
    /** NOT a criterion. */
    static SearchCriteria not(SearchCriteria criterion) { return new Not(criterion); }

    /**
     * Formats a date for IMAP protocol (dd-MMM-yyyy).
     *
     * @param date the date
     * @return the formatted date string
     */
    private static String formatDate(LocalDate date) {
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        return String.format("%d-%s-%d", date.getDayOfMonth(),
                months[date.getMonthValue() - 1], date.getYear());
    }
}
