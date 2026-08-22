package ssg.legoflow.network.dns.rdata;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import ssg.legoflow.network.dns.protocol.RecordType;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class AaaaRecordTest {

    @Test
    void testOf() {
        AaaaRecord record = AaaaRecord.of("2001:db8::1");
        assertThat(record.type()).isEqualTo(RecordType.AAAA);
        assertThat(record.address().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void testFromBytes() throws Exception {
        // 2001:0db8::0001 in bytes (big-endian)
        byte[] bytes = new byte[16];
        bytes[0] = (byte)0x20; bytes[1] = (byte)0x01;  // 2001
        bytes[2] = (byte)0x0d; bytes[3] = (byte)0xb8;  // 0db8
        // zeros in middle
        bytes[15] = 0x01; // last group = 1
        
        AaaaRecord record = AaaaRecord.fromBytes(bytes);
        assertThat(record.address().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void testFromBytesWrongLength() {
        assertThatThrownBy(() -> AaaaRecord.fromBytes(new byte[4]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOfInvalidAddress() {
        assertThatThrownBy(() -> AaaaRecord.of("not.an.ipv6"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOfIpv4Throws() {
        assertThatThrownBy(() -> AaaaRecord.of("192.0.2.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not an IPv6");
    }

    @Test
    void testLoopback() {
        AaaaRecord record = AaaaRecord.of("::1");
        assertThat(record.address().getHostAddress()).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    void testFromBytesLoopback() throws Exception {
        byte[] bytes = new byte[16];
        bytes[15] = 1;
        AaaaRecord record = AaaaRecord.fromBytes(bytes);
        assertThat(record.address().getHostAddress()).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    void testAllZeros() {
        AaaaRecord record = AaaaRecord.of("::");
        assertThat(record.address().getHostAddress()).isEqualTo("0:0:0:0:0:0:0:0");
    }
}
