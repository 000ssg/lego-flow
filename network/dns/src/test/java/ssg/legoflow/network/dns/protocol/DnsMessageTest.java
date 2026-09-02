package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.rdata.ARecord;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsMessageTest {

    @Test
    void testQueryConstruction() {
        DnsQuestion question = DnsQuestion.of("example.com", RecordType.A);
        DnsMessage msg = DnsMessage.query(question);
        
        assertThat(msg.header().qr()).isFalse();
        assertThat(msg.questions()).hasSize(1);
        assertThat(msg.answers()).isEmpty();
        assertThat(msg.authority()).isEmpty();
        assertThat(msg.additional()).isEmpty();
        assertThat(msg.isResponse()).isFalse();
    }

    @Test
    void testQueryWithStringName() {
        DnsMessage msg = DnsMessage.query("test.example.org", RecordType.AAAA);
        assertThat(msg.questions()).hasSize(1);
        assertThat(msg.questions().get(0).name().toString()).isEqualTo("test.example.org");
        assertThat(msg.questions().get(0).type()).isEqualTo(RecordType.AAAA);
    }

    @Test
    void testResponseFor() {
        DnsMessage query = DnsMessage.query("example.com", RecordType.A);
        DnsMessage response = DnsMessage.responseFor(query, ResponseCode.NOERROR)
                .build();
        
        assertThat(response.isResponse()).isTrue();
        assertThat(response.header().id()).isEqualTo(query.header().id());
        assertThat(response.questions()).hasSize(1);
    }

    @Test
    void testBuilderWithAllSections() {
        ARecord a = ARecord.of("192.0.2.1");
        DnsName name = DnsName.of("example.com");
        
        DnsMessage msg = DnsMessage.builder()
                .id(42)
                .qr(true)
                .aa(true)
                .rd(true)
                .ra(true)
                .addQuestion(DnsQuestion.of("example.com", RecordType.A))
                .addAnswer(DnsRecord.of("example.com", 300, a))
                .addAuthority(DnsRecord.of("com.", 86400, ssg.legoflow.network.dns.rdata.NsRecord.of("ns1.com.")))
                .addAdditional(DnsRecord.of("ns1.com.", 86400, ARecord.of("10.0.0.1")))
                .build();
        
        assertThat(msg.header().id()).isEqualTo(42);
        assertThat(msg.questions()).hasSize(1);
        assertThat(msg.answers()).hasSize(1);
        assertThat(msg.authority()).hasSize(1);
        assertThat(msg.additional()).hasSize(1);
        assertThat(msg.isAuthoritative()).isTrue();
    }

    @Test
    void testUnmodifiableSections() {
        DnsMessage msg = DnsMessage.query("example.com", RecordType.A);
        assertThatThrownBy(() -> msg.questions().add(DnsQuestion.of("x.com", RecordType.A)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testTruncatedFlag() {
        DnsMessage msg = DnsMessage.builder()
                .tc(true)
                .build();
        assertThat(msg.isTruncated()).isTrue();
    }

    @Test
    void testHeaderAutoCalculation() {
        ARecord a = ARecord.of("1.2.3.4");
        DnsMessage msg = DnsMessage.builder()
                .id(99)
                .addQuestion(DnsQuestion.of("x.com", RecordType.A))
                .addAnswer(DnsRecord.of("x.com", 60, a))
                .addAnswer(DnsRecord.of("x.com", 60, ARecord.of("5.6.7.8")))
                .build();
        
        assertThat(msg.header().qdCount()).isEqualTo(1);
        assertThat(msg.header().anCount()).isEqualTo(2);
    }

    @Test
    void testExplicitHeader() {
        DnsHeader explicit = new DnsHeader(1, true, OpCode.QUERY, false, false, false, false, false, false, ResponseCode.NOERROR, 5, 5, 5, 5);
        DnsMessage msg = DnsMessage.builder().header(explicit).build();
        assertThat(msg.header()).isEqualTo(explicit);
    }

    @Test
    void testToString() {
        DnsMessage msg = DnsMessage.query("example.com", RecordType.A);
        String s = msg.toString();
        assertThat(s).contains("DnsMessage");
        assertThat(s).contains("id=");
    }
}
