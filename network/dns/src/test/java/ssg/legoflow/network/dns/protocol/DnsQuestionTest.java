package ssg.legoflow.network.dns.protocol;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class DnsQuestionTest {

    @Test
    void testOfWithDnsName() {
        DnsName name = DnsName.of("example.com");
        DnsQuestion q = DnsQuestion.of(name, RecordType.A);
        
        assertThat(q.name()).isEqualTo(name);
        assertThat(q.type()).isEqualTo(RecordType.A);
        assertThat(q.recordClass()).isEqualTo(RecordClass.IN);
    }

    @Test
    void testOfWithString() {
        DnsQuestion q = DnsQuestion.of("test.example.org", RecordType.AAAA);
        
        assertThat(q.name().toString()).isEqualTo("test.example.org");
        assertThat(q.type()).isEqualTo(RecordType.AAAA);
    }

    @Test
    void testNullNameThrows() {
        assertThatThrownBy(() -> DnsQuestion.of((DnsName) null, RecordType.A))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullTypeThrows() {
        assertThatThrownBy(() -> DnsQuestion.of(DnsName.of("x.com"), (RecordType) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorWithCustomClass() {
        DnsQuestion q = new DnsQuestion(DnsName.of("example.com"), RecordType.A, RecordClass.CH);
        assertThat(q.recordClass()).isEqualTo(RecordClass.CH);
    }

    @Test
    void testEquality() {
        DnsQuestion q1 = DnsQuestion.of("example.com", RecordType.A);
        DnsQuestion q2 = DnsQuestion.of("example.com", RecordType.A);
        assertThat(q1).isEqualTo(q2);
    }
}
