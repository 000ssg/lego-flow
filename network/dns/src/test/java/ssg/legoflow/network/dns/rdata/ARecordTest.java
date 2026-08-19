package ssg.legoflow.network.dns.rdata;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.RecordType;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class ARecordTest {

    @Test
    void testOf() {
        ARecord record = ARecord.of("192.0.2.1");
        assertThat(record.type()).isEqualTo(RecordType.A);
        assertThat(record.address().getHostAddress()).isEqualTo("192.0.2.1");
    }

    @Test
    void testFromBytes() throws Exception {
        byte[] bytes = new byte[]{(byte)192, 0, 2, 1};
        ARecord record = ARecord.fromBytes(bytes);
        assertThat(record.address().getHostAddress()).isEqualTo("192.0.2.1");
    }

    @Test
    void testFromBytesWrongLength() {
        assertThatThrownBy(() -> ARecord.fromBytes(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOfInvalidAddress() {
        assertThatThrownBy(() -> ARecord.of("not.an.ip"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOfIpv6Throws() {
        assertThatThrownBy(() -> ARecord.of("::1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not an IPv4");
    }

    @Test
    void testNullAddress() {
        assertThatThrownBy(() -> new ARecord(null))
                .isInstanceOf(NullPointerException.class);
    }
}
