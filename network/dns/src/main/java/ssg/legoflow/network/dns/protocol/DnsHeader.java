package ssg.legoflow.network.dns.protocol;

/**
 * DNS message header as defined in RFC 1035, Section 4.1.1.
 *
 * <p>The header is 12 bytes and contains the message ID, flags, and
 * section counts. This record is immutable.
 *
 * <pre>
 *                                 1  1  1  1  1  1
 *   0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                      ID                       |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |QR|   Opcode  |AA|TC|RD|RA| Z|AD|CD|   RCODE   |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    QDCOUNT                     |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    ANCOUNT                     |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    NSCOUNT                     |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    ARCOUNT                     |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * </pre>
 *
 * @param id       16-bit message identifier
 * @param qr       query (false) or response (true)
 * @param opCode   operation code
 * @param aa       authoritative answer
 * @param tc       truncation
 * @param rd       recursion desired
 * @param ra       recursion available
 * @param ad       authenticated data (DNSSEC, RFC 4035)
 * @param cd       checking disabled (DNSSEC, RFC 4035)
 * @param rCode    response code
 * @param qdCount  number of questions
 * @param anCount  number of answer records
 * @param nsCount  number of authority records
 * @param arCount  number of additional records
 * @since 1.0.0
 */
public record DnsHeader(
        int id,
        boolean qr,
        OpCode opCode,
        boolean aa,
        boolean tc,
        boolean rd,
        boolean ra,
        boolean ad,
        boolean cd,
        ResponseCode rCode,
        int qdCount,
        int anCount,
        int nsCount,
        int arCount
) {

    /** DNS header size in bytes. */
    public static final int SIZE = 12;

    /**
     * Returns the flags as a 16-bit integer for wire encoding.
     *
     * @return the flags word
     * @since 1.0.0
     */
    public int flags() {
        int flags = 0;
        if (qr) flags |= 0x8000;
        flags |= (opCode.value() & 0x0F) << 11;
        if (aa) flags |= 0x0400;
        if (tc) flags |= 0x0200;
        if (rd) flags |= 0x0100;
        if (ra) flags |= 0x0080;
        if (ad) flags |= 0x0020;
        if (cd) flags |= 0x0010;
        flags |= rCode.value() & 0x0F;
        return flags;
    }

    /**
     * Parses flags from a 16-bit integer.
     *
     * @param id    the message ID
     * @param flags the 16-bit flags word
     * @param qdCount question count
     * @param anCount answer count
     * @param nsCount authority count
     * @param arCount additional count
     * @return the parsed header
     * @since 1.0.0
     */
    public static DnsHeader fromFlags(int id, int flags, int qdCount, int anCount,
                                       int nsCount, int arCount) {
        boolean qr = (flags & 0x8000) != 0;
        OpCode opCode = OpCode.fromValue((flags >> 11) & 0x0F);
        boolean aa = (flags & 0x0400) != 0;
        boolean tc = (flags & 0x0200) != 0;
        boolean rd = (flags & 0x0100) != 0;
        boolean ra = (flags & 0x0080) != 0;
        boolean ad = (flags & 0x0020) != 0;
        boolean cd = (flags & 0x0010) != 0;
        ResponseCode rCode = ResponseCode.fromValue(flags & 0x0F);
        return new DnsHeader(id, qr, opCode, aa, tc, rd, ra, ad, cd, rCode,
                qdCount, anCount, nsCount, arCount);
    }

    /**
     * Creates a builder pre-populated with this header's values.
     *
     * @return a new builder
     * @since 1.0.0
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id).qr(qr).opCode(opCode).aa(aa).tc(tc).rd(rd).ra(ra)
                .ad(ad).cd(cd).rCode(rCode)
                .qdCount(qdCount).anCount(anCount).nsCount(nsCount).arCount(arCount);
    }

    /**
     * Builder for {@link DnsHeader}.
     *
     * @since 1.0.0
     */
    public static final class Builder {
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
        private int qdCount;
        private int anCount;
        private int nsCount;
        private int arCount;

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
        public Builder qdCount(int qdCount) { this.qdCount = qdCount; return this; }
        public Builder anCount(int anCount) { this.anCount = anCount; return this; }
        public Builder nsCount(int nsCount) { this.nsCount = nsCount; return this; }
        public Builder arCount(int arCount) { this.arCount = arCount; return this; }

        /**
         * Builds the header.
         *
         * @return the constructed header
         * @since 1.0.0
         */
        public DnsHeader build() {
            return new DnsHeader(id, qr, opCode, aa, tc, rd, ra, ad, cd, rCode,
                    qdCount, anCount, nsCount, arCount);
        }
    }
}
