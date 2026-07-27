package ssg.legoflow.network.dns.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A complete DNS message consisting of a header, questions, and resource
 * record sections (answer, authority, additional) as defined in RFC 1035.
 *
 * <p>Instances are created via the {@link Builder}.
 *
 * @since 1.0.0
 */
public final class DnsMessage {

    private final DnsHeader header;
    private final List<DnsQuestion> questions;
    private final List<DnsRecord> answers;
    private final List<DnsRecord> authority;
    private final List<DnsRecord> additional;

    private DnsMessage(Builder builder) {
        this.questions = Collections.unmodifiableList(new ArrayList<>(builder.questions));
        this.answers = Collections.unmodifiableList(new ArrayList<>(builder.answers));
        this.authority = Collections.unmodifiableList(new ArrayList<>(builder.authority));
        this.additional = Collections.unmodifiableList(new ArrayList<>(builder.additional));

        if (builder.header != null) {
            this.header = builder.header;
        } else {
            this.header = new DnsHeader.Builder()
                    .id(builder.id)
                    .qr(builder.qr)
                    .opCode(builder.opCode)
                    .aa(builder.aa)
                    .tc(builder.tc)
                    .rd(builder.rd)
                    .ra(builder.ra)
                    .ad(builder.ad)
                    .cd(builder.cd)
                    .rCode(builder.rCode)
                    .qdCount(questions.size())
                    .anCount(answers.size())
                    .nsCount(authority.size())
                    .arCount(additional.size())
                    .build();
        }
    }

    /**
     * Returns the message header.
     *
     * @return the header
     * @since 1.0.0
     */
    public DnsHeader header() {
        return header;
    }

    /**
     * Returns the question section.
     *
     * @return unmodifiable list of questions
     * @since 1.0.0
     */
    public List<DnsQuestion> questions() {
        return questions;
    }

    /**
     * Returns the answer section.
     *
     * @return unmodifiable list of answer records
     * @since 1.0.0
     */
    public List<DnsRecord> answers() {
        return answers;
    }

    /**
     * Returns the authority section.
     *
     * @return unmodifiable list of authority records
     * @since 1.0.0
     */
    public List<DnsRecord> authority() {
        return authority;
    }

    /**
     * Returns the additional section.
     *
     * @return unmodifiable list of additional records
     * @since 1.0.0
     */
    public List<DnsRecord> additional() {
        return additional;
    }

    /**
     * Returns whether this is a response message.
     *
     * @return {@code true} if QR=1
     * @since 1.0.0
     */
    public boolean isResponse() {
        return header.qr();
    }

    /**
     * Returns whether this is an authoritative answer.
     *
     * @return {@code true} if AA=1
     * @since 1.0.0
     */
    public boolean isAuthoritative() {
        return header.aa();
    }

    /**
     * Returns whether this message was truncated.
     *
     * @return {@code true} if TC=1
     * @since 1.0.0
     */
    public boolean isTruncated() {
        return header.tc();
    }

    /**
     * Creates a standard query for the given question.
     *
     * @param question the question
     * @return the query message
     * @since 1.0.0
     */
    public static DnsMessage query(DnsQuestion question) {
        return new Builder()
                .id(ThreadLocalRandom.current().nextInt(0, 65536))
                .rd(true)
                .addQuestion(question)
                .build();
    }

    /**
     * Creates a standard query for a domain name and record type.
     *
     * @param name the domain name
     * @param type the record type
     * @return the query message
     * @since 1.0.0
     */
    public static DnsMessage query(String name, RecordType type) {
        return query(DnsQuestion.of(name, type));
    }

    /**
     * Creates a response for the given query.
     *
     * @param query the query message
     * @param rCode the response code
     * @return a builder pre-populated with response fields
     * @since 1.0.0
     */
    public static Builder responseFor(DnsMessage query, ResponseCode rCode) {
        return new Builder()
                .id(query.header().id())
                .qr(true)
                .opCode(query.header().opCode())
                .rd(query.header().rd())
                .ra(true)
                .rCode(rCode)
                .questions(query.questions());
    }

    /**
     * Returns a new builder.
     *
     * @return the builder
     * @since 1.0.0
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "DnsMessage{id=" + header.id()
                + ", qr=" + header.qr()
                + ", opcode=" + header.opCode()
                + ", rcode=" + header.rCode()
                + ", qd=" + questions.size()
                + ", an=" + answers.size()
                + ", ns=" + authority.size()
                + ", ar=" + additional.size() + "}";
    }

    /**
     * Builder for {@link DnsMessage}.
     *
     * @since 1.0.0
     */
    public static final class Builder {
        private DnsHeader header;
        private int id;
        private boolean qr;
        private OpCode opCode = OpCode.QUERY;
        private boolean aa;
        private boolean tc;
        private boolean rd;
        private boolean ra;
        private boolean ad;
        private boolean cd;
        private ResponseCode rCode = ResponseCode.NOERROR;
        private final List<DnsQuestion> questions = new ArrayList<>();
        private final List<DnsRecord> answers = new ArrayList<>();
        private final List<DnsRecord> authority = new ArrayList<>();
        private final List<DnsRecord> additional = new ArrayList<>();

        public Builder header(DnsHeader header) { this.header = header; return this; }
        public Builder id(int id) { this.id = id; return this; }
        public Builder qr(boolean qr) { this.qr = qr; return this; }
        public Builder opCode(OpCode opCode) { this.opCode = opCode; return this; }
        public Builder aa(boolean aa) { this.aa = aa; return this; }
        public Builder tc(boolean tc) { this.tc = tc; return this; }
        public Builder rd(boolean rd) { this.rd = rd; return this; }
        public Builder ra(boolean ra) { this.ra = ra; return this; }
        public Builder ad(boolean ad) { this.ad = ad; return this; }
        public Builder cd(boolean cd) { this.cd = cd; return this; }
        public Builder rCode(ResponseCode rCode) { this.rCode = rCode; return this; }

        public Builder addQuestion(DnsQuestion q) { questions.add(q); return this; }
        public Builder questions(List<DnsQuestion> qs) { questions.clear(); questions.addAll(qs); return this; }
        public Builder addAnswer(DnsRecord r) { answers.add(r); return this; }
        public Builder answers(List<DnsRecord> rs) { answers.clear(); answers.addAll(rs); return this; }
        public Builder addAuthority(DnsRecord r) { authority.add(r); return this; }
        public Builder authorities(List<DnsRecord> rs) { authority.clear(); authority.addAll(rs); return this; }
        public Builder addAdditional(DnsRecord r) { additional.add(r); return this; }
        public Builder additionals(List<DnsRecord> rs) { additional.clear(); additional.addAll(rs); return this; }

        /**
         * Builds the DNS message.
         *
         * @return the constructed message
         * @since 1.0.0
         */
        public DnsMessage build() {
            return new DnsMessage(this);
        }
    }
}
