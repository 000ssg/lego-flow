package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsHeaderTest {

    @Test
    void testDefaultQueryHeader() {
        DnsHeader header = new DnsHeader(1234, false, OpCode.QUERY, false, false, true, false, false, false, ResponseCode.NOERROR, 1, 0, 0, 0);
        assertThat(header.id()).isEqualTo(1234);
        assertThat(header.qr()).isFalse();
        assertThat(header.opCode()).isEqualTo(OpCode.QUERY);
        assertThat(header.rd()).isTrue();
        assertThat(header.rCode()).isEqualTo(ResponseCode.NOERROR);
        assertThat(header.qdCount()).isEqualTo(1);
    }

    @Test
    void testResponseHeader() {
        DnsHeader header = new DnsHeader(5678, true, OpCode.QUERY, true, false, true, true, false, false, ResponseCode.NOERROR, 1, 2, 1, 0);
        assertThat(header.qr()).isTrue();
        assertThat(header.aa()).isTrue();
        assertThat(header.ra()).isTrue();
        assertThat(header.anCount()).isEqualTo(2);
        assertThat(header.nsCount()).isEqualTo(1);
    }

    @Test
    void testFlagsRoundTrip() {
        DnsHeader header = new DnsHeader(42, true, OpCode.QUERY, false, true, false, true, true, false, ResponseCode.NXDOMAIN, 1, 0, 0, 0);
        int flags = header.flags();
        
        // Verify individual bits
        assertThat((flags & 0x8000)).isEqualTo(0x8000); // QR=1
        assertThat((flags & 0x0200)).isEqualTo(0x0200); // TC=1
        assertThat((flags & 0x0080)).isEqualTo(0x0080); // RA=1
        assertThat((flags & 0x0020)).isEqualTo(0x0020); // AD=1
        assertThat(flags & 0x0F).isEqualTo(ResponseCode.NXDOMAIN.value()); // RCODE
        
        // Parse back
        DnsHeader parsed = DnsHeader.fromFlags(42, flags, 1, 0, 0, 0);
        assertThat(parsed.id()).isEqualTo(42);
        assertThat(parsed.qr()).isTrue();
        assertThat(parsed.tc()).isTrue();
        assertThat(parsed.ra()).isTrue();
        assertThat(parsed.ad()).isTrue();
        assertThat(parsed.rCode()).isEqualTo(ResponseCode.NXDOMAIN);
    }

    @Test
    void testFlagsZero() {
        DnsHeader header = new DnsHeader(0, false, OpCode.QUERY, false, false, false, false, false, false, ResponseCode.NOERROR, 0, 0, 0, 0);
        assertThat(header.flags()).isEqualTo(0);
    }

    @Test
    void testFromFlags() {
        DnsHeader header = DnsHeader.fromFlags(99, 0x8180, 1, 2, 0, 0);
        assertThat(header.id()).isEqualTo(99);
        assertThat(header.qr()).isTrue(); // QR bit set
        assertThat(header.rd()).isTrue(); // RD bit set
        assertThat(header.ra()).isTrue(); // RA bit set
        assertThat(header.qdCount()).isEqualTo(1);
        assertThat(header.anCount()).isEqualTo(2);
    }

    @Test
    void testToBuilder() {
        DnsHeader original = new DnsHeader(42, true, OpCode.IQUERY, false, false, true, true, true, true, ResponseCode.NOERROR, 3, 1, 1, 1);
        DnsHeader copy = original.toBuilder().build();
        assertThat(copy).isEqualTo(original);
    }

    @Test
    void testBuilderModifications() {
        DnsHeader modified = new DnsHeader(42, true, OpCode.QUERY, false, false, true, true, false, false, ResponseCode.NOERROR, 1, 0, 0, 0)
                .toBuilder()
                .aa(true)
                .rCode(ResponseCode.FORMERR)
                .build();
        assertThat(modified.aa()).isTrue();
        assertThat(modified.rCode()).isEqualTo(ResponseCode.FORMERR);
    }

    @Test
    void testBuilderDefaults() {
        DnsHeader header = new DnsHeader.Builder()
                .id(100)
                .build();
        assertThat(header.id()).isEqualTo(100);
        assertThat(header.opCode()).isEqualTo(OpCode.QUERY); // default
        assertThat(header.rCode()).isEqualTo(ResponseCode.NOERROR); // default
    }

    @Test
    void testSizeConstant() {
        assertThat(DnsHeader.SIZE).isEqualTo(12);
    }

    @Test
    void testFlagsWithAllOpCodes() {
        for (OpCode op : OpCode.values()) {
            DnsHeader h = new DnsHeader.Builder().id(0).opCode(op).build();
            DnsHeader parsed = DnsHeader.fromFlags(0, h.flags(), 0, 0, 0, 0);
            assertThat(parsed.opCode()).isEqualTo(op);
        }
    }

    @Test
    void testFlagsWithAllResponseCodes() {
        for (ResponseCode rc : ResponseCode.values()) {
            DnsHeader h = new DnsHeader.Builder().id(0).rCode(rc).build();
            DnsHeader parsed = DnsHeader.fromFlags(0, h.flags(), 0, 0, 0, 0);
            assertThat(parsed.rCode()).isEqualTo(rc);
        }
    }

    @Test
    void testCdFlag() {
        DnsHeader h = new DnsHeader.Builder().id(0).cd(true).build();
        assertThat(h.flags() & 0x0010).isEqualTo(0x0010);
    }
}
